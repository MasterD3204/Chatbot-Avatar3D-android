#include "mnn_tts_sdk.hpp"
#include "piper/utf8.h"
#include "supertonic/mnn_supertonic_tts_impl.hpp"
#include "mnn_tts_logger.hpp"
#include "nlohmann/json.hpp"
#include <codecvt>
#include <locale>
#include <mutex>

using json = nlohmann::json;

// 辅助方法：将 JSON 字符串解析为 std::map<string, string>
std::map<std::string, std::string> MNNTTSSDK::parseJsonToMap(const std::string &json_str) {
  std::map<std::string, std::string> result;

  if (json_str.empty() || json_str == "{}") {
    return result;
  }

  try {
    json j = json::parse(json_str);
    for (auto& [key, value] : j.items()) {
      if (value.is_string()) {
        result[key] = value.get<std::string>();
      } else if (value.is_number_integer()) {
        result[key] = std::to_string(value.get<int>());
      } else if (value.is_number_float()) {
        result[key] = std::to_string(value.get<float>());
      } else if (value.is_boolean()) {
        result[key] = value.get<bool>() ? "true" : "false";
      } else {
        result[key] = value.dump();
      }
    }
  } catch (const json::exception &e) {
    PLOG(ERROR, "Failed to parse JSON: " + std::string(e.what()));
  }

  return result;
}

MNNTTSSDK::MNNTTSSDK(const std::string &config_folder, const std::string &params_json)
{
  std::string config_json_path = config_folder + "/config.json";

  // 1. 解析 JSON 为 map
  auto overrides = parseJsonToMap(params_json);

  // 2. 创建 MNNTTSConfig，传入 overrides
  auto config = MNNTTSConfig(config_json_path, overrides);

  model_type_ = config.model_type_;
  auto model_path    = config_folder + "/" + config.model_path_;
  auto asset_folder  = config_folder + "/" + config.asset_folder_;
  auto cache_folder  = config_folder + "/" + config.cache_folder_;
  sample_rate_       = config.sample_rate_;

  if (model_type_ == "piper")
  {
    // asset_folder = espeak-ng-data directory
    // model_path   = .mnn model file
    // config_json_path = Piper config.json (for phoneme_id_map, voice, sample_rate)
    impl_ = std::make_shared<MNNPiperTTSImpl>(
        asset_folder,        // espeak_data_path
        model_path,          // .mnn model path
        config_json_path,    // Piper config.json
        cache_folder         // cache_path (unused currently)
    );
    // Override sample_rate from MNNPiperTTSImpl (reads it from config.json)
    auto piper_impl = std::dynamic_pointer_cast<MNNPiperTTSImpl>(impl_);
    if (piper_impl) {
      sample_rate_ = piper_impl->GetSampleRate();
    }
    PLOG(INFO, "Piper TTS initialized, sample_rate: " + std::to_string(sample_rate_));
  }
  else if (model_type_ == "bertvits")
  {
    impl_ = std::make_shared<MNNBertVits2TTSImpl>(asset_folder, model_path, cache_folder);
  }
  else if (model_type_ == "supertonic")
  {
    impl_ = std::make_shared<MNNSupertonicTTSImpl>(config_folder, overrides);
  }
  else
  {
    throw std::runtime_error("Invalid model type: " + model_type_);
  }
}

std::tuple<int, Audio> MNNTTSSDK::Process(const std::string &text)
{
  return impl_->Process(text);
}

void MNNTTSSDK::WriteAudioToFile(const Audio &audio_data,
                                 const std::string &output_file_path)
{
  std::ofstream audioFile(output_file_path, std::ios::binary);
  writeWavHeader(sample_rate_, 2, 1, (int32_t)audio_data.size(), audioFile);
  audioFile.write((const char *)audio_data.data(),
                  sizeof(int16_t) * audio_data.size());
}

void MNNTTSSDK::SetSpeakerId(const std::string &speaker_id)
{
  if (model_type_ != "supertonic")
  {
    PLOG(WARNING, "SetSpeakerId is only supported for supertonic model, current: " + model_type_);
    return;
  }
  auto supertonic_impl = std::dynamic_pointer_cast<MNNSupertonicTTSImpl>(impl_);
  if (supertonic_impl) {
    supertonic_impl->SetSpeakerId(speaker_id);
  } else {
    PLOG(ERROR, "Failed to cast impl to MNNSupertonicTTSImpl");
  }
}

