#include "legatus/common/telemetry_collector.hpp"
#include <fstream>
#include <sstream>
#include <thread>
#include <algorithm>

#if defined(_WIN32)
#include <windows.h>
#else
#include <unistd.h>
#include <cstdlib>
#endif

namespace legatus::common {

double TelemetryCollector::collect_cpu_load() {
#if defined(_WIN32)
    return 10.0;
#else
    double loads[1] = {0.0};
    if (getloadavg(loads, 1) > 0) {
        unsigned int cores = std::thread::hardware_concurrency();
        if (cores == 0) cores = 1;
        double pct = (loads[0] / cores) * 100.0;
        return std::clamp(pct, 0.0, 100.0);
    }
    return 5.0;
#endif
}

void TelemetryCollector::collect_memory(uint64_t& used_bytes, uint64_t& free_bytes) {
    used_bytes = 1024ULL * 1024 * 1024; // 1 GB fallback
    free_bytes = 1024ULL * 1024 * 1024;

#if defined(_WIN32)
    MEMORYSTATUSEX mem_status;
    mem_status.dwLength = sizeof(mem_status);
    if (GlobalMemoryStatusEx(&mem_status)) {
        free_bytes = mem_status.ullAvailPhys;
        used_bytes = mem_status.ullTotalPhys - mem_status.ullAvailPhys;
    }
#else
    std::ifstream meminfo("/proc/meminfo");
    if (meminfo.is_open()) {
        std::string line;
        uint64_t total_kb = 0;
        uint64_t avail_kb = 0;
        while (std::getline(meminfo, line)) {
            if (line.rfind("MemTotal:", 0) == 0) {
                std::istringstream iss(line.substr(9));
                iss >> total_kb;
            } else if (line.rfind("MemAvailable:", 0) == 0) {
                std::istringstream iss(line.substr(13));
                iss >> avail_kb;
            }
        }
        if (total_kb > 0) {
            free_bytes = avail_kb * 1024;
            used_bytes = (total_kb >= avail_kb ? (total_kb - avail_kb) : 0) * 1024;
        }
    }
#endif
}

HostMetrics TelemetryCollector::collect() {
    HostMetrics metrics;
    metrics.cpu_load_pct = collect_cpu_load();
    metrics.gpu_load_pct = 0.0;
    collect_memory(metrics.memory_used_bytes, metrics.memory_free_bytes);
    return metrics;
}

} // namespace legatus::common
