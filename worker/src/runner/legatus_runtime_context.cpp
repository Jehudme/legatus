#include "legatus/runner/legatus_runtime_context.hpp"

namespace legatus::runner {

LegatusRuntimeContext::LegatusRuntimeContext(const solix::RuntimeOptions& opts)
    : solix::RuntimeContext(opts) {}

void LegatusRuntimeContext::on_budget_exceeded() {
    budget_exhausted_ = true;
    exit_code = 75; // EX_TEMPFAIL
}

void LegatusRuntimeContext::on_out_of_memory(size_t requested_bytes) {
    (void)requested_bytes;
    oom_occurred_ = true;
    exit_code = 71; // EX_OSERR
}

} // namespace legatus::runner
