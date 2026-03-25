#include "mnn_tts_config.hpp"

MNNTTSConfig::MNNTTSConfig(const std::string &config_json_path)
{
  if (!fs::exists(config_json_path) || !fs::is_regular_file(config_json_path))
  {
    throw std::runtime_error("Config file not found: " + config_json_path);
  }

  std::ifstream file(config_json_path);
  if (!file.is_open())
  {
    throw std::runtime_error("Failed to open config file: " + config_json_path);
  }

  try
  {
    file >> raw_config_data_;
  }
  catch (const nlohmann::json::parse_error &e)
  {
    throw std::runtime_error("Error parsing config.json (" + config_json_path + "): " + e.what());
  }

  try
  {
    model_type_ = get_value_from_json<std::string>(raw_config_data_, "model_type");

    // Piper model format: model_path, asset_folder, cache_folder are optional
    // — MNNTTSSDK overrides them anyway when model_type == "piper"
    if (model_type_ == "piper") {
      // Piper config.json (huongly.onnx.json style) doesn't have these fields;
      // they are resolved by MNNTTSSDK from the config_folder directly.
      model_path_   = raw_config_data_.value("model_path", "");
      asset_folder_ = raw_config_data_.value("asset_folder", "espeak-ng-data");
      cache_folder_ = raw_config_data_.value("cache_folder", "cache");

      // Read sample_rate from audio.sample_rate (Piper format)
      if (raw_config_data_.contains("audio") &&
          raw_config_data_["audio"].contains("sample_rate")) {
        sample_rate_ = raw_config_data_["audio"]["sample_rate"].get<int>();
      } else {
        sample_rate_ = raw_config_data_.value("sample_rate", 22050);
      }
    } else {
      model_path_   = get_value_from_json<std::string>(raw_config_data_, "model_path");
      asset_folder_ = get_value_from_json<std::string>(raw_config_data_, "asset_folder");
      cache_folder_ = get_value_from_json<std::string>(raw_config_data_, "cache_folder");
      sample_rate_  = get_value_from_json<int>(raw_config_data_, "sample_rate");
    }
  }
  catch (const std::runtime_error &e)
  {
    throw std::runtime_error("Error in config file " + config_json_path + ": " + e.what());
  }
}

MNNTTSConfig::MNNTTSConfig(const std::string &config_file_path,
                           const std::map<std::string, std::string> &overrides)
    : MNNTTSConfig(config_file_path)
{
  applyOverrides(overrides);
}

void MNNTTSConfig::applyOverrides(const std::map<std::string, std::string> &overrides) {
  if (overrides.empty()) return;

  for (const auto& [key, value] : overrides) {
    try {
      if      (key == "model_type")   model_type_   = value;
      else if (key == "sample_rate")  sample_rate_  = std::stoi(value);
      else if (key == "model_path")   model_path_   = value;
      else if (key == "asset_folder") asset_folder_ = value;
    } catch (const std::exception &e) {
      std::cerr << "Warning: Failed to override '" << key
                << "' = '" << value << "': " << e.what() << std::endl;
    }
  }
}

