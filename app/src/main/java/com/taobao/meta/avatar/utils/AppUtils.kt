// Created by ruoyi.sjd on 2025/3/27.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.utils;

import android.content.Context
import com.alibaba.mls.api.ApplicationProvider

public object AppUtils {

    fun getAppVersionName(context: Context): String {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName!!
    }

    fun isChinese(): Boolean {
        val locale = ApplicationProvider.get().resources.configuration.locales[0]
        return locale.language == "zh" && locale.country == "CN"
    }

    /** Returns true if device locale is Vietnamese (vi). */
    fun isVietnamese(): Boolean {
        val locale = ApplicationProvider.get().resources.configuration.locales[0]
        return locale.language == "vi"
    }
}

