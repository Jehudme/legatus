#include "legatus/version.hpp"
#include <iostream>

int main(int argc, char* argv[]) {
    (void)argc;
    (void)argv;
    std::cout << "legatus-runner v" << legatus::VERSION << "\n";
    return 0;
}
