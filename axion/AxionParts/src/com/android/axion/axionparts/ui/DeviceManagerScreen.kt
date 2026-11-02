/*
 * Copyright (C) 2025-2026 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.axion.axionparts.ui

import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.android.axion.axionparts.R
import com.android.axion.axionparts.ui.screens.AppFilterType
import com.android.axion.axionparts.ui.screens.AppOptimizationScreen
import com.android.axion.axionparts.ui.screens.AppPickerScreen
import com.android.axion.axionparts.ui.screens.BackgroundManagerScreen
import com.android.axion.axionparts.ui.screens.KernelManagerScreen
import com.android.axion.axionparts.ui.screens.PerformanceScreen
import com.android.axion.axionparts.ui.screens.RamPlusScreen

@Composable
fun DeviceManagerScreen(initialDetailScreen: String? = null) {
    val activity = LocalContext.current as? Activity
    val context = LocalContext.current

    var detailScreen by rememberSaveable { mutableStateOf(initialDetailScreen) }
    var appPickerTitleRes by rememberSaveable { mutableStateOf(R.string.select_apps) }
    var appPickerSelectedApps by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var appPickerSettingKey by rememberSaveable {
        mutableStateOf("axion_perf_package_freezer")
    }
    var appPickerMaxSelection by rememberSaveable { mutableStateOf<Int?>(null) }
    var appPickerMaxSelectionMessageRes by rememberSaveable { mutableStateOf<Int?>(null) }
    var appPickerFilterTypeName by rememberSaveable {
        mutableStateOf(AppFilterType.LAUNCHABLE_USER_ONLY.name)
    }
    var appPickerExcludedPackages by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }

    fun closeDetail() {
        if (detailScreen == "app_picker") {
            detailScreen = "background_manager"
        } else if (detailScreen != null) {
            detailScreen = null
        } else {
            activity?.finish()
        }
    }

    fun navigateToAppPicker(
        titleRes: Int,
        selectedApps: Set<String>,
        settingKey: String,
        maxSelection: Int? = null,
        maxSelectionMessageRes: Int? = null,
        filterType: AppFilterType = AppFilterType.LAUNCHABLE_USER_ONLY,
        excludedPackages: Set<String> = emptySet(),
    ) {
        appPickerTitleRes = titleRes
        appPickerSelectedApps = selectedApps
        appPickerSettingKey = settingKey
        appPickerMaxSelection = maxSelection
        appPickerMaxSelectionMessageRes = maxSelectionMessageRes
        appPickerFilterTypeName = filterType.name
        appPickerExcludedPackages = excludedPackages
        detailScreen = "app_picker"
    }

    if (detailScreen != null) {
        BackHandler { closeDetail() }
    }

    when (detailScreen) {
        null ->
            PerformanceScreen(
                onBackClick = { activity?.finish() },
                onNavigateToDetail = { detailScreen = it },
            )
        "app_optimization" -> AppOptimizationScreen(onBackClick = ::closeDetail)
        "background_manager" ->
            BackgroundManagerScreen(
                onBackClick = ::closeDetail,
                onNavigateToAppPicker = ::navigateToAppPicker,
            )
        "kernel_manager" -> KernelManagerScreen(onBackClick = ::closeDetail)
        "ram_plus" -> RamPlusScreen(onBackClick = ::closeDetail)
        "app_picker" -> {
            val maxSelectionMessage =
                appPickerMaxSelectionMessageRes?.let { resId ->
                    appPickerMaxSelection?.let { activity?.getString(resId, it) }
                        ?: activity?.getString(resId)
                }
            val filterType =
                runCatching { AppFilterType.valueOf(appPickerFilterTypeName) }
                    .getOrDefault(AppFilterType.LAUNCHABLE_USER_ONLY)
            AppPickerScreen(
                title = context.getString(appPickerTitleRes),
                selectedApps = appPickerSelectedApps,
                onBackClick = ::closeDetail,
                onAppsSelected = { apps ->
                    Settings.Secure.putString(
                        context.contentResolver,
                        appPickerSettingKey,
                        apps.joinToString(","),
                    )
                    detailScreen = "background_manager"
                },
                maxSelection = appPickerMaxSelection,
                maxSelectionMessage = maxSelectionMessage,
                filterType = filterType,
                excludedPackages = appPickerExcludedPackages,
            )
        }
        else -> detailScreen = null
    }
}
