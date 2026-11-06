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

package com.android.axion.axionparts

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.android.axion.axionparts.network.PrivateDnsTileService
import com.android.axion.axionparts.ui.DeviceManagerScreen
import com.android.axion.axionparts.ui.theme.AxionPartsTheme
import com.google.android.material.color.DynamicColors

class DeviceManagerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Long-pressing the Private DNS tile must not open the kernel manager.
        val qsComponent =
            intent?.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        if (
            intent?.action == ACTION_QS_TILE_PREFERENCES &&
                qsComponent?.className == PrivateDnsTileService::class.java.name
        ) {
            startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
            return
        }

        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()

        val initialScreen = when (intent?.action) {
            ACTION_APP_OPTIMIZATION -> "app_optimization"
            ACTION_BACKGROUND_MANAGER -> "background_manager"
            ACTION_KERNEL_MANAGER -> "kernel_manager"
            ACTION_RAM_PLUS -> "ram_plus"
            ACTION_QS_TILE_PREFERENCES -> "kernel_manager"
            else -> null
        }

        setContent {
            AxionPartsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    DeviceManagerScreen(initialDetailScreen = initialScreen)
                }
            }
        }
    }

    companion object {
        const val ACTION_PERFORMANCE = "com.android.axion.axionparts.action.PERFORMANCE"
        const val ACTION_BACKGROUND_MANAGER =
            "com.android.axion.axionparts.action.BACKGROUND_MANAGER"
        const val ACTION_KERNEL_MANAGER = "com.android.axion.axionparts.action.KERNEL_MANAGER"
        const val ACTION_RAM_PLUS = "com.android.axion.axionparts.action.RAM_PLUS"
        const val ACTION_APP_OPTIMIZATION =
                "com.android.axion.axionparts.ACTION_APP_OPTIMIZATION"
        const val ACTION_QS_TILE_PREFERENCES =
                "android.service.quicksettings.action.QS_TILE_PREFERENCES"
    }
}
