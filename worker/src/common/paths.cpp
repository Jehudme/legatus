#include "legatus/common/paths.hpp"
#include <cstdlib>

#if defined(_WIN32)
#include <windows.h>
#include <shlobj.h>
#else
#include <unistd.h>
#include <pwd.h>
#endif

namespace legatus::common {

static std::filesystem::path get_home_dir() {
#if defined(_WIN32)
    const char* user_profile = std::getenv("USERPROFILE");
    if (user_profile) return std::filesystem::path(user_profile);
    return std::filesystem::current_path();
#else
    const char* home = std::getenv("HOME");
    if (home) return std::filesystem::path(home);
    struct passwd* pw = getpwuid(getuid());
    if (pw && pw->pw_dir) return std::filesystem::path(pw->pw_dir);
    return std::filesystem::current_path();
#endif
}

std::filesystem::path PathResolver::get_config_path() {
    // If local worker.toml exists in cwd, prioritize it
    if (std::filesystem::exists("worker.toml")) {
        return std::filesystem::absolute("worker.toml");
    }

#if defined(_WIN32)
    const char* appdata = std::getenv("LOCALAPPDATA");
    if (appdata) {
        return std::filesystem::path(appdata) / "Legatus" / "worker.toml";
    }
    return get_home_dir() / "AppData" / "Local" / "Legatus" / "worker.toml";
#else
    if (getuid() == 0) {
        return std::filesystem::path("/etc/legatus/worker.toml");
    }
    const char* xdg_config = std::getenv("XDG_CONFIG_HOME");
    if (xdg_config && *xdg_config) {
        return std::filesystem::path(xdg_config) / "legatus" / "worker.toml";
    }
    return get_home_dir() / ".config" / "legatus" / "worker.toml";
#endif
}

std::filesystem::path PathResolver::get_state_dir() {
#if defined(_WIN32)
    const char* appdata = std::getenv("LOCALAPPDATA");
    if (appdata) {
        return std::filesystem::path(appdata) / "Legatus" / "state";
    }
    return get_home_dir() / "AppData" / "Local" / "Legatus" / "state";
#else
    if (getuid() == 0) {
        return std::filesystem::path("/var/lib/legatus");
    }
    const char* xdg_data = std::getenv("XDG_DATA_HOME");
    if (xdg_data && *xdg_data) {
        return std::filesystem::path(xdg_data) / "legatus";
    }
    return get_home_dir() / ".local" / "share" / "legatus";
#endif
}

std::filesystem::path PathResolver::get_log_dir() {
#if defined(_WIN32)
    const char* appdata = std::getenv("LOCALAPPDATA");
    if (appdata) {
        return std::filesystem::path(appdata) / "Legatus" / "logs";
    }
    return get_home_dir() / "AppData" / "Local" / "Legatus" / "logs";
#else
    if (getuid() == 0) {
        return std::filesystem::path("/var/log/legatus");
    }
    const char* xdg_state = std::getenv("XDG_STATE_HOME");
    if (xdg_state && *xdg_state) {
        return std::filesystem::path(xdg_state) / "legatus" / "log";
    }
    return get_home_dir() / ".local" / "state" / "legatus" / "log";
#endif
}

std::filesystem::path PathResolver::get_bytecode_cache_dir() {
    return get_state_dir() / "bytecode_cache";
}

std::filesystem::path PathResolver::get_default_ipc_path() {
#if defined(_WIN32)
    return "ipc://legatus_worker_ipc";
#else
    return "ipc:///tmp/legatus_worker.ipc";
#endif
}

} // namespace legatus::common
