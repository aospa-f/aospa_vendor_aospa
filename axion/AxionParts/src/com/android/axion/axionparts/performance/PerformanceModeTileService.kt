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

package com.android.axion.axionparts.performance

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.android.axion.axionparts.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Two-mode BGMI QS tile.
 *
 * <p>CPU/GPU values are applied only through [PerformanceModeController] and the Axion kernel
 * manager, so the Kernel Manager screen shows the same values.
 */
class PerformanceModeTileService : TileService() {
    companion object {
        private val executor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())
    }

    private val applyInFlight = AtomicBoolean(false)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Drop clicks while a profile is still being applied; the single-thread
        // executor would otherwise serialize them and re-apply the same profile.
        if (applyInFlight.getAndSet(true)) {
            return
        }

        val enable = !PerformanceModeController.isPerformance(this)
        // Optimistic flip: reflect the requested state immediately instead of
        // waiting for every kernel-manager binder call to complete.
        qsTile?.let { tile ->
            tile.state = if (enable) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle =
                    getString(
                            if (enable) {
                                R.string.performance_mode_performance
                            } else {
                                R.string.performance_mode_balanced
                            }
                    )
            tile.updateTile()
        }

        executor.execute {
            val applied =
                    if (enable) {
                        PerformanceModeController.enablePerformance(this)
                    } else {
                        PerformanceModeController.disablePerformance(this)
                        true
                    }

            mainHandler.post {
                applyInFlight.set(false)
                if (!applied) {
                    Toast.makeText(
                                    this,
                                    R.string.performance_mode_failed,
                                    Toast.LENGTH_SHORT,
                            )
                            .show()
                }
                // Sync with the real state; reverts the optimistic flip on failure.
                updateTile()
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val performance = PerformanceModeController.isPerformance(this)
        tile.state = if (performance) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.performance_mode)
        tile.subtitle =
                getString(
                        if (performance) {
                            R.string.performance_mode_performance
                        } else {
                            R.string.performance_mode_balanced
                        }
                )
        tile.icon = Icon.createWithResource(this, R.drawable.ic_performance_mode)
        tile.updateTile()
    }
}
