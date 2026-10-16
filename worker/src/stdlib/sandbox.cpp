#include "legatus/stdlib/sandbox.hpp"

// Forward declarations for safe solixlib natives
void register_console_natives(solix::NativeRegistry &registry);
void register_string_natives(solix::NativeRegistry &registry);
void register_primitives_natives(solix::NativeRegistry &registry);
void register_math_natives(solix::NativeRegistry &registry);
void register_time_natives(solix::NativeRegistry &registry);
void register_crypto_natives(solix::NativeRegistry &registry);

namespace legatus::stdlib {

void register_sandboxed_stdlib(solix::NativeRegistry &registry, const TaskSandboxContext &context) {
    // 1. Inherited Safe solixlib natives (Pure in-memory)
    register_console_natives(registry);
    register_string_natives(registry);
    register_primitives_natives(registry);
    register_math_natives(registry);
    register_time_natives(registry);
    register_crypto_natives(registry);

    // Note: register_io_fs_natives, register_io_stream_natives (FileStream),
    // and register_system_natives (solix.system.Process) are PERMANENTLY EXCLUDED under ADR-0009.

    // 2. New Serverless Compute sublibraries
    register_http_natives(registry);
    register_json_natives(registry);
    register_runtime_natives(registry, context);
    register_crypto_extended_natives(registry);
    register_regex_natives(registry);
    register_encoding_natives(registry);
    register_uuid_natives(registry);
    register_kv_natives(registry);
    register_env_natives(registry, context);
}

} // namespace legatus::stdlib
