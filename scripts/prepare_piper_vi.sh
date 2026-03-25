#!/usr/bin/env bash
# =============================================================================
# prepare_piper_vi.sh
# Chuẩn bị model Piper Vietnamese cho MnnTaoAvatar
#
# Yêu cầu:
#   - mnnconvert  (build từ MNN source hoặc pip install MNN)
#   - adb          (Android Debug Bridge)
#   - espeak-ng-data cho tiếng Việt (từ train_piper/espeak-ng)
#
# Sử dụng:
#   chmod +x scripts/prepare_piper_vi.sh
#   ./scripts/prepare_piper_vi.sh
# =============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ─── Paths ────────────────────────────────────────────────────────────────────
ONNX_MODEL="${HOME}/Downloads/huongly.onnx"
ONNX_CONFIG="${HOME}/Downloads/huongly.onnx.json"
ESPEAK_DATA_SRC="${HOME}/Downloads/gemini_live/train_piper/espeak-ng/espeak-ng-data"

# Output directory on device (matches MHConfig.TTS_MODEL_DIR_VI)
DEVICE_BASE="/data/data/com.taobao.meta.avatar/files/.mnnmodels/modelscope"
DEVICE_TTS_DIR="${DEVICE_BASE}/piper-vi-tts"

# Local staging directory
STAGING_DIR="${PROJECT_DIR}/staging/piper-vi-tts"

# MNN converter — adjust path if needed
MNNCONVERT="${HOME}/MNN/build/MNNConvert"
if [ ! -f "$MNNCONVERT" ]; then
    MNNCONVERT="$(which mnnconvert 2>/dev/null || echo '')"
fi

echo "============================================="
echo "  Piper Vietnamese TTS — Model Preparation  "
echo "============================================="

# ─── Step 1: Check inputs ─────────────────────────────────────────────────────
echo ""
echo "[1/5] Checking input files..."

if [ ! -f "$ONNX_MODEL" ]; then
    echo "ERROR: ONNX model not found at: $ONNX_MODEL"
    echo "  Download from: https://huggingface.co/lddai/Piper-TTS-Vietnamese"
    echo "  or: https://huggingface.co/rhasspy/piper-voices/tree/main/vi/vi_VN"
    exit 1
fi

if [ ! -d "$ESPEAK_DATA_SRC" ]; then
    echo "ERROR: espeak-ng-data not found at: $ESPEAK_DATA_SRC"
    exit 1
fi

echo "  ✓ ONNX model: $ONNX_MODEL ($(du -sh "$ONNX_MODEL" | cut -f1))"
echo "  ✓ espeak-ng-data: $ESPEAK_DATA_SRC"

# ─── Step 2: Convert ONNX → MNN ──────────────────────────────────────────────
echo ""
echo "[2/5] Converting ONNX → MNN format..."

mkdir -p "$STAGING_DIR"
MNN_MODEL="${STAGING_DIR}/huongly.mnn"

if [ -f "$MNN_MODEL" ]; then
    echo "  ✓ MNN model already exists, skipping conversion"
else
    if [ -z "$MNNCONVERT" ] || [ ! -f "$MNNCONVERT" ]; then
        echo "  ERROR: mnnconvert not found."
        echo "  Build MNN first:"
        echo "    cd ~/MNN && mkdir build && cd build"
        echo "    cmake .. -DMNN_BUILD_CONVERTER=ON && make MNNConvert -j$(nproc)"
        echo "  Then re-run this script."
        exit 1
    fi

    echo "  Converting with: $MNNCONVERT"
    "$MNNCONVERT" \
        --framework ONNX \
        --modelFile "$ONNX_MODEL" \
        --MNNModel "$MNN_MODEL" \
        --bizCode MNN

    echo "  ✓ Converted: $MNN_MODEL ($(du -sh "$MNN_MODEL" | cut -f1))"
fi

# ─── Step 3: Create config.json ──────────────────────────────────────────────
echo ""
echo "[3/5] Creating MNN-compatible config.json..."

CONFIG_FILE="${STAGING_DIR}/config.json"
cat > "$CONFIG_FILE" << 'EOF'
{
  "model_type": "piper",
  "model_path": "huongly.mnn",
  "asset_folder": "espeak-ng-data",
  "cache_folder": "cache",
  "sample_rate": 22050,
  "audio": {
    "sample_rate": 22050
  },
  "espeak": {
    "voice": "vi"
  },
  "inference": {
    "noise_scale": 0.667,
    "length_scale": 1.0,
    "noise_w": 0.8
  },
  "phoneme_type": "espeak",
  "num_symbols": 256,
  "num_speakers": 1,
  "phoneme_id_map": {
    "_": [0], "^": [1], "$": [2], " ": [3], "!": [4], "'": [5],
    "(": [6], ")": [7], ",": [8], "-": [9], ".": [10], ":": [11],
    ";": [12], "?": [13], "a": [14], "b": [15], "c": [16], "d": [17],
    "e": [18], "f": [19], "h": [20], "i": [21], "j": [22], "k": [23],
    "l": [24], "m": [25], "n": [26], "o": [27], "p": [28], "q": [29],
    "r": [30], "s": [31], "t": [32], "u": [33], "v": [34], "w": [35],
    "x": [36], "y": [37], "z": [38], "æ": [39], "ç": [40], "ð": [41],
    "ø": [42], "ħ": [43], "ŋ": [44], "œ": [45], "ǀ": [46], "ǁ": [47],
    "ǂ": [48], "ǃ": [49], "ɐ": [50], "ɑ": [51], "ɒ": [52], "ɓ": [53],
    "ɔ": [54], "ɕ": [55], "ɖ": [56], "ɗ": [57], "ɘ": [58], "ə": [59],
    "ɚ": [60], "ɛ": [61], "ɜ": [62], "ɞ": [63], "ɟ": [64], "ɠ": [65],
    "ɡ": [66], "ɢ": [67], "ɣ": [68], "ɤ": [69], "ɥ": [70], "ɦ": [71],
    "ɧ": [72], "ɨ": [73], "ɪ": [74], "ɫ": [75], "ɬ": [76], "ɭ": [77],
    "ɮ": [78], "ɯ": [79], "ɰ": [80], "ɱ": [81], "ɲ": [82], "ɳ": [83],
    "ɴ": [84], "ɵ": [85], "ɶ": [86], "ɸ": [87], "ɹ": [88], "ɺ": [89],
    "ɻ": [90], "ɽ": [91], "ɾ": [92], "ʀ": [93], "ʁ": [94], "ʂ": [95],
    "ʃ": [96], "ʄ": [97], "ʈ": [98], "ʉ": [99], "ʊ": [100], "ʋ": [101],
    "ʌ": [102], "ʍ": [103], "ʎ": [104], "ʏ": [105], "ʐ": [106], "ʑ": [107],
    "ʒ": [108], "ʔ": [109], "ʕ": [110], "ʘ": [111], "ʙ": [112], "ʛ": [113],
    "ʜ": [114], "ʝ": [115], "ʟ": [116], "ʡ": [117], "ʢ": [118], "ʲ": [119],
    "ˈ": [120], "ˌ": [121], "ː": [122], "ˑ": [123], "˞": [124], "β": [125],
    "θ": [126], "χ": [127], "ᵻ": [128], "ⱱ": [129],
    "0": [130], "1": [131], "2": [132], "3": [133], "4": [134],
    "5": [135], "6": [136], "7": [137], "8": [138], "9": [139],
    "\u0327": [140], "\u0303": [141], "\u032a": [142], "\u032f": [143],
    "\u0329": [144], "ʰ": [145], "ˤ": [146], "ε": [147],
    "↓": [148], "#": [149], "\"": [150], "↑": [151],
    "\u033a": [152], "\u033b": [153]
  }
}
EOF

echo "  ✓ Created: $CONFIG_FILE"

# ─── Step 4: Copy espeak-ng-data (vi voice only, to save space) ──────────────
echo ""
echo "[4/5] Preparing espeak-ng-data (Vietnamese)..."

ESPEAK_STAGING="${STAGING_DIR}/espeak-ng-data"
mkdir -p "$ESPEAK_STAGING"

# Copy all espeak-ng-data (vi language data is embedded in the main data files)
# We need: phondata, phontab, intonations, lang/vi, voices/vi*
cp -rn "$ESPEAK_DATA_SRC"/* "$ESPEAK_STAGING"/ 2>/dev/null || true
echo "  ✓ espeak-ng-data copied ($(du -sh "$ESPEAK_STAGING" | cut -f1))"

# ─── Step 5: Push to device via adb ──────────────────────────────────────────
echo ""
echo "[5/5] Pushing to Android device via adb..."

if ! command -v adb &> /dev/null; then
    echo "  WARNING: adb not found. Skipping device push."
    echo ""
    echo "  Manual push commands:"
    echo "    adb shell mkdir -p '$DEVICE_TTS_DIR'"
    echo "    adb push '$STAGING_DIR/config.json' '$DEVICE_TTS_DIR/'"
    echo "    adb push '$STAGING_DIR/huongly.mnn' '$DEVICE_TTS_DIR/'"
    echo "    adb push '$STAGING_DIR/espeak-ng-data' '$DEVICE_TTS_DIR/'"
    echo ""
    echo "  Staging directory: $STAGING_DIR"
    exit 0
fi

ADB_DEVICE=$(adb devices | grep -v "List" | grep "device$" | head -1 | cut -f1)
if [ -z "$ADB_DEVICE" ]; then
    echo "  WARNING: No Android device connected."
    echo "  Files prepared at: $STAGING_DIR"
    exit 0
fi

echo "  Device: $ADB_DEVICE"

# Create remote directory structure
adb -s "$ADB_DEVICE" shell "run-as com.taobao.meta.avatar mkdir -p '$DEVICE_TTS_DIR/espeak-ng-data'"

# Push files
echo "  Pushing config.json..."
adb -s "$ADB_DEVICE" push "$CONFIG_FILE" "/sdcard/piper_vi_config.json"
adb -s "$ADB_DEVICE" shell "run-as com.taobao.meta.avatar cp /sdcard/piper_vi_config.json '$DEVICE_TTS_DIR/config.json'"

echo "  Pushing huongly.mnn (~$(du -sh "$MNN_MODEL" | cut -f1))..."
adb -s "$ADB_DEVICE" push "$MNN_MODEL" "/sdcard/huongly.mnn"
adb -s "$ADB_DEVICE" shell "run-as com.taobao.meta.avatar cp /sdcard/huongly.mnn '$DEVICE_TTS_DIR/huongly.mnn'"

echo "  Pushing espeak-ng-data (~$(du -sh "$ESPEAK_STAGING" | cut -f1))..."
adb -s "$ADB_DEVICE" push "$ESPEAK_STAGING" "/sdcard/espeak-ng-data-vi"
adb -s "$ADB_DEVICE" shell "run-as com.taobao.meta.avatar cp -r /sdcard/espeak-ng-data-vi '$DEVICE_TTS_DIR/espeak-ng-data'"

echo ""
echo "============================================="
echo "  ✓ All done!"
echo ""
echo "  Device path: $DEVICE_TTS_DIR"
echo "  Contents:"
adb -s "$ADB_DEVICE" shell "run-as com.taobao.meta.avatar ls -la '$DEVICE_TTS_DIR/'"
echo "============================================="
