#include "legatus/common/telemetry_collector.hpp"
#include <catch2/catch_all.hpp>
#include <thread>
#include <chrono>

TEST_CASE("TelemetryCollector Normalized CPU Telemetry", "[worker][telemetry][cpu]") {
    SECTION("collect_cpu_load returns non-negative bounded percentage [0.0, 100.0]") {
        double cpu1 = legatus::common::TelemetryCollector::collect_cpu_load();
        REQUIRE(cpu1 >= 0.0);
        REQUIRE(cpu1 <= 100.0);

        // Sleep briefly and take another sample to exercise delta calculation
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
        double cpu2 = legatus::common::TelemetryCollector::collect_cpu_load();
        REQUIRE(cpu2 >= 0.0);
        REQUIRE(cpu2 <= 100.0);
    }

    SECTION("collect returns comprehensive HostMetrics with memory and cpu") {
        auto metrics = legatus::common::TelemetryCollector::collect();
        REQUIRE(metrics.cpu_load_pct >= 0.0);
        REQUIRE(metrics.cpu_load_pct <= 100.0);
        REQUIRE(metrics.gpu_load_pct == 0.0);
        REQUIRE(metrics.memory_used_bytes > 0);
        REQUIRE(metrics.memory_free_bytes > 0);
    }
}
