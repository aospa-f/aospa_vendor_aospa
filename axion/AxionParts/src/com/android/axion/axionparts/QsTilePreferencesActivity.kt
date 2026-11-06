/*
 * Copyright (C) 2026 The AOSPA Project
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

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.android.axion.axionparts.network.PrivateDnsTileService

/**
 * Routes a Quick Settings tile long-press to the correct settings screen.
 *
 * <p>All AxionParts tiles share this activity because the platform resolves one
 * QS_TILE_PREFERENCES activity per package. The tile component is supplied in
 * [Intent.EXTRA_COMPONENT_NAME].
 */
class QsTilePreferencesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component =
            intent?.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        val target =
            if (component?.className == PrivateDnsTileService::class.java.name) {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            } else {
                Intent(this, DeviceManagerActivity::class.java)
                    .setAction(DeviceManagerActivity.ACTION_KERNEL_MANAGER)
            }
        startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
