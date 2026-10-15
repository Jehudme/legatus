#pragma once

#include "legatus/daemon/worker_config.hpp"
#include <string>

namespace legatus::common {

void init_logging(const daemon::LoggingConfig& config);

bool set_log_level_string(const std::string& level);

std::string get_active_log_level_string();

std::string get_active_log_file_path();

} // namespace legatus::common
