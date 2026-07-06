package com.konodiary.app.audio.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.konodiary.app.KonoApp
import com.konodiary.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps song-segment analysis alive while the queue is non-empty. Hour-long
 * decodes must survive the screen turning off, so this promotes the analysis
 * work (which still runs in the app process via [DefaultAnalysisController]) to
 * a foreground service holding a partial wake lock, and mirrors queue progress
 * into an ongoing notification.
 *
 * The service owns no analysis logic: it observes the controller's [progress]
 * map and stops itself the moment it drains. Started by the controller when the
 * map transitions empty -> non-empty; on failure to start (background FGS start
 * restrictions) analysis still proceeds in-process, just unprotected.
 */
class AnalysisService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    // Cache the display name so we only hit the repository when the leading
    // recording id changes, not on every 500ms notification refresh.
    private var cachedId: Long = -1L
    private var cachedName: String = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val pm = getSystemService<PowerManager>()
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
            // Timeout is a safety net; the service releases explicitly in onDestroy.
            acquire(6 * 60 * 60 * 1000L)
        }

        observeProgress()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must promote to foreground immediately (well under the ~5s window).
        // This THROWS when the app is not in a foreground state (e.g. the
        // cold-start enqueue races ahead of the activity reaching TOP) — never
        // crash for that: analysis keeps running in-process, and MainActivity
        // re-starts this service once the UI is visibly foregrounded.
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification("", 0), foregroundServiceType())
        } catch (e: Exception) {
            android.util.Log.w(TAG, "startForeground denied/failed; analysis continues unprotected", e)
            stopSelf()
            return START_NOT_STICKY
        }
        // The in-memory queue is rebuilt by enqueueAllPending() on app start, so
        // there is nothing to redeliver if the system kills us.
        return START_NOT_STICKY
    }

    /**
     * Android 15+ enforces a ~6h cap on mediaProcessing/dataSync foreground
     * services; the OS calls this when the budget runs out (API 35+ only). We
     * stop cleanly — the next app launch resumes remaining work through
     * enqueueAllPending(), since the queue never survives process death anyway.
     */
    override fun onTimeout(startId: Int, fgsServiceType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        // observeProgress() re-posts the notification via notify(); if we die
        // without startForeground having attached it, that copy would linger as
        // an un-dismissable ongoing notification. Always clear it explicitly.
        notificationManager()?.cancel(NOTIF_ID)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeProgress() {
        val controller = (application as KonoApp).container.analysisController
        scope.launch {
            controller.progress.collect { map ->
                if (map.isEmpty()) {
                    // Queue drained: tear down right away.
                    stopSelf()
                    return@collect
                }
                // "Current" item = the one furthest along (largest progress).
                val current = map.maxByOrNull { it.value } ?: return@collect
                val name = displayNameFor(current.key)
                val waiting = map.size - 1
                val percent = (current.value.coerceIn(0f, 1f) * 100f).toInt()
                notificationManager()?.notify(
                    NOTIF_ID,
                    buildNotification(bodyText(name, waiting), percent),
                )
                // Throttle to ~2 refreshes/s; StateFlow conflates whatever we
                // miss while suspended here, so no updates are queued up.
                delay(500)
            }
        }
    }

    private suspend fun displayNameFor(recordingId: Long): String {
        if (recordingId == cachedId) return cachedName
        val repo = (application as KonoApp).container.recordingRepository
        val name = repo.getRecording(recordingId)?.displayName ?: ""
        cachedId = recordingId
        cachedName = name
        return name
    }

    private fun bodyText(currentName: String, waiting: Int): String =
        "$currentName · 대기 ${waiting}개"

    private fun buildNotification(body: String, percent: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("노래 구간 분석 중")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "노래 구간 분석",
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager()?.createNotificationChannel(channel)
        }
    }

    private fun notificationManager(): NotificationManager? = getSystemService()

    private fun foregroundServiceType(): Int = when {
        // mediaProcessing (API 35+) gets rejected with
        // InvalidForegroundServiceTypeException("type none") on Android 16
        // (Galaxy S23+, targetSdk 35) even though the manifest declares it —
        // dataSync is accepted everywhere and carries the same 6h budget.
        Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else -> 0
    }

    companion object {
        private const val TAG = "AnalysisService"
        private const val CHANNEL_ID = "analysis"
        private const val NOTIF_ID = 1001
        private const val WAKE_LOCK_TAG = "konodiary:analysis"

        /**
         * Best-effort start. Wrapped in runCatching because starting a
         * foreground service from the background can throw on newer Android;
         * if it fails the in-process analysis still runs, just without the
         * wake lock / notification protection.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AnalysisService::class.java),
                )
            }
        }
    }
}
