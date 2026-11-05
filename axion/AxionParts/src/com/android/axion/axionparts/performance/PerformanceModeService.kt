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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.android.axion.axionparts.R

/**
 * Keeps the BGMI performance profile safe while it is active.
 *
 * <p>The CPU/GPU profile itself is applied by [PerformanceModeController] through the Axion
 * kernel manager. This service only monitors battery level and temperature, keeps the
 * foreground notification alive, and backs up the 30-minute AlarmManager timeout.
 */
class PerformanceModeService : Service() {
    companion object {
        private const val CHANNEL_ID = "performance_mode"
        private const val NOTIFICATION_ID = 0x50455246

        fun start(context: Context) {
            context.startForegroundService(
                    Intent(context, PerformanceModeService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PerformanceModeService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false

    private val timeoutRunnable = Runnable { revert() }

    private val batteryReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Intent.ACTION_BATTERY_CHANGED) {
                        return
                    }
                    if (!PerformanceModeController.isPerformance(context)) {
                        return
                    }

                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    val charging =
                            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                    status == BatteryManager.BATTERY_STATUS_FULL
                    val percent = if (scale > 0) level * 100 / scale else level

                    if (!charging && percent in 0..20) {
                        revert()
                        return
                    }
                    if (temperature >= 450) {
                        revert()
                    }
                }
            }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!PerformanceModeController.isPerformance(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!receiverRegistered) {
            registerReceiver(
                    batteryReceiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }

        handler.removeCallbacks(timeoutRunnable)
        val remaining = PerformanceModeController.remainingAutoRevertMs(this)
        if (remaining <= 0L) {
            revert()
        } else {
            handler.postDelayed(timeoutRunnable, remaining)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun revert() {
        PerformanceModeController.disablePerformance(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.performance_mode_channel),
                        NotificationManager.IMPORTANCE_LOW,
                )
        channel.description = getString(R.string.performance_mode_channel_desc)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
            Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_performance_mode)
                    .setContentTitle(getString(R.string.performance_mode))
                    .setContentText(getString(R.string.performance_mode_active))
                    .setSubText(getString(R.string.performance_mode_auto_revert))
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
}
