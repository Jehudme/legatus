#pragma once

#include <solix/runtime.hpp>
#include <string>

namespace legatus::runner {

class LegatusRuntimeContext : public solix::RuntimeContext {
public:
    explicit LegatusRuntimeContext(const solix::RuntimeOptions& opts);
    ~LegatusRuntimeContext() override = default;

    void on_budget_exceeded() override;
    void on_out_of_memory(size_t requested_bytes) override;

    bool is_budget_exhausted() const noexcept { return budget_exhausted_; }
    bool is_oom_occurred() const noexcept { return oom_occurred_; }
    std::uint64_t get_instructions_executed() const { return instructions_executed; }
    std::uint64_t get_peak_memory_bytes() const { return memory.peak_used_words * 8; }

private:
    bool budget_exhausted_ = false;
    bool oom_occurred_ = false;
};

} // namespace legatus::runner
