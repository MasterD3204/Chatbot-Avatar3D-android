#include "common/mnn_backend.hpp"

#include <algorithm>
#include <cctype>

namespace TaoAvatar {

namespace {

std::string NormalizeBackendName(const std::string& backend_name) {
    std::string normalized = backend_name;
    std::transform(
        normalized.begin(),
        normalized.end(),
        normalized.begin(),
        [](unsigned char c) { return static_cast<char>(std::tolower(c)); }
    );
    return normalized;
}

} // namespace

MnnBackend ParseMnnBackend(const std::string& backend_name) {
    const auto normalized = NormalizeBackendName(backend_name);
    if (normalized == "opencl") {
        return MnnBackend::OPENCL;
    }
    if (normalized == "vulkan") {
        return MnnBackend::VULKAN;
    }
    return MnnBackend::CPU;
}

const char* GetMnnBackendName(MnnBackend backend) {
    switch (backend) {
        case MnnBackend::OPENCL:
            return "opencl";
        case MnnBackend::VULKAN:
            return "vulkan";
        case MnnBackend::CPU:
        default:
            return "cpu";
    }
}

std::vector<MnnBackend> GetMnnBackendFallbackOrder(const std::string& preferred_backend) {
    const auto parsed = ParseMnnBackend(preferred_backend);
    switch (parsed) {
        case MnnBackend::OPENCL:
            return {MnnBackend::OPENCL, MnnBackend::CPU};
        case MnnBackend::VULKAN:
            return {MnnBackend::VULKAN, MnnBackend::CPU};
        case MnnBackend::CPU:
        default:
            return {MnnBackend::CPU};
    }
}

MNNForwardType ToMnnForwardType(MnnBackend backend) {
    switch (backend) {
        case MnnBackend::OPENCL:
            return MNN_FORWARD_OPENCL;
        case MnnBackend::VULKAN:
            return MNN_FORWARD_VULKAN;
        case MnnBackend::CPU:
        default:
            return MNN_FORWARD_CPU;
    }
}

MNN::BackendConfig CreateBackendConfig(MnnBackend backend) {
    MNN::BackendConfig config;
    config.precision = MNN::BackendConfig::Precision_High;
    config.power = MNN::BackendConfig::Power_High;
    config.memory = backend == MnnBackend::CPU
        ? MNN::BackendConfig::Memory_Low
        : MNN::BackendConfig::Memory_Normal;
    return config;
}

std::shared_ptr<MNN::Express::Executor> CreateExecutorForBackend(
    MnnBackend backend,
    int num_threads,
    const MNN::BackendConfig& backend_config
) {
    return MNN::Express::Executor::newExecutor(
        ToMnnForwardType(backend),
        backend_config,
        num_threads
    );
}

std::shared_ptr<MNN::Express::Executor::RuntimeManager> CreateRuntimeManagerForBackend(
    MnnBackend backend,
    int num_threads,
    MNN::BackendConfig& backend_config
) {
    MNN::ScheduleConfig schedule_config;
    schedule_config.numThread = num_threads;
    schedule_config.type = ToMnnForwardType(backend);
    schedule_config.backupType = MNN_FORWARD_CPU;
    schedule_config.backendConfig = &backend_config;
    return std::shared_ptr<MNN::Express::Executor::RuntimeManager>(
        MNN::Express::Executor::RuntimeManager::createRuntimeManager(schedule_config),
        MNN::Express::Executor::RuntimeManager::destroy
    );
}

} // namespace TaoAvatar
