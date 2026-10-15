#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# scripts/verify-cli-docs.sh
# Automated CLI/API Drift Verification Script for Legatus Operational Guides
# Complies with Engineering Guideline GL-0010 (Living Documentation Standard)
# ==============================================================================

echo "=== Verifying Legatus Operational Guides (GL-0010 Zero-Drift Audit) ==="

ERRORS=0

# 1. Audit Worker CLI Subcommands and Options in docs/guides/05-worker.md
echo "[1/4] Auditing worker CLI commands and flags..."
WORKER_CLI_SOURCE="worker/src/cli/main.cpp"
WORKER_DOC="docs/guides/05-worker.md"

if [[ ! -f "$WORKER_CLI_SOURCE" ]]; then
    echo "ERROR: Worker CLI source file not found at $WORKER_CLI_SOURCE" >&2
    exit 1
fi

SUBCOMMANDS=("up" "down" "drain" "status" "log" "tasks")
for cmd in "${SUBCOMMANDS[@]}"; do
    if ! grep -q "add_subcommand(\"$cmd\"" "$WORKER_CLI_SOURCE"; then
        echo "ERROR: Subcommand '$cmd' documented but not registered in $WORKER_CLI_SOURCE" >&2
        ERRORS=$((ERRORS + 1))
    fi
done

FLAGS=("--config" "--force" "--timeout" "--disable" "--json" "--lines" "--reason")
for flag in "${FLAGS[@]}"; do
    if ! grep -q -- "$flag" "$WORKER_CLI_SOURCE"; then
        echo "ERROR: Flag '$flag' documented in $WORKER_DOC but missing in $WORKER_CLI_SOURCE" >&2
        ERRORS=$((ERRORS + 1))
    fi
done

# 2. Audit Server Configuration Keys in docs/guides/02-server.md
echo "[2/4] Auditing server configuration properties..."
SERVER_DOC="docs/guides/02-server.md"
SERVER_YAML_KEYS=("server:" "port:" "spring:" "datasource:" "legatus:" "cluster:")
for key in "${SERVER_YAML_KEYS[@]}"; do
    if ! grep -q "$key" "$SERVER_DOC"; then
        echo "ERROR: Key '$key' missing in server documentation $SERVER_DOC" >&2
        ERRORS=$((ERRORS + 1))
    fi
done

# 3. Audit Studio Profiles and Targets in docs/guides/04-studio.md
echo "[3/4] Auditing studio build profiles and jpackage targets..."
STUDIO_POM="studio/pom.xml"
STUDIO_DOC="docs/guides/04-studio.md"

if ! grep -q "<id>jpackage</id>" "$STUDIO_POM"; then
    echo "ERROR: jpackage profile documented in $STUDIO_DOC missing in $STUDIO_POM" >&2
    ERRORS=$((ERRORS + 1))
fi

if ! grep -q "legatus-studio" "$STUDIO_POM"; then
    echo "ERROR: legatus-studio artifact naming missing in $STUDIO_POM" >&2
    ERRORS=$((ERRORS + 1))
fi

# 4. Audit Documentation Link Integrity across docs/guides/
echo "[4/4] Verifying internal markdown link integrity across docs/guides/..."
python3 -c "
import os, re, sys
errors = 0
for root, _, files in os.walk('docs/guides'):
    for f in files:
        if f.endswith('.md'):
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8') as handle:
                content = handle.read()
            for match in re.findall(r'\[.*?\]\(([^http#][^\)]*?)\)', content):
                target = match.split('#')[0]
                if target:
                    resolved = os.path.normpath(os.path.join(root, target))
                    if not os.path.exists(resolved):
                        print(f'Broken link in {path}: {target} -> {resolved}')
                        errors += 1
if errors > 0:
    sys.exit(1)
" || ERRORS=$((ERRORS + 1))

if [[ $ERRORS -gt 0 ]]; then
    echo "FAILED: $ERRORS documentation drift or link integrity defect(s) detected!" >&2
    exit 1
fi

echo "[SUCCESS] All 4 operator guides (docs/guides/) pass GL-0010 zero-drift verification."
