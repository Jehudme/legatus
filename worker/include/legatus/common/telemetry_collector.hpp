#pragma once

#include <cstdint>

namespace legatus::common {

struct HostMetrics {
    double cpu_load_pct = 0.0;
    double gpu_load_pct = 0.0;
    uint64_t memory_used_bytes = 0;
    uint64_t memory_free_bytes = 0;
};

class TelemetryCollector {
public:
    static HostMetrics collect();
    static double collect_cpu_load();
    static void collect_memory(uint64_t& used_bytes, uint64_t& free_bytes);
};

} // namespace legatus::common
