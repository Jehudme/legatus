#include "legatus/runner/runner_engine.hpp"
#include <iostream>

int main(int argc, char* argv[]) {
    (void)argc;
    (void)argv;
    return legatus::runner::run_task_runner(std::cin, std::cout);
}
