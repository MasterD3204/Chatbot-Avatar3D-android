#pragma once

#include <chrono>
#include <map>
#include <string>
#include <vector>
#include <mutex>

#include "mnn_tts_impl_base.hpp"
#include "audio_generator.hpp"
#include "phoneme_ids.hpp"
#include "espeak_ng_wrapper.hpp"
#include "mnn_tts_logger.hpp"
#include "uni_algo.hpp"
#include "nlohmann/json.hpp"

typedef std::chrono::milliseconds ms;
using clk = std::chrono::system_clock;

typedef std::vector<int16_t> Audio;

class MNNPiperTTSImpl: public MNNTTSImplBase
{
public:
  // Constructor nhận config từ MNNTTSSDK (espeak_data_path, model_path, config_json_path)
  MNNPiperTTSImpl(const std::string &espeak_data_path,
                  const std::string &model_path,
                  const std::string &config_json_path,
                  const std::string &cache_path);

  void phonemize_eSpeak(const std::string &text, std::vector<std::vector<Phoneme>> &phonemes);

  std::vector<int16_t> synthesize(std::vector<PhonemeId> &phonemeIds);

  // Phonemize text and synthesize audio
  std::tuple<int, Audio> Process(const std::string& text) override;

  int GetSampleRate() const { return sample_rate_; }

private:
  // Load phoneme_id_map từ Piper config JSON
  PhonemeIdMap loadPhonemeIdMap(const nlohmann::json &config_json);

  std::mutex mtx_;
  AudioGenerator audio_generator_;
  PhonemeIdMap phone_id_map_;
  int sample_rate_ = 22050;
  std::string espeak_voice_; // e.g. "vi", "en-us"

  Phoneme period      = U'.';
  Phoneme comma       = U',';
  Phoneme question    = U'?';
  Phoneme exclamation = U'!';
  Phoneme colon       = U':';
  Phoneme semicolon   = U';';
  Phoneme space       = U' ';
};