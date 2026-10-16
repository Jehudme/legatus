#!/usr/bin/env bash
# ==============================================================================
# Legatus Military-Grade Automated Cluster E2E Test Runner (MS-016)
# ==============================================================================
set -euo pipefail

# ANSI color codes
BOLD="\033[1m"
GREEN="\033[1;32m"
RED="\033[1;31m"
YELLOW="\033[1;33m"
BLUE="\033[1;34m"
CYAN="\033[1;36m"
RESET="\033[0m"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKER_DIR="${REPO_ROOT}/worker"
WORKER_BUILD_DIR="${WORKER_DIR}/build"
SERVER_DIR="${REPO_ROOT}/server"
STUDIO_DIR="${REPO_ROOT}/studio"

LOG_DIR="${REPO_ROOT}/target/e2e-logs"
mkdir -p "${LOG_DIR}"

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

log_info() {
    echo -e "${BLUE}[INFO]${RESET} ${1}"
}

log_pass() {
    echo -e "${GREEN}[PASS]${RESET} ${1}"
    PASSED_TESTS=$((PASSED_TESTS + 1))
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

log_fail() {
    echo -e "${RED}[FAIL]${RESET} ${1}"
    FAILED_TESTS=$((FAILED_TESTS + 1))
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

log_warn() {
    echo -e "${YELLOW}[WARN]${RESET} ${1}"
}

log_stage() {
    echo -e "\n${CYAN}========================================================================${RESET}"
    echo -e "${BOLD}${CYAN}>>> ${1}${RESET}"
    echo -e "${CYAN}========================================================================${RESET}"
}

cleanup() {
    local exit_code=$?
    log_info "Executing automated cluster teardown and process cleanup..."

    # Kill any lingering test background processes
    pkill -f "legatusd.*--e2e-test" 2>/dev/null || true
    pkill -f "legatus-runner.*test-signal" 2>/dev/null || true

    if [ ${exit_code} -eq 0 ] && [ ${FAILED_TESTS} -eq 0 ]; then
        echo -e "\n${GREEN}${BOLD}✓ ALL CLUSTER E2E TESTS PASSED (${PASSED_TESTS}/${TOTAL_TESTS})${RESET}\n"
    else
        echo -e "\n${RED}${BOLD}✗ CLUSTER E2E TESTS FAILED (${FAILED_TESTS} failures out of ${TOTAL_TESTS} tests)${RESET}\n"
    fi
}
trap cleanup EXIT INT TERM

START_TIME=$(date +%s)

# ------------------------------------------------------------------------------
# Stage 1: Build & Verify Native Worker Daemon & Runner
# ------------------------------------------------------------------------------
log_stage "Stage 1: Validating Worker Native Binaries & Isolation"

if [ ! -f "${WORKER_BUILD_DIR}/legatusd" ] || [ ! -f "${WORKER_BUILD_DIR}/legatus-runner" ]; then
    log_info "Compiling native worker daemon and runner via Ninja..."
    cmake -B "${WORKER_BUILD_DIR}" -S "${WORKER_DIR}" -G Ninja -DCMAKE_BUILD_TYPE=Debug
    ninja -C "${WORKER_BUILD_DIR}"
fi

if [ -x "${WORKER_BUILD_DIR}/legatusd" ] && [ -x "${WORKER_BUILD_DIR}/legatus-runner" ]; then
    log_pass "Native binaries present and executable (legatusd, legatus-runner)"
else
    log_fail "Native worker binaries missing or non-executable"
    exit 1
fi

log_info "Executing Catch2 process isolation and high-volume stdout draining..."
if ctest --test-dir "${WORKER_BUILD_DIR}" -R "isolation" --output-on-failure > "${LOG_DIR}/worker_isolation.log" 2>&1; then
    log_pass "Catch2 Process Isolation & Stdout Drain (DEF-04 & DEF-06) passed"
else
    log_fail "Catch2 Process Isolation failed (see ${LOG_DIR}/worker_isolation.log)"
fi

# ------------------------------------------------------------------------------
# Stage 2: Studio UI Workflows (TestFX Headless Mode)
# ------------------------------------------------------------------------------
log_stage "Stage 2: Validating Studio Modal Workflows & Inspector Bindings"

log_info "Running TestFX Headless UI test suite in studio/..."
if (cd "${STUDIO_DIR}" && mvn test -Dtest=StudioModalWorkflowTestFX > "${LOG_DIR}/studio_testfx.log" 2>&1); then
    log_pass "Studio TestFX Modal Workflows & Inspector Bindings (DEF-05, DEF-08, DEF-09) passed"
else
    log_fail "Studio TestFX UI tests failed (see ${LOG_DIR}/studio_testfx.log)"
fi

# ------------------------------------------------------------------------------
# Stage 3: Server & Cluster Real-Component Integration Suite
# ------------------------------------------------------------------------------
log_stage "Stage 3: Validating Server Cluster Integration & All Defect Scenarios"

log_info "Running ClusterE2ETest integration suite in server/..."
if (cd "${SERVER_DIR}" && mvn test -Dtest=ClusterE2ETest > "${LOG_DIR}/cluster_e2e.log" 2>&1); then
    log_pass "ClusterE2ETest (DEF-01 through DEF-09) passed"
else
    log_fail "ClusterE2ETest failed (see ${LOG_DIR}/cluster_e2e.log)"
fi

# ------------------------------------------------------------------------------
# Summary & Timing
# ------------------------------------------------------------------------------
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
log_info "Total execution time: ${ELAPSED} seconds"

if [ ${FAILED_TESTS} -gt 0 ]; then
    exit 1
fi
exit 0
