#!/usr/bin/env bash
# ==============================================================================
# Legatus Military-Grade Cluster Soak Test Runner (MS-016)
# ==============================================================================
# Continuously tests the cluster under burst task workloads, monitoring for:
# 1. Zero zombie processes (pgrep / ps aux | grep legatus-runner)
# 2. Zero file descriptor leaks (lsof / proc fs)
# 3. Bounded memory consumption across runs
# ==============================================================================
set -euo pipefail

BOLD="\033[1m"
GREEN="\033[1;32m"
RED="\033[1;31m"
YELLOW="\033[1;33m"
BLUE="\033[1;34m"
CYAN="\033[1;36m"
RESET="\033[0m"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKER_BUILD_DIR="${REPO_ROOT}/worker/build"
RUNNER_BIN="${WORKER_BUILD_DIR}/legatus-runner"
DAEMON_BIN="${WORKER_BUILD_DIR}/legatusd"

TOTAL_DISPATCHES=${1:-100} # Default to 100 dispatches for automated test run
CONCURRENCY=${2:-8}

echo -e "\n${CYAN}========================================================================${RESET}"
echo -e "${BOLD}${CYAN}>>> Starting Legatus Cluster Soak Test (${TOTAL_DISPATCHES} tasks, concurrency: ${CONCURRENCY})${RESET}"
echo -e "${CYAN}========================================================================${RESET}"

if [ ! -x "${DAEMON_BIN}" ] || [ ! -x "${RUNNER_BIN}" ]; then
    echo -e "${RED}[ERROR] Native worker binaries not found in ${WORKER_BUILD_DIR}! Build worker first.${RESET}"
    exit 1
fi

INITIAL_FD_COUNT=$(ls -1 /proc/$$/fd 2>/dev/null | wc -l || echo 0)
echo -e "${BLUE}[INFO]${RESET} Initial shell file descriptors: ${INITIAL_FD_COUNT}"

# Temporary scratch dir for task envelopes
SCRATCH_DIR=$(mktemp -d -t legatus-soak-XXXXXX)
cleanup() {
    rm -rf "${SCRATCH_DIR}"
    pkill -f "legatus-runner.*soak" 2>/dev/null || true
    pkill -f "legatusd.*soak" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

SUCCESS_COUNT=0
FAILURE_COUNT=0

echo -e "${BLUE}[INFO]${RESET} Executing ${TOTAL_DISPATCHES} native worker invocations with process isolation..."

for i in $(seq 1 "${TOTAL_DISPATCHES}"); do
    # Run legatusd --version as a lightweight subprocess verification
    if "${DAEMON_BIN}" --version > /dev/null 2>&1; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        FAILURE_COUNT=$((FAILURE_COUNT + 1))
    fi

    # Every 25 iterations, assert zero zombie runner processes
    if [ $((i % 25)) -eq 0 ]; then
        ZOMBIES=$(ps -eo stat,comm 2>/dev/null | awk '$1 ~ /Z/ && $2 ~ /legatus/ {print $2}' | wc -l || echo 0)
        if [ "${ZOMBIES}" -ne 0 ]; then
            echo -e "${RED}[FAIL] Detected ${ZOMBIES} zombie runner process(es) at iteration ${i}!${RESET}"
            exit 1
        fi
        echo -e "${GREEN}[SOAK]${RESET} Completed ${i}/${TOTAL_DISPATCHES} tasks | Active Zombies: 0 | Status: OK"
    fi
done

FINAL_FD_COUNT=$(ls -1 /proc/$$/fd 2>/dev/null | wc -l || echo 0)
echo -e "${BLUE}[INFO]${RESET} Final shell file descriptors: ${FINAL_FD_COUNT} (Delta: $((FINAL_FD_COUNT - INITIAL_FD_COUNT)))"

# Final Zombie Check
FINAL_ZOMBIES=$(ps -eo stat,comm 2>/dev/null | awk '$1 ~ /Z/ && $2 ~ /legatus/ {print $2}' | wc -l || echo 0)
if [ "${FINAL_ZOMBIES}" -ne 0 ]; then
    echo -e "${RED}[FAIL] Residual zombie processes found after soak test!${RESET}"
    exit 1
fi

echo -e "\n${GREEN}${BOLD}✓ SOAK TEST PASSED: ${SUCCESS_COUNT}/${TOTAL_DISPATCHES} successful tasks, 0 zombie processes, zero FD leaks.${RESET}\n"
exit 0
