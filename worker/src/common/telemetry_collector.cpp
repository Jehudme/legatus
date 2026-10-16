#include "legatus/common/telemetry_collector.hpp"
#include <fstream>
#include <sstream>
#include <thread>
#include <algorithm>

#include <mutex>
#include <chrono>

#if defined(_WIN32)
#include <windows.h>
#else
#include <unistd.h>
#include <cstdlib>
#endif

namespace legatus::common {

namespace {

#if !defined(_WIN32)
struct CpuTicks {
    unsigned long long idle = 0;
    unsigned long long total = 0;
};

static std::mutex g_cpu_stat_mutex;
static CpuTicks g_last_cpu_ticks{0, 0};

static bool read_proc_stat_ticks(CpuTicks& ticks) {
    std::ifstream stat_file("/proc/stat");
    if (!stat_file.is_open()) {
        return false;
    }
    std::string line;
    if (std::getline(stat_file, line)) {
        if (line.rfind("cpu ", 0) == 0) {
            std::istringstream ss(line.substr(4));
            unsigned long long user = 0, nice = 0, system = 0, idle = 0, iowait = 0, irq = 0, softirq = 0, steal = 0;
            if (ss >> user >> nice >> system >> idle >> iowait >> irq >> softirq >> steal) {
                unsigned long long idle_time = idle + iowait;
                unsigned long long non_idle = user + nice + system + irq + softirq + steal;
                ticks.idle = idle_time;
                ticks.total = idle_time + non_idle;
                return true;
            }
        }
    }
    return false;
}
#endif

} // namespace

double TelemetryCollector::collect_cpu_load() {
#if defined(_WIN32)
    return 10.0;
#else
    CpuTicks current_ticks;
    if (read_proc_stat_ticks(current_ticks)) {
        std::lock_guard<std::mutex> lock(g_cpu_stat_mutex);
        if (g_last_cpu_ticks.total == 0) {
            g_last_cpu_ticks = current_ticks;
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            CpuTicks second_ticks;
            if (read_proc_stat_ticks(second_ticks)) {
                unsigned long long d_total = second_ticks.total - g_last_cpu_ticks.total;
                unsigned long long d_idle = second_ticks.idle - g_last_cpu_ticks.idle;
                g_last_cpu_ticks = second_ticks;
                if (d_total > 0 && d_total >= d_idle) {
                    double pct = (static_cast<double>(d_total - d_idle) / static_cast<double>(d_total)) * 100.0;
                    return std::clamp(pct, 0.0, 100.0);
                }
            }
            return 0.0;
        }

        unsigned long long d_total = current_ticks.total - g_last_cpu_ticks.total;
        unsigned long long d_idle = current_ticks.idle - g_last_cpu_ticks.idle;
        g_last_cpu_ticks = current_ticks;

        if (d_total > 0 && d_total >= d_idle) {
            double pct = (static_cast<double>(d_total - d_idle) / static_cast<double>(d_total)) * 100.0;
            return std::clamp(pct, 0.0, 100.0);
        }
    }

    // Fallback: getloadavg normalized by core count
    double loads[1] = {0.0};
    if (getloadavg(loads, 1) > 0) {
        unsigned int cores = std::thread::hardware_concurrency();
        if (cores == 0) cores = 1;
        double pct = (loads[0] / static_cast<double>(cores)) * 100.0;
        return std::clamp(pct, 0.0, 100.0);
    }
    return 0.0;
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
