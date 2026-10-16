#!/usr/bin/env bash
# ==============================================================================
# Legatus Unified Master Military-Grade Test Suite (MS-016)
# ==============================================================================
# Executes all test layers across the distributed runtime:
# 1. Native Worker Catch2 Suite (32 tests including ASan/UBSan & isolation)
# 2. Studio Desktop Cockpit Suite (134 tests including TestFX headless UI)
# 3. Server Subsystem & E2E Integration Suite (743+ Spring Boot & DB tests)
# 4. End-to-End Cluster Harness & Soak Verification
# ==============================================================================
set -euo pipefail

BOLD="\033[1m"
GREEN="\033[1;32m"
RED="\033[1;31m"
BLUE="\033[1;34m"
CYAN="\033[1;36m"
YELLOW="\033[1;33m"
RESET="\033[0m"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
chmod +x "${REPO_ROOT}/scripts/"*.sh 2>/dev/null || true

START_TIME=$(date +%s)

echo -e "\n${BOLD}${CYAN}========================================================================${RESET}"
echo -e "${BOLD}${CYAN}   LEGATUS MILITARY-GRADE AUTOMATED TEST PYRAMID VERIFICATION (MS-016)   ${RESET}"
echo -e "${BOLD}${CYAN}========================================================================${RESET}\n"

# ------------------------------------------------------------------------------
# Layer 1: Native Worker Catch2 Suite (Isolation, Stdout Flood, Signals)
# ------------------------------------------------------------------------------
echo -e "${BOLD}${BLUE}[LAYER 1/4] Running Worker Catch2 Test Suite (CTest)...${RESET}"
if ctest --test-dir "${REPO_ROOT}/worker/build" --output-on-failure; then
    echo -e "${GREEN}[PASS] Worker Catch2 Suite Passed (100%)${RESET}\n"
else
    echo -e "${RED}[FAIL] Worker Catch2 Suite Failed!${RESET}\n"
    exit 1
fi

# ------------------------------------------------------------------------------
# Layer 2: Studio Cockpit TestFX Headless Suite (Modals, Telemetry, Bindings)
# ------------------------------------------------------------------------------
echo -e "${BOLD}${BLUE}[LAYER 2/4] Running Studio Desktop Cockpit Test Suite (Maven)...${RESET}"
if (cd "${REPO_ROOT}/studio" && mvn test); then
    echo -e "${GREEN}[PASS] Studio Cockpit Suite Passed (100%)${RESET}\n"
else
    echo -e "${RED}[FAIL] Studio Cockpit Suite Failed!${RESET}\n"
    exit 1
fi

# ------------------------------------------------------------------------------
# Layer 3: Server Subsystem & E2E Spring Boot Suite
# ------------------------------------------------------------------------------
echo -e "${BOLD}${BLUE}[LAYER 3/4] Running Server Integration & Defect Scenarios Suite (Maven)...${RESET}"
if (cd "${REPO_ROOT}/server" && mvn test -Dtest="*IT,*Test"); then
    echo -e "${GREEN}[PASS] Server Subsystem & Integration Suite Passed (100%)${RESET}\n"
else
    echo -e "${RED}[FAIL] Server Subsystem Suite Failed!${RESET}\n"
    exit 1
fi

# ------------------------------------------------------------------------------
# Layer 4: Cluster E2E Harness & Soak Test
# ------------------------------------------------------------------------------
echo -e "${BOLD}${BLUE}[LAYER 4/4] Running Cluster E2E & Soak Harness...${RESET}"
if "${REPO_ROOT}/scripts/test_cluster_e2e.sh"; then
    echo -e "${GREEN}[PASS] Cluster E2E Harness Passed!${RESET}\n"
else
    echo -e "${RED}[FAIL] Cluster E2E Harness Failed!${RESET}\n"
    exit 1
fi

if "${REPO_ROOT}/scripts/soak_test_cluster.sh" 100 8; then
    echo -e "${GREEN}[PASS] Cluster Soak Verification Passed!${RESET}\n"
else
    echo -e "${RED}[FAIL] Cluster Soak Verification Failed!${RESET}\n"
    exit 1
fi

END_TIME=$(date +%s)
TOTAL_TIME=$((END_TIME - START_TIME))

echo -e "\n${BOLD}${GREEN}========================================================================${RESET}"
echo -e "${BOLD}${GREEN}   ✓ ALL MILITARY-GRADE TEST SUITES PASSED DETERMINISTICALLY (${TOTAL_TIME}s)   ${RESET}"
echo -e "${BOLD}${GREEN}========================================================================${RESET}\n"
exit 0
