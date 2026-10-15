#pragma once

#include <string>
#include <filesystem>

namespace legatus::common {

class PathResolver {
public:
    static std::filesystem::path get_config_path();
    static std::filesystem::path get_state_dir();
    static std::filesystem::path get_log_dir();
    static std::filesystem::path get_bytecode_cache_dir();
    static std::filesystem::path get_default_ipc_path();
};

} // namespace legatus::common
