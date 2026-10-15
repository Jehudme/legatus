#pragma once

#include <string>
#include <vector>
#include <iostream>
#include <iomanip>
#include <sstream>

namespace legatus::cli {

class TableFormatter {
public:
    struct Row {
        std::string label;
        std::string value;
    };

    static std::string format_key_value_table(const std::string& title, const std::vector<Row>& rows) {
        size_t max_label = title.length() + 2;
        size_t max_val = 20;

        for (const auto& r : rows) {
            if (r.label.length() > max_label) max_label = r.label.length();
            if (r.value.length() > max_val) max_val = r.value.length();
        }

        std::ostringstream oss;
        std::string h_line = "+" + std::string(max_label + 2, '-') + "+" + std::string(max_val + 2, '-') + "+\n";

        oss << h_line;
        oss << "| " << std::left << std::setw(max_label) << title
            << " | " << std::left << std::setw(max_val) << "" << " |\n";
        oss << h_line;

        for (const auto& r : rows) {
            oss << "| " << std::left << std::setw(max_label) << r.label
                << " | " << std::left << std::setw(max_val) << r.value << " |\n";
        }
        oss << h_line;
        return oss.str();
    }
};

} // namespace legatus::cli
