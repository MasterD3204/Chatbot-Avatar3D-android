// Created by ruoyi.sjd on 2025/3/26.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.settings

import android.os.Bundle
import android.os.Environment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.utils.AppUtils
import java.io.File

class MainSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_settings_prefs, rootKey)

        val checkUpdatePref = findPreference<Preference>("check_update")
        checkUpdatePref?.apply {
            summary = getString(
                R.string.current_version,
                AppUtils.getAppVersionName(requireContext())
            )
        }

        val llmPromptPreference = findPreference<EditTextPreference>("llm_prompt")
        llmPromptPreference?.apply {
            text = MainSettings.getLlmPrompt(requireContext())
            summary = MainSettings.getLlmPrompt(requireContext())
            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue as String
                true
            }
        }

        setupLlmModelPicker()
    }

    /**
     * Scan /sdcard/Download/ for all .litertlm files and populate the ListPreference.
     * If none found, show a disabled entry explaining the situation.
     */
    private fun setupLlmModelPicker() {
        val pref = findPreference<ListPreference>("llm_model_name") ?: return
        val models = scanLitertlmModels()

        if (models.isEmpty()) {
            pref.entries = arrayOf(getString(R.string.llm_model_no_models))
            pref.entryValues = arrayOf("")
            pref.isEnabled = false
            pref.summary = getString(R.string.llm_model_no_models)
            return
        }

        val names = models.map { it.name }.toTypedArray()
        pref.entries = names
        pref.entryValues = names
        pref.isEnabled = true

        // Show current selection in summary
        val current = MainSettings.getLlmModelName(requireContext())
            ?: names.first()

        // If saved value is no longer in the list, reset to newest
        val effective = if (names.contains(current)) current else names.first()
        pref.value = effective
        pref.summary = getString(R.string.llm_model_summary, effective)

        pref.setOnPreferenceChangeListener { _, newValue ->
            val selected = newValue as String
            MainSettings.setLlmModelName(requireContext(), selected)
            pref.summary = getString(R.string.llm_model_summary, selected)
            true
        }
    }

    /** Returns .litertlm files in Downloads sorted newest-first. */
    private fun scanLitertlmModels(): List<File> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        return try {
            downloadDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
