#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <re2/re2.h>
#include <string>
#include <memory>
#include <mutex>
#include <list>
#include <unordered_map>

namespace legatus::stdlib {

namespace {

class RegexCache {
public:
    static constexpr size_t MAX_CAPACITY = 256;

    std::shared_ptr<re2::RE2> get_or_compile(const std::string& pattern_str) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(pattern_str);
        if (it != map_.end()) {
            lru_list_.splice(lru_list_.begin(), lru_list_, it->second);
            return it->second->second;
        }

        auto re = std::make_shared<re2::RE2>(pattern_str, re2::RE2::Quiet);
        lru_list_.emplace_front(pattern_str, re);
        map_[pattern_str] = lru_list_.begin();

        if (map_.size() > MAX_CAPACITY) {
            auto last = lru_list_.end();
            --last;
            map_.erase(last->first);
            lru_list_.pop_back();
        }

        return re;
    }

private:
    std::mutex mutex_;
    std::list<std::pair<std::string, std::shared_ptr<re2::RE2>>> lru_list_;
    std::unordered_map<std::string, std::list<std::pair<std::string, std::shared_ptr<re2::RE2>>>::iterator> map_;
};

static RegexCache g_regex_cache;

static uint64_t regex_native_is_match(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    std::string pattern_str = read_solix_chars(vm, args[0]);
    std::string text = read_solix_chars(vm, args[1]);

    auto pattern = g_regex_cache.get_or_compile(pattern_str);
    if (!pattern || !pattern->ok()) {
        return 0ULL;
    }

    bool matched = re2::RE2::PartialMatch(text, *pattern);
    return matched ? 1ULL : 0ULL;
}

static uint64_t regex_native_replace(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string pattern_str = read_solix_chars(vm, args[0]);
    std::string text = read_solix_chars(vm, args[1]);
    std::string replacement = read_solix_chars(vm, args[2]);

    auto pattern = g_regex_cache.get_or_compile(pattern_str);
    if (!pattern || !pattern->ok()) {
        return allocate_solix_chars(vm, text);
    }

    re2::RE2::GlobalReplace(&text, *pattern, replacement);
    return allocate_solix_chars(vm, text);
}

static uint64_t regex_native_extract(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string pattern_str = read_solix_chars(vm, args[0]);
    std::string text = read_solix_chars(vm, args[1]);

    auto pattern = g_regex_cache.get_or_compile(pattern_str);
    if (!pattern || !pattern->ok()) {
        return 0ULL; // null
    }

    std::string match;
    if (re2::RE2::PartialMatch(text, *pattern, &match)) {
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
