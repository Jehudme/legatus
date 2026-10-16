#include "legatus/daemon/config_manager.hpp"
#include <catch2/catch_all.hpp>
#include <atomic>

TEST_CASE("ConfigManager Dynamic Server Configuration", "[worker][daemon][config]") {
    legatus::daemon::ConfigManager manager;

    SECTION("Default configuration state is sane") {
        REQUIRE(manager.config_version() == 1);
        REQUIRE(manager.is_active() == true);
        REQUIRE(manager.target_cpu_pct() == 75.0);
        REQUIRE(manager.target_ram_pct() == 80.0);
        REQUIRE(manager.max_concurrent_tasks() == 16);
        REQUIRE(manager.auto_renew_lease() == true);
        REQUIRE(manager.heartbeat_interval_ms() == 5000);
        REQUIRE(manager.log_level() == "info");
    }

    SECTION("apply_server_config updates fields and triggers listener") {
        std::atomic<int> notification_count{0};
        manager.register_listener([&]() {
            notification_count++;
        });

        nlohmann::json payload = {
            {"config_version", 42},
            {"friendly_name", "alpha-node-01"},
            {"active", false},
            {"target_cpu_pct", 65.5},
            {"target_ram_pct", 70.0},
            {"max_concurrent_tasks", 8},
            {"auto_renew_lease", false},
            {"remaining_lease_sec", 3600},
            {"heartbeat_interval_ms", 3000},
            {"log_level", "debug"},
            {"runtime_config_json", "{\"key\":\"val\"}"}
        };

        bool ok = manager.apply_server_config(payload);
        REQUIRE(ok == true);
        REQUIRE(notification_count == 1);

        REQUIRE(manager.config_version() == 42);
        REQUIRE(manager.friendly_name() == "alpha-node-01");
        REQUIRE(manager.is_active() == false);
        REQUIRE(manager.target_cpu_pct() == 65.5);
        REQUIRE(manager.target_ram_pct() == 70.0);
        REQUIRE(manager.max_concurrent_tasks() == 8);
        REQUIRE(manager.auto_renew_lease() == false);
        REQUIRE(manager.remaining_lease_sec() == 3600);
        REQUIRE(manager.heartbeat_interval_ms() == 3000);
        REQUIRE(manager.log_level() == "debug");
        REQUIRE(manager.runtime_config_json() == "{\"key\":\"val\"}");
    }

    SECTION("compute_backoff calculates exponential delays capped at max") {
        auto b0 = legatus::daemon::ConfigManager::compute_backoff(0);
        REQUIRE(b0.count() == 1000);

        auto b1 = legatus::daemon::ConfigManager::compute_backoff(1);
        REQUIRE(b1.count() == 2000);

        auto b2 = legatus::daemon::ConfigManager::compute_backoff(2);
        REQUIRE(b2.count() == 4000);

        auto b3 = legatus::daemon::ConfigManager::compute_backoff(3);
        REQUIRE(b3.count() == 8000);

        auto b10 = legatus::daemon::ConfigManager::compute_backoff(10);
        REQUIRE(b10.count() <= 30000); // capped at default max 30s
    }
}
