// Created by ruoyi.sjd on 2025/3/12.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.taobao.meta.avatar


object MHConfig {
    private var baseDir: String = ""
    var BASE_DIR
        get() = baseDir
        set(value) {
            baseDir = value
        }

    val NNR_MODEL_DIR
        get() = "${BASE_DIR}/TaoAvatar-NNR-MNN/"

    val A2BS_MODEL_DIR
        get() = "${BASE_DIR}/UniTalker-MNN/"

    /**
     * Default LLM model filename (.litertlm) when no model is selected.
     * The actual file will be searched in /sdcard/Download/ and other common paths.
     */
    const val LLM_MODEL_FILENAME_DEFAULT = "model.litertlm"

    const val DEBUG_MODE = false

    const val DEBUG_SCREEN_SHOT = false

    const val DEBUG_LOG_VERBOSE = false

    object DebugConfig {
        val DebugWriteBlendShape = false
    }
}
