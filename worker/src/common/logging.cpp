#include "legatus/common/logging.hpp"
#include "legatus/common/paths.hpp"
#include <spdlog/spdlog.h>
#include <spdlog/sinks/rotating_file_sink.h>
#include <spdlog/sinks/stdout_color_sinks.h>
#include <filesystem>
#include <mutex>

namespace legatus::common {

static std::string g_active_log_file;
static std::mutex g_log_mutex;

void init_logging(const daemon::LoggingConfig& config) {
    std::lock_guard<std::mutex> lock(g_log_mutex);

    std::filesystem::path log_dir = config.log_directory.empty()
        ? PathResolver::get_log_dir()
        : std::filesystem::path(config.log_directory);

    std::error_code ec;
    std::filesystem::create_directories(log_dir, ec);

    g_active_log_file = (log_dir / "legatusd.log").string();

    std::vector<spdlog::sink_ptr> sinks;
    sinks.push_back(std::make_shared<spdlog::sinks::stdout_color_sink_mt>());

    size_t max_size = static_cast<size_t>(config.max_file_size_mb > 0 ? config.max_file_size_mb : 50) * 1024 * 1024;
    size_t max_files = config.max_backup_files > 0 ? config.max_backup_files : 5;

    try {
        auto rotating_sink = std::make_shared<spdlog::sinks::rotating_file_sink_mt>(
            g_active_log_file, max_size, max_files);
        sinks.push_back(rotating_sink);
    } catch (...) {
        // Fallback to stdout only if file cannot be opened
    }

    auto combined_logger = std::make_shared<spdlog::logger>(
        "legatus", sinks.begin(), sinks.end());
    spdlog::set_default_logger(combined_logger);

    set_log_level_string(config.level);
    spdlog::flush_on(spdlog::level::info);
}

bool set_log_level_string(const std::string& level) {
    if (level == "trace") {
        spdlog::set_level(spdlog::level::trace);
    } else if (level == "debug") {
        spdlog::set_level(spdlog::level::debug);
    } else if (level == "info") {
        spdlog::set_level(spdlog::level::info);
    } else if (level == "warn" || level == "warning") {
        spdlog::set_level(spdlog::level::warn);
    } else if (level == "err" || level == "error") {
        spdlog::set_level(spdlog::level::err);
    } else if (level == "critical") {
        spdlog::set_level(spdlog::level::critical);
    } else if (level == "off") {
        spdlog::set_level(spdlog::level::off);
    } else {
        return false;
    }
    return true;
}

std::string get_active_log_level_string() {
    auto lvl = spdlog::get_level();
    switch (lvl) {
        case spdlog::level::trace: return "trace";
        case spdlog::level::debug: return "debug";
        case spdlog::level::info: return "info";
        case spdlog::level::warn: return "warn";
        case spdlog::level::err: return "error";
        case spdlog::level::critical: return "critical";
        case spdlog::level::off: return "off";
        default: return "unknown";
    }
}

std::string get_active_log_file_path() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    return g_active_log_file;
}

} // namespace legatus::common
