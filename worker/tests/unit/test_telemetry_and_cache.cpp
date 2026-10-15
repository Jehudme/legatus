#include "legatus/common/paths.hpp"
#include "legatus/common/telemetry_collector.hpp"
#include "legatus/common/crypto_utils.hpp"
#include "legatus/common/base64.hpp"
#include "legatus/daemon/bytecode_cache.hpp"
#include <catch2/catch_all.hpp>
#include <chrono>

TEST_CASE("PathResolver - Cross-Platform Path Invariants", "[worker][common][paths]") {
    auto config_path = legatus::common::PathResolver::get_config_path();
    REQUIRE(!config_path.empty());
    REQUIRE(config_path.filename() == "worker.toml");

    auto state_dir = legatus::common::PathResolver::get_state_dir();
    REQUIRE(!state_dir.empty());

    auto log_dir = legatus::common::PathResolver::get_log_dir();
    REQUIRE(!log_dir.empty());

    auto cache_dir = legatus::common::PathResolver::get_bytecode_cache_dir();
    REQUIRE(!cache_dir.empty());
    REQUIRE(cache_dir.filename() == "bytecode_cache");

    auto ipc_path = legatus::common::PathResolver::get_default_ipc_path();
    REQUIRE(!ipc_path.empty());
}

TEST_CASE("TelemetryCollector - Live Host CPU & Memory Metrics", "[worker][common][telemetry]") {
    SECTION("CPU load percentage is within valid range [0.0, 100.0]") {
        double cpu = legatus::common::TelemetryCollector::collect_cpu_load();
        REQUIRE(cpu >= 0.0);
        REQUIRE(cpu <= 100.0);
    }

    SECTION("Physical memory metrics report positive byte allocations") {
        uint64_t used = 0, free_mem = 0;
        legatus::common::TelemetryCollector::collect_memory(used, free_mem);
        REQUIRE(used > 0);
        REQUIRE(free_mem > 0);
    }

    SECTION("Composite HostMetrics snapshot is populated") {
        auto metrics = legatus::common::TelemetryCollector::collect();
        REQUIRE(metrics.cpu_load_pct >= 0.0);
        REQUIRE(metrics.memory_used_bytes > 0);
        REQUIRE(metrics.memory_free_bytes > 0);
    }
}

TEST_CASE("BytecodeCache - Local Deduplication & Disk Atomicity", "[worker][daemon][cache]") {
    std::filesystem::path test_cache_dir = "/tmp/legatus_cache_test_" +
        std::to_string(std::chrono::steady_clock::now().time_since_epoch().count());

    legatus::daemon::BytecodeCache cache(test_cache_dir);
    REQUIRE(std::filesystem::exists(test_cache_dir));

    std::vector<uint8_t> sample_bytecode = {0x53, 0x4F, 0x4C, 0x49, 0x58, 0x01, 0x02, 0x03};
    std::string hash = legatus::common::sha256_hex(sample_bytecode.data(), sample_bytecode.size());

    SECTION("Querying non-existent cache returns false and empty buffer") {
        REQUIRE(!cache.has(hash));
        REQUIRE(cache.get(hash).empty());
    }

    SECTION("Store raw bytes and retrieve identical payload") {
        cache.store(hash, sample_bytecode);
        REQUIRE(cache.has(hash));
        REQUIRE(cache.get(hash) == sample_bytecode);

        auto path = cache.get_path(hash);
        REQUIRE(std::filesystem::exists(path));
        REQUIRE(path.filename() == hash + ".slxb");
    }

    SECTION("Store Base64 payload and retrieve decoded bytecode") {
        std::string b64 = legatus::common::base64_encode(sample_bytecode);
        cache.store_base64(hash, b64);
        REQUIRE(cache.has(hash));
        REQUIRE(cache.get(hash) == sample_bytecode);
    }

    SECTION("get_or_fetch with local Base64 decodes and caches immediately") {
        std::string b64 = legatus::common::base64_encode(sample_bytecode);
        auto retrieved = cache.get_or_fetch(hash, b64, "");
        REQUIRE(retrieved == sample_bytecode);
        REQUIRE(cache.has(hash));
    }

    std::error_code ec;
    std::filesystem::remove_all(test_cache_dir, ec);
}
