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
import android.content.Context
import android.os.SystemProperties
import android.os.UserHandle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import com.android.internal.kernel.AxKernelControl
import com.android.internal.kernel.AxKernelManager

/**
 * Applies the two performance profiles for BGMI.
 *
 * <p>CPU/GPU limits go through [AxKernelManager.setControlValue] so the persisted values are
 * the same ones the Kernel Manager screen reads and displays. The root-only WALT and touch
 * tunables are applied by a vendor init script that watches [SETTING_PROP]; both modes are
 * covered (performance and balanced) so toggling in either direction restores the other
 * profile's WALT/touch values.
 */
object PerformanceModeController {
    private const val TAG = "PerformanceMode"

    const val SETTING_MODE = "ax_perf_mode"

    /** Root-only WALT/touch applier trigger, watched by init.ax_perf.rc. */
    const val SETTING_PROP = "persist.sys.ax_perf_mode"

    const val MODE_BALANCED = 0
    const val MODE_PERFORMANCE = 1

    // CPU frequencies are in kHz, matching AxKernelControl.availableValues.
    private val PERFORMANCE_MIN_TARGETS = intArrayOf(1_100_000, 1_500_000, 1_900_000)
    private val BALANCED_MAX_TARGETS = intArrayOf(1_804_800, 2_323_200, 2_534_400)

    // GPU frequencies are converted to kHz by AxKernelConfigLoader.
    private const val PERFORMANCE_GPU_MIN_KHZ = 422_000
    private const val BALANCED_GPU_MAX_KHZ = 680_000

    fun getMode(context: Context): Int =
            Settings.Secure.getIntForUser(
                    context.contentResolver,
                    SETTING_MODE,
                    MODE_BALANCED,
                    UserHandle.USER_CURRENT,
            )

    fun isPerformance(context: Context): Boolean = getMode(context) == MODE_PERFORMANCE

    /** Applies the BGMI profile. */
    fun enablePerformance(context: Context): Boolean {
        if (!apply(context, performance = true)) {
            return false
        }
        Settings.Secure.putIntForUser(
                context.contentResolver,
                SETTING_MODE,
                MODE_PERFORMANCE,
                UserHandle.USER_CURRENT,
        )
        requestWaltTouchProfile(performance = true)
        requestTileUpdate(context)
        return true
    }

    /** Restores the balanced profile. */
    fun disablePerformance(context: Context) {
        apply(context, performance = false)
        Settings.Secure.putIntForUser(
                context.contentResolver,
                SETTING_MODE,
                MODE_BALANCED,
                UserHandle.USER_CURRENT,
        )
        requestWaltTouchProfile(performance = false)
        requestTileUpdate(context)
    }

    fun requestTileUpdate(context: Context) {
        try {
            TileService.requestListeningState(
                    context,
                    ComponentName(context, PerformanceModeTileService::class.java),
            )
        } catch (_: Exception) {
            // SystemUI may not be ready during boot.
        }
    }

    /**
     * Triggers the root WALT/touch applier for the requested mode. CPU/GPU limits above are
     * already applied by the kernel manager; this covers the tunables the app cannot write.
     */
    private fun requestWaltTouchProfile(performance: Boolean) {
        try {
            SystemProperties.set(
                    SETTING_PROP,
                    if (performance) "1" else "0",
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to set WALT/touch profile property", t)
        }
    }

    private fun apply(context: Context, performance: Boolean): Boolean {
        return try {
            val controls = AxKernelManager.getControls()
            val clusters =
                    controls
                            .filter {
                                it.type == AxKernelControl.TYPE_CPU_MIN_FREQ ||
                                        it.type == AxKernelControl.TYPE_CPU_MAX_FREQ
                            }
                            .groupBy { it.group }
                            .values
                            .mapNotNull { group ->
                                val min =
                                        group.firstOrNull {
                                            it.type == AxKernelControl.TYPE_CPU_MIN_FREQ
                                        }
                                val max =
                                        group.firstOrNull {
                                            it.type == AxKernelControl.TYPE_CPU_MAX_FREQ
                                        }
                                if (min == null || max == null) {
                                    null
                                } else {
                                    CpuCluster(
                                            min = min,
                                            max = max,
                                            maxAvailable =
                                                    max.availableValues.maxOrNull()
                                                            ?: max.defaultValue,
                                    )
                                }
                            }
                            .sortedBy { it.maxAvailable }

            if (clusters.isEmpty()) {
                Log.w(TAG, "No CPU frequency controls available")
                return false
            }

            clusters.forEachIndexed { index, cluster ->
                if (performance) {
                    val maxValue = cluster.max.availableValues.maxOrNull()
                    if (maxValue != null) {
                        setControl(cluster.max, maxValue)
                    }
                    val target =
                            PERFORMANCE_MIN_TARGETS.getOrElse(index) {
                                PERFORMANCE_MIN_TARGETS.last()
                            }
                    setControl(
                            cluster.min,
                            nearestAtLeast(cluster.min.availableValues, target),
                    )
                } else {
                    val target =
                            BALANCED_MAX_TARGETS.getOrElse(index) {
                                BALANCED_MAX_TARGETS.last()
                            }
                    setControl(
                            cluster.max,
                            nearestAtMost(cluster.max.availableValues, target),
                    )
                    setControl(cluster.min, cluster.min.defaultValue)
                }
            }

            val gpuMin =
                    controls.firstOrNull {
                        it.type == AxKernelControl.TYPE_GPU_MIN_FREQ
                    }
            val gpuMax =
                    controls.firstOrNull {
                        it.type == AxKernelControl.TYPE_GPU_MAX_FREQ
                    }
            if (gpuMin != null && gpuMax != null) {
                if (performance) {
                    setControl(
                            gpuMax,
                            gpuMax.availableValues.maxOrNull() ?: gpuMax.defaultValue,
                    )
                    setControl(
                            gpuMin,
                            nearestAtLeast(
                                    gpuMin.availableValues,
                                    PERFORMANCE_GPU_MIN_KHZ,
                            ),
                    )
                } else {
                    setControl(
                            gpuMax,
                            nearestAtMost(gpuMax.availableValues, BALANCED_GPU_MAX_KHZ),
                    )
                    setControl(gpuMin, gpuMin.defaultValue)
                }
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to apply performance profile", t)
            false
        }
    }

    private fun setControl(control: AxKernelControl, value: Int) {
        if (value > 0) {
            AxKernelManager.setControlValue(control.id, value)
        }
    }

    private fun nearestAtLeast(values: IntArray, target: Int): Int {
        if (values.isEmpty()) return target
        val sorted = values.sorted()
        return sorted.firstOrNull { it >= target } ?: sorted.last()
    }

    private fun nearestAtMost(values: IntArray, target: Int): Int {
        if (values.isEmpty()) return target
        val sorted = values.sorted()
        return sorted.lastOrNull { it <= target } ?: sorted.first()
    }

    private data class CpuCluster(
            val min: AxKernelControl,
            val max: AxKernelControl,
            val maxAvailable: Int,
    )
}
