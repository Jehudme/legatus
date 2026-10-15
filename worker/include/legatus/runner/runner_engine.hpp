#pragma once

#include "legatus/runner/execution_envelope.hpp"
#include "legatus/runner/secret_zeroizer.hpp"
#include <iostream>

namespace legatus::runner {

ExecutionResult execute_task(ExecutionEnvelope& envelope, SecretZeroizer& zeroizer);

int run_task_runner(std::istream& in, std::ostream& out);

} // namespace legatus::runner
