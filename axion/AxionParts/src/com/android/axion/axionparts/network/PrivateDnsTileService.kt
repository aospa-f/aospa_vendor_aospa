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

package com.android.axion.axionparts.network

import android.content.ComponentName
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

import com.android.axion.axionparts.R

/**
 * Two-state Private DNS quick settings tile.
 *
 * <p>Off -> restores the last non-off mode (automatic or the last hostname), or enables automatic
 * Private DNS if no previous state exists. On -> turns Private DNS off and remembers the mode.
 */
class PrivateDnsTileService : TileService() {
    companion object {
        private const val MODE_OFF = "off"
        private const val MODE_AUTO = "opportunistic"
        private const val MODE_HOSTNAME = "hostname"
        private const val PREFS = "private_dns_tile"
        private const val PREF_MODE = "previous_mode"
        private const val PREF_SPECIFIER = "previous_specifier"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null

    override fun onStartListening() {
        super.onStartListening()
        if (observer == null) {
            observer =
                object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        updateTile()
                    }
                }
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.PRIVATE_DNS_MODE),
                false,
                observer!!,
            )
        }
        updateTile()
    }

    override fun onStopListening() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        super.onStopListening()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val resolver = contentResolver
        val mode =
            Settings.Global.getString(resolver, Settings.Global.PRIVATE_DNS_MODE) ?: MODE_OFF
        if (mode == MODE_OFF) {
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val previousMode = prefs.getString(PREF_MODE, null)
            val previousSpecifier = prefs.getString(PREF_SPECIFIER, null)
            if (previousMode == MODE_HOSTNAME && !previousSpecifier.isNullOrBlank()) {
                Settings.Global.putString(
                    resolver,
                    Settings.Global.PRIVATE_DNS_SPECIFIER,
                    previousSpecifier,
                )
                Settings.Global.putString(
                    resolver,
                    Settings.Global.PRIVATE_DNS_MODE,
                    MODE_HOSTNAME,
                )
            } else {
                Settings.Global.putString(
                    resolver,
                    Settings.Global.PRIVATE_DNS_MODE,
                    MODE_AUTO,
                )
            }
        } else {
            val specifier =
                Settings.Global.getString(
                    resolver,
                    Settings.Global.PRIVATE_DNS_SPECIFIER,
                )
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_MODE, mode)
                .putString(PREF_SPECIFIER, specifier)
                .apply()
            Settings.Global.putString(
                resolver,
                Settings.Global.PRIVATE_DNS_MODE,
                MODE_OFF,
            )
        }
        updateTile()
        TileService.requestListeningState(
            this,
            ComponentName(this, PrivateDnsTileService::class.java),
        )
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val mode =
            Settings.Global.getString(contentResolver, Settings.Global.PRIVATE_DNS_MODE)
                ?: MODE_OFF
        val specifier =
            Settings.Global.getString(
                contentResolver,
                Settings.Global.PRIVATE_DNS_SPECIFIER,
            )
        val active = mode != MODE_OFF
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.private_dns_tile_label)
        tile.subtitle =
            when (mode) {
                MODE_AUTO -> getString(R.string.private_dns_auto)
                MODE_HOSTNAME ->
                    specifier?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.private_dns_on)
                else -> getString(R.string.private_dns_off)
            }
        tile.icon =
            Icon.createWithResource(
                this,
                if (active) R.drawable.ic_private_dns_on else R.drawable.ic_private_dns_off,
            )
        tile.updateTile()
    }
}
