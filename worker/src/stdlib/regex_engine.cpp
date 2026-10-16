#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <re2/re2.h>
#include <string>

namespace legatus::stdlib {

namespace {

static uint64_t regex_native_is_match(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    std::string pattern_str = read_solix_chars(vm, args[0]);
    std::string text = read_solix_chars(vm, args[1]);

    re2::RE2 pattern(pattern_str, re2::RE2::Quiet);
    if (!pattern.ok()) {
        return 0ULL;
    }

    bool matched = re2::RE2::PartialMatch(text, pattern);
    return matched ? 1ULL : 0ULL;
}

static uint64_t regex_native_replace(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string pattern_str = read_solix_chars(vm, args[0]);
    std::string text = read_solix_chars(vm, args[1]);
    std::string replacement = read_solix_chars(vm, args[2]);

    re2::RE2 pattern(pattern_str, re2::RE2::Quiet);
    if (!pattern.ok()) {
        return allocate_solix_chars(vm, text);
    }

    re2::RE2::GlobalReplace(&text, pattern, replacement);
    return allocate_solix_chars(vm, text);
}

static uint64_t regex_native_extract(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string pattern_str = read_solix_chars(vm, args[0]);
    std::string text = read_solix_chars(vm, args[1]);

    re2::RE2 pattern(pattern_str, re2::RE2::Quiet);
    if (!pattern.ok()) {
        return 0ULL; // null
    }

    std::string match;
    if (re2::RE2::PartialMatch(text, pattern, &match)) {
        return allocate_solix_chars(vm, match);
    }
    return 0ULL; // null
}

} // namespace

void register_regex_natives(solix::NativeRegistry &registry) {
    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_validation_regex_" + cls + "_" + name, func);
        registry.register_function("legatus.validation.regex." + cls + "." + name, func);
    };

    reg("Regex", "native_is_match", regex_native_is_match);
    reg("Regex", "native_replace", regex_native_replace);
    reg("Regex", "native_extract", regex_native_extract);
}

} // namespace legatus::stdlib
