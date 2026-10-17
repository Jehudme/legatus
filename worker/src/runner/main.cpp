#include "legatus/runner/runner_engine.hpp"
#include <iostream>
#include <string>
#include <csignal>
#include <cstdlib>

#if defined(__unix__) || defined(__APPLE__) || defined(__linux__)
#include <sys/resource.h>
#endif

namespace {

void signal_handler(int sig) {
    (void)sig;
    std::_Exit(130);
}

void suppress_core_dumps() {
#if defined(__unix__) || defined(__APPLE__) || defined(__linux__)
    struct rlimit rl;
    rl.rlim_cur = 0;
    rl.rlim_max = 0;
    setrlimit(RLIMIT_CORE, &rl);
#endif
}

} // namespace

int main(int argc, char* argv[]) {
    suppress_core_dumps();

    std::signal(SIGTERM, signal_handler);
    std::signal(SIGINT, signal_handler);
#if defined(SIGBREAK)
    std::signal(SIGBREAK, signal_handler);
#endif

    std::string result_file;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if ((arg == "--result-file" || arg == "-r") && i + 1 < argc) {
            result_file = argv[++i];
        }
    }

    int code = legatus::runner::run_task_runner(std::cin, std::cout, result_file);
    std::cout.flush();
    std::cerr.flush();
    return code;
}
