cmake_minimum_required(VERSION 3.28)
project(legatus-worker VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(MSVC)
    add_compile_options(/W4 /permissive-)
    add_compile_definitions(_CRT_SECURE_NO_WARNINGS NOMINMAX WIN32_LEAN_AND_MEAN)
else()
    add_compile_options(-Wall -Wextra)
endif()

# Sanitizer configuration
option(LEGATUS_ENABLE_SANITIZERS "Enable Address and UndefinedBehavior Sanitizers" OFF)
if(LEGATUS_ENABLE_SANITIZERS)
    if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang|AppleClang")
        message(STATUS "Enabling AddressSanitizer and UndefinedBehaviorSanitizer")
        add_compile_options(-fsanitize=address,undefined -fno-omit-frame-pointer)
        add_link_options(-fsanitize=address,undefined)
    endif()
endif()

find_package(OpenSSL REQUIRED)

include(FetchContent)

# 1. Solix language runtime (v1.1.1 from GitHub)
option(USE_LOCAL_SOLIX "Use local solix checkout from ~/Projects/solix" OFF)
if(USE_LOCAL_SOLIX AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../../solix/CMakeLists.txt")
    message(STATUS "Using local Solix repository at ${CMAKE_CURRENT_SOURCE_DIR}/../../solix")
    FetchContent_Declare(
        solix
        SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../solix"
    )
else()
    message(STATUS "Fetching Solix v1.1.1 from GitHub")
    FetchContent_Declare(
        solix
        GIT_REPOSITORY https://github.com/Jehudme/solix.git
        GIT_TAG        v1.1.1
    )
endif()

# fmt & spdlog
FetchContent_Declare(
    fmt
    GIT_REPOSITORY https://github.com/fmtlib/fmt.git
    GIT_TAG        11.1.4
)

set(SPDLOG_FMT_EXTERNAL ON CACHE BOOL "Use external fmt" FORCE)
FetchContent_Declare(
    spdlog
    GIT_REPOSITORY https://github.com/gabime/spdlog.git
    GIT_TAG        v1.15.1
)

# nlohmann_json
FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG        v3.11.3
)

# CLI11
FetchContent_Declare(
    cli11
    GIT_REPOSITORY https://github.com/CLIUtils/CLI11.git
    GIT_TAG        v2.4.2
)

# tomlplusplus
FetchContent_Declare(
    tomlplusplus
    GIT_REPOSITORY https://github.com/marzer/tomlplusplus.git
    GIT_TAG        v3.4.0
)

# nng
set(NNG_TESTS OFF CACHE BOOL "Disable NNG tests" FORCE)
set(NNG_TOOLS OFF CACHE BOOL "Disable NNG tools" FORCE)
set(NNG_ENABLE_DOC OFF CACHE BOOL "Disable NNG docs" FORCE)
FetchContent_Declare(
    nng
    GIT_REPOSITORY https://github.com/nanomsg/nng.git
    GIT_TAG        v1.9.0
)

# reproc
set(REPROC_INSTALL OFF CACHE BOOL "Disable reproc install" FORCE)
set(REPROC++ ON CACHE BOOL "Build reproc++ C++ wrapper" FORCE)
set(REPROC_TEST OFF CACHE BOOL "Disable reproc tests" FORCE)
FetchContent_Declare(
    reproc
    GIT_REPOSITORY https://github.com/DaanDeMeyer/reproc.git
    GIT_TAG        v14.2.5
)

# IXWebSocket
set(USE_TLS ON CACHE BOOL "Enable TLS in IXWebSocket" FORCE)
set(USE_OPENSSL ON CACHE BOOL "Use OpenSSL in IXWebSocket" FORCE)
FetchContent_Declare(
    ixwebsocket
    GIT_REPOSITORY https://github.com/machinezone/IXWebSocket.git
    GIT_TAG        v11.4.5
)

# Catch2
FetchContent_Declare(
    Catch2
    GIT_REPOSITORY https://github.com/catchorg/Catch2.git
    GIT_TAG        v3.5.2
)

FetchContent_MakeAvailable(fmt spdlog nlohmann_json cli11 tomlplusplus nng reproc ixwebsocket Catch2 solix)

# -----------------------------------------------------------------------------
# Common Library: legatus_common
# -----------------------------------------------------------------------------
add_library(legatus_common
    src/common/crypto_utils.cpp
    src/common/base64.cpp
    src/common/paths.cpp
    src/common/telemetry_collector.cpp
)

target_include_directories(legatus_common PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

target_link_libraries(legatus_common PUBLIC
    OpenSSL::Crypto
    OpenSSL::SSL
    spdlog::spdlog
    fmt::fmt
    nlohmann_json::nlohmann_json
    tomlplusplus::tomlplusplus
)

# -----------------------------------------------------------------------------
# Runner Library: legatus_runner_core
# -----------------------------------------------------------------------------
add_library(legatus_runner_core
    src/runner/legatus_runtime_context.cpp
    src/runner/runner_engine.cpp
)

target_include_directories(legatus_runner_core PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

target_link_libraries(legatus_runner_core PUBLIC
    legatus_common
    solix_core
)

# -----------------------------------------------------------------------------
# Daemon Library: legatus_daemon_core
# -----------------------------------------------------------------------------
add_library(legatus_daemon_core
    src/daemon/process_supervisor.cpp
    src/daemon/worker_config.cpp
    src/daemon/enrollment_client.cpp
    src/daemon/ipc_bus.cpp
    src/daemon/lctp_client.cpp
    src/daemon/bytecode_cache.cpp
)

target_include_directories(legatus_daemon_core PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

target_link_libraries(legatus_daemon_core PUBLIC
    legatus_common
    legatus_runner_core
    reproc++
    nng
    ixwebsocket
)

# -----------------------------------------------------------------------------
# Executables
# -----------------------------------------------------------------------------

# 1. legatus-runner
add_executable(legatus-runner
    src/runner/main.cpp
)
target_link_libraries(legatus-runner PRIVATE
    legatus_runner_core
)

# 2. legatusd
add_executable(legatusd
    src/daemon/main.cpp
)
target_link_libraries(legatusd PRIVATE
    legatus_daemon_core
)

# 3. legatus (CLI)
add_executable(legatus
    src/cli/main.cpp
)
target_link_libraries(legatus PRIVATE
    legatus_common
    CLI11::CLI11
    nng
)

# -----------------------------------------------------------------------------
# Testing: Catch2
# -----------------------------------------------------------------------------
enable_testing()

add_executable(legatus_tests
    tests/unit/test_common_scaffold.cpp
    tests/unit/test_runner_engine.cpp
    tests/unit/test_process_supervisor.cpp
    tests/unit/test_crypto_enrollment.cpp
    tests/unit/test_ipc_bus.cpp
    tests/unit/test_lctp_client.cpp
)

target_compile_definitions(legatus_tests PRIVATE
    LEGATUS_RUNNER_PATH="$<TARGET_FILE:legatus-runner>"
)

target_link_libraries(legatus_tests PRIVATE
    legatus_common
    legatus_runner_core
    legatus_daemon_core
    Catch2::Catch2WithMain
    solix_core
    nng
    reproc++
    ixwebsocket
)

target_include_directories(legatus_tests PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

include(Catch)
catch_discover_tests(legatus_tests)
