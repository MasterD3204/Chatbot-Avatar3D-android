
#include "piper/mnn_piper_tts_impl.hpp"
#include "piper/utf8.h"
#include <fstream>
#include <mutex>
#include <codecvt>
#include <locale>
#include <stdexcept>

// ---------------------------------------------------------------------------
// Helper: convert UTF-8 string to UTF-32 codepoint vector
// ---------------------------------------------------------------------------
static std::u32string utf8ToUtf32(const std::string &utf8_str) {
  std::wstring_convert<std::codecvt_utf8<char32_t>, char32_t> conv;
  return conv.from_bytes(utf8_str);
}

// ---------------------------------------------------------------------------
// Load phoneme_id_map from Piper config JSON
// Piper config stores keys as UTF-8 strings (IPA chars), values as int arrays.
// ---------------------------------------------------------------------------
PhonemeIdMap MNNPiperTTSImpl::loadPhonemeIdMap(const nlohmann::json &config_json) {
  PhonemeIdMap result;
  if (!config_json.contains("phoneme_id_map")) {
    PLOG(WARNING, "No phoneme_id_map in config, using DEFAULT_PHONEME_ID_MAP");
    return DEFAULT_PHONEME_ID_MAP;
  }
  const auto &map_json = config_json["phoneme_id_map"];
  for (auto it = map_json.begin(); it != map_json.end(); ++it) {
    const std::string key_utf8 = it.key();
    const auto &ids_json = it.value();

    // Convert UTF-8 key to UTF-32 codepoints
    auto codepoints = utf8ToUtf32(key_utf8);
    if (codepoints.empty()) continue;
    Phoneme phoneme = static_cast<Phoneme>(codepoints[0]);

    std::vector<PhonemeId> ids;
    for (auto &id_val : ids_json) {
      ids.push_back(id_val.get<int64_t>());
    }
    result[phoneme] = ids;
  }
  return result;
}

// ---------------------------------------------------------------------------
// Constructor: load config JSON → espeak voice + phoneme_id_map + sample_rate
// ---------------------------------------------------------------------------
MNNPiperTTSImpl::MNNPiperTTSImpl(const std::string &espeak_data_path,
                                   const std::string &model_path,
                                   const std::string &config_json_path,
                                   const std::string &cache_path)
{
  // 1. Load Piper config JSON
  std::ifstream cfg_file(config_json_path);
  if (!cfg_file.is_open()) {
    throw std::runtime_error("Cannot open Piper config: " + config_json_path);
  }
  nlohmann::json cfg;
  try {
    cfg_file >> cfg;
  } catch (const nlohmann::json::exception &e) {
    throw std::runtime_error("Invalid Piper config JSON: " + std::string(e.what()));
  }

  // 2. Read sample_rate
  if (cfg.contains("audio") && cfg["audio"].contains("sample_rate")) {
    sample_rate_ = cfg["audio"]["sample_rate"].get<int>();
  }
  PLOG(INFO, "Piper sample_rate: " + std::to_string(sample_rate_));

  // 3. Read espeak voice name (default to "vi" for Vietnamese)
  espeak_voice_ = "vi";
  if (cfg.contains("espeak") && cfg["espeak"].contains("voice")) {
    espeak_voice_ = cfg["espeak"]["voice"].get<std::string>();
  }
  PLOG(INFO, "Piper espeak voice: " + espeak_voice_);

  // 4. Load phoneme_id_map from config
  phone_id_map_ = loadPhonemeIdMap(cfg);
  PLOG(INFO, "Piper phoneme_id_map loaded, size: " + std::to_string(phone_id_map_.size()));

  // 5. Init MNN AudioGenerator (VITS model)
  audio_generator_ = AudioGenerator(model_path);

  // 6. Init espeak-ng
  char path_data[1024];
  PLOG(INFO, "espeak data path: " + espeak_data_path);
  strncpy(path_data, espeak_data_path.c_str(), sizeof(path_data) - 1);
  path_data[sizeof(path_data) - 1] = '\0';

  espeak_AUDIO_OUTPUT output_type = AUDIO_OUTPUT_SYNCHRONOUS;
  int buflength = 500;
  int options   = 0;

  if (espeak_Initialize(output_type, buflength, path_data, options) == EE_INTERNAL_ERROR) {
    throw std::runtime_error(
      "Failed to initialize espeak-ng. Check espeak-ng-data path: " + espeak_data_path);
  }

  // Set voice — use the voice from config (e.g. "vi" for Vietnamese)
  int result = espeak_SetVoiceByName(espeak_voice_.c_str());
  if (result != 0) {
    // Fallback: try with language code prefix
    std::string lang_code = espeak_voice_.substr(0, 2);
    result = espeak_SetVoiceByName(lang_code.c_str());
    if (result != 0) {
      throw std::runtime_error("Failed to set eSpeak-ng voice: " + espeak_voice_);
    }
  }
  PLOG(INFO, "eSpeak-ng voice set to: " + espeak_voice_);
}

// ---------------------------------------------------------------------------
// phonemize_eSpeak: text → IPA phonemes via espeak-ng
// ---------------------------------------------------------------------------
void MNNPiperTTSImpl::phonemize_eSpeak(const std::string &text,
                                        std::vector<std::vector<Phoneme>> &phonemes)
{
  std::string textCopy(text);
  std::vector<Phoneme> *sentencePhonemes = nullptr;
  const char *inputTextPointer = textCopy.c_str();
  int terminator = 0;

  while (inputTextPointer != nullptr) {
    std::string clausePhonemes(espeak_TextToPhonemesWithTerminator(
        (const void **)&inputTextPointer,
        espeakCHARS_AUTO,
        0x02,  // IPA mode
        &terminator));

    // NFD decompose for combining chars (tones, diacritics)
    auto phonemesNorm  = una::norm::to_nfd_utf8(clausePhonemes);
    auto phonemesRange = una::ranges::utf8_view{phonemesNorm};

    if (!sentencePhonemes) {
      phonemes.emplace_back();
      sentencePhonemes = &phonemes.back();
    }

    // Build mapped phonemes, filtering language-switch flags "(xx)"
    std::vector<Phoneme> mappedSentPhonemes(phonemesRange.begin(), phonemesRange.end());
    bool inLanguageFlag = false;

    for (auto &ph : mappedSentPhonemes) {
      if (inLanguageFlag) {
        if (ph == U')') inLanguageFlag = false;
      } else if (ph == U'(') {
        inLanguageFlag = true;
      } else {
        sentencePhonemes->push_back(ph);
      }
    }

    // Append clause-terminal punctuation phoneme
    int punctuation = terminator & 0x000FFFFF;
    if      (punctuation == CLAUSE_PERIOD)      sentencePhonemes->push_back(period);
    else if (punctuation == CLAUSE_QUESTION)    sentencePhonemes->push_back(question);
    else if (punctuation == CLAUSE_EXCLAMATION) sentencePhonemes->push_back(exclamation);
    else if (punctuation == CLAUSE_COMMA)     { sentencePhonemes->push_back(comma);     sentencePhonemes->push_back(space); }
    else if (punctuation == CLAUSE_COLON)     { sentencePhonemes->push_back(colon);     sentencePhonemes->push_back(space); }
    else if (punctuation == CLAUSE_SEMICOLON) { sentencePhonemes->push_back(semicolon); sentencePhonemes->push_back(space); }

    if ((terminator & CLAUSE_TYPE_SENTENCE) == CLAUSE_TYPE_SENTENCE) {
      sentencePhonemes = nullptr;
    }
  }
}

// ---------------------------------------------------------------------------
// synthesize: phonemeIds → PCM int16 via MNN VITS model
// ---------------------------------------------------------------------------
std::vector<int16_t> MNNPiperTTSImpl::synthesize(std::vector<PhonemeId> &phonemeIds)
{
  std::vector<int> input;
  input.reserve(phonemeIds.size());
  for (auto id : phonemeIds) {
    input.push_back(static_cast<int>(id));
  }

  // scales: [noise_scale, length_scale, noise_w]
  std::vector<float> scales{0.667f, 1.0f, 0.8f};
  auto audio_float = audio_generator_.Process(input, static_cast<int>(input.size()), scales);

  // Convert float32 [-1, 1] → int16
  std::vector<int16_t> audioBuffer;
  audioBuffer.reserve(audio_float.size());
  for (float s : audio_float) {
    float clamped = s < -1.0f ? -1.0f : (s > 1.0f ? 1.0f : s);
    audioBuffer.push_back(static_cast<int16_t>(clamped * 32767.0f));
  }
  return audioBuffer;
}

// ---------------------------------------------------------------------------
// Process: main entry point  text → (sample_rate, int16 PCM)
// ---------------------------------------------------------------------------
std::tuple<int, Audio> MNNPiperTTSImpl::Process(const std::string &text)
{
  std::lock_guard<std::mutex> lock(mtx_);

  auto t0 = clk::now();

  // Step 1: text → phoneme sequences (one per sentence)
  std::vector<std::vector<Phoneme>> phonemes;
  phonemize_eSpeak(text, phonemes);

  if (phonemes.empty()) {
    PLOG(WARNING, "No phonemes produced for text: " + text);
    return std::make_tuple(sample_rate_, Audio{});
  }

  std::wstring_convert<std::codecvt_utf8<char32_t>, char32_t> converter;

  std::vector<PhonemeId> phonemeIds;
  std::map<Phoneme, std::size_t> missingPhonemes;
  Audio audioBufferFinal;

  // Step 2: phonemes → ids → audio, per sentence
  for (auto &sentencePhonemes : phonemes) {
    if (sentencePhonemes.empty()) continue;

    // Debug: log phonemes as UTF-8
    std::string utf8_phonemes = converter.to_bytes(
        sentencePhonemes.data(),
        sentencePhonemes.data() + sentencePhonemes.size());
    PLOG(INFO, "TTS phonemes: " + utf8_phonemes);

    PhonemeIdConfig idConfig;
    idConfig.phonemeIdMap = std::make_shared<PhonemeIdMap>(phone_id_map_);

    phonemes_to_ids(sentencePhonemes, idConfig, phonemeIds, missingPhonemes);

    if (!phonemeIds.empty()) {
      auto audioBuffer = synthesize(phonemeIds);
      audioBufferFinal.insert(audioBufferFinal.end(),
                              audioBuffer.begin(), audioBuffer.end());
      phonemeIds.clear();
    }
  }

  // Log missing phonemes (useful for debugging Vietnamese tones)
  if (!missingPhonemes.empty()) {
    for (auto &[ph, count] : missingPhonemes) {
      std::string ph_utf8 = converter.to_bytes(&ph, &ph + 1);
      PLOG(WARNING, "Missing phoneme (x" + std::to_string(count) + "): " + ph_utf8);
    }
  }

  auto t1 = clk::now();
  auto duration_total  = std::chrono::duration_cast<ms>(t1 - t0);
  float timecost_ms    = static_cast<float>(duration_total.count());
  float audio_len_ms   = audioBufferFinal.empty() ? 1.0f :
      static_cast<float>(audioBufferFinal.size()) / static_cast<float>(sample_rate_) * 1000.0f;
  float rtf = timecost_ms / audio_len_ms;

  PLOG(INFO, "Piper TTS timecost: " + std::to_string(timecost_ms) +
       "ms, audio_duration: " + std::to_string(audio_len_ms) +
       "ms, rtf: " + std::to_string(rtf));

  return std::make_tuple(sample_rate_, audioBufferFinal);
}
