#pragma once

#include "legatus/stdlib/task_sandbox_context.hpp"
#include <solix/native_registry.hpp>

namespace legatus::stdlib {

/**
 * Registers all sandboxed standard library native functions into the Solix NativeRegistry.
 * <p>
 * Under ADR-0009:
 * - Safe solixlib natives (core, collections, math, time, crypto, streams, console) are registered.
 * - Prohibited natives (solix.system.Process, solix.io.filesystem) are strictly OMITTED.
 * - New serverless capabilities (legatus.net.http, legatus.json, legatus.runtime,
 *   legatus.crypto.extended, legatus.validation.regex, legatus.encoding, legatus.uuid,
 *   legatus.storage.kv, legatus.env) are registered.
 */
void register_sandboxed_stdlib(solix::NativeRegistry &registry, const TaskSandboxContext &context);

void register_http_natives(solix::NativeRegistry &registry);
void register_json_natives(solix::NativeRegistry &registry);
void register_runtime_natives(solix::NativeRegistry &registry, const TaskSandboxContext &context);
void register_crypto_extended_natives(solix::NativeRegistry &registry);
void register_regex_natives(solix::NativeRegistry &registry);
void register_encoding_natives(solix::NativeRegistry &registry);
void register_uuid_natives(solix::NativeRegistry &registry);
void register_kv_natives(solix::NativeRegistry &registry);
void register_env_natives(solix::NativeRegistry &registry, const TaskSandboxContext &context);

} // namespace legatus::stdlib
