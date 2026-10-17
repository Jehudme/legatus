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
find_package(CURL REQUIRED)
find_package(ZLIB REQUIRED)
find_package(re2 CONFIG QUIET)
if(re2_FOUND)
    set(RE2_TARGET re2::re2)
else()
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(RE2 REQUIRED re2)
    set(RE2_TARGET ${RE2_LIBRARIES})
endif()

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
    src/common/logging.cpp
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
# Sandboxed Standard Library: legatus_stdlib_native
# -----------------------------------------------------------------------------
add_library(legatus_stdlib_native
    src/stdlib/sandbox.cpp
    src/stdlib/http_client.cpp
    src/stdlib/task_context.cpp
    src/stdlib/json_engine.cpp
    src/stdlib/crypto_extended.cpp
    src/stdlib/regex_engine.cpp
    src/stdlib/encoding_engine.cpp
    src/stdlib/uuid_engine.cpp
    src/stdlib/kv_cache.cpp
    src/stdlib/env_engine.cpp
    src/stdlib/solix/primitives.cpp
    src/stdlib/solix/string.cpp
    src/stdlib/solix/math.cpp
    src/stdlib/solix/time.cpp
    src/stdlib/solix/crypto.cpp
    src/stdlib/solix/console.cpp
)

target_include_directories(legatus_stdlib_native PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<BUILD_INTERFACE:${solix_SOURCE_DIR}/core/include>
    $<INSTALL_INTERFACE:include>
    ${RE2_INCLUDE_DIRS}
)

target_link_directories(legatus_stdlib_native PUBLIC
    ${RE2_LIBRARY_DIRS}
)

target_link_libraries(legatus_stdlib_native PUBLIC
    legatus_common
    solix_core
    CURL::libcurl
    ZLIB::ZLIB
    ${RE2_TARGET}
    OpenSSL::Crypto
    OpenSSL::SSL
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
    legatus_stdlib_native
    solix_core
)

# -----------------------------------------------------------------------------
# Daemon Library: legatus_daemon_core
# -----------------------------------------------------------------------------
add_library(legatus_daemon_core
    src/daemon/process_supervisor.cpp
    src/daemon/worker_config.cpp
    src/daemon/config_manager.cpp
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
    CLI11::CLI11
)
target_compile_definitions(legatusd PRIVATE
    LEGATUS_RUNNER_PATH="$<TARGET_FILE:legatus-runner>"
)

# 3. legatus (CLI)
add_executable(legatus
    src/cli/main.cpp
)
target_link_libraries(legatus PRIVATE
    legatus_daemon_core
    CLI11::CLI11
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
    tests/unit/test_telemetry_and_cache.cpp
    tests/unit/test_telemetry_normalized_cpu.cpp
    tests/unit/test_config_manager.cpp
    tests/unit/test_cli_and_logging.cpp
    tests/unit/test_stdlib_sandbox.cpp
    tests/unit/test_stdlib_http.cpp
    tests/unit/test_stdlib_json.cpp
    tests/unit/test_stdlib_crypto.cpp
    tests/unit/test_stdlib_regex.cpp
    tests/unit/test_stdlib_encoding_and_kv.cpp
    tests/integration/test_runner_isolation.cpp
    tests/integration/test_bytecode_cache_fallback.cpp
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

# -----------------------------------------------------------------------------
# Precompiled Headers (PCH)
# -----------------------------------------------------------------------------
target_precompile_headers(legatus_common PUBLIC
    "$<$<COMPILE_LANGUAGE:CXX>:${CMAKE_CURRENT_SOURCE_DIR}/include/legatus/pch.hpp>"
)

# -----------------------------------------------------------------------------
# Installation & CPack Packaging
# -----------------------------------------------------------------------------
include(GNUInstallDirs)

install(TARGETS legatus legatusd legatus-runner
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
)

install(DIRECTORY include/legatus/
    DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/legatus
    FILES_MATCHING PATTERN "*.hpp"
)

set(CPACK_PACKAGE_NAME "legatus-worker")
set(CPACK_PACKAGE_VENDOR "Legatus Project")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "Legatus Worker Subsystem (legatusd, legatus, legatus-runner)")
set(CPACK_PACKAGE_VERSION "${PROJECT_VERSION}")
set(CPACK_PACKAGE_VERSION_MAJOR "${PROJECT_VERSION_MAJOR}")
set(CPACK_PACKAGE_VERSION_MINOR "${PROJECT_VERSION_MINOR}")
set(CPACK_PACKAGE_VERSION_PATCH "${PROJECT_VERSION_PATCH}")
set(CPACK_PACKAGE_CONTACT "maintainers@legatus.dev")

if(UNIX AND NOT APPLE)
    set(CPACK_GENERATOR "TGZ;DEB;RPM")
    set(CPACK_DEBIAN_PACKAGE_MAINTAINER "maintainers@legatus.dev")
    set(CPACK_DEBIAN_PACKAGE_SECTION "utils")
    set(CPACK_DEBIAN_PACKAGE_DEPENDS "libc6 (>= 2.34), libssl3 (>= 3.0.0)")
    set(CPACK_RPM_PACKAGE_LICENSE "Apache-2.0")
    set(CPACK_RPM_PACKAGE_GROUP "Applications/System")
elseif(APPLE)
    set(CPACK_GENERATOR "TGZ;productbuild")
elseif(WIN32)
    set(CPACK_GENERATOR "ZIP;WIX")
endif()

include(CPack)
