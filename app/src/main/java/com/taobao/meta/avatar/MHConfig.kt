// Created by ruoyi.sjd on 2025/3/12.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.taobao.meta.avatar

import com.taobao.meta.avatar.utils.DeviceUtils


object MHConfig {
    private var baseDir: String = ""
    var BASE_DIR
        get() = baseDir
        set(value) {
            baseDir = value
        }
    val NNR_MODEL_DIR
        get() = "${BASE_DIR}/TaoAvatar-NNR-MNN/"

    val TTS_MODEL_DIR
        get() = "${BASE_DIR}/bert-vits2-MNN/"

    val TTS_MODEL_DIR_EN
        get() = "${BASE_DIR}/supertonic-tts-mnn/"

    /** Piper Vietnamese TTS model directory.
     *  Expected contents:
     *    config.json          — Piper config (model_type="piper", audio.sample_rate, espeak.voice, phoneme_id_map)
     *    huongly.mnn          — MNN-converted VITS model (converted from huongly.onnx)
     *    espeak-ng-data/      — espeak-ng language data directory (vi voice data required)
     */
    val TTS_MODEL_DIR_VI
        get() = "${BASE_DIR}/piper-vi-tts/"

    val A2BS_MODEL_DIR
        get() = "${BASE_DIR}/UniTalker-MNN/"

    val LLM_MODEL_DIR
        get() = "${BASE_DIR}/Qwen2.5-1.5B-Instruct-MNN"

    val ASR_MODEL_DIR
        get() = if (DeviceUtils.isChinese) {
            "${BASE_DIR}/sherpa-mnn-streaming-zipformer-bilingual-zh-en-2023-02-20/"
        } else {
            "${BASE_DIR}/sherpa-mnn-streaming-zipformer-en-2023-02-21"
        }

    const val DEBUG_MODE = false

    const val DEBUG_SCREEN_SHOT = false

    const val DEBUG_LOG_VERBOSE = false

    object DebugConfig  {
        val DebugWriteBlendShape = false
    }
}