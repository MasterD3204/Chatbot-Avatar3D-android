#pragma once

#include <memory>
#include <string>
#include <vector>

#include "MNN/Interpreter.hpp"
#include "MNN/MNNForwardType.h"
#include "MNN/expr/Executor.hpp"

namespace TaoAvatar {

enum class MnnBackend {
    CPU,
    OPENCL,
    VULKAN,
};

MnnBackend ParseMnnBackend(const std::string& backend_name);
const char* GetMnnBackendName(MnnBackend backend);
std::vector<MnnBackend> GetMnnBackendFallbackOrder(const std::string& preferred_backend);
MNNForwardType ToMnnForwardType(MnnBackend backend);
MNN::BackendConfig CreateBackendConfig(MnnBackend backend);
std::shared_ptr<MNN::Express::Executor> CreateExecutorForBackend(
    MnnBackend backend,
    int num_threads,
    const MNN::BackendConfig& backend_config
);
std::shared_ptr<MNN::Express::Executor::RuntimeManager> CreateRuntimeManagerForBackend(
    MnnBackend backend,
    int num_threads,
    MNN::BackendConfig& backend_config
);

} // namespace TaoAvatar
