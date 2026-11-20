package me.muriloscolari.unicep.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.repo.PrefKeys

class NotificationSyncReceiver : BroadcastReceiver() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        PlatformBridge.initContext(appCtx)

        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
                if (!token.isNullOrBlank()) {
                    NotificationSyncScheduler.schedulePeriodicSync(appCtx)
                    val rawEvents = PlatformBridge.getString(PrefKeys.CACHE_CALENDAR_EVENTS)
                    if (!rawEvents.isNullOrBlank()) {
                        val events = runCatching {
                            json.decodeFromString<List<UserCalendarEvent>>(rawEvents)
                        }.getOrNull().orEmpty()
                        if (events.isNotEmpty()) {
                            PlatformBridge.syncCalendarReminders(events)
                        }
                    }
                    BackgroundSyncEngine.evaluateCachedCalendarReminders(appCtx)
                }
            }

            NotificationSyncScheduler.ACTION_CALENDAR_REMINDER -> {
                val reminderKey = intent.getStringExtra(CalendarReminderWorker.KEY_REMINDER_KEY).orEmpty()
                val notifId = intent.getIntExtra(CalendarReminderWorker.KEY_NOTIF_ID, 90_001)
                val title = intent.getStringExtra(CalendarReminderWorker.KEY_TITLE).orEmpty()
                val body = intent.getStringExtra(CalendarReminderWorker.KEY_BODY).orEmpty()
                if (reminderKey.isBlank() || title.isBlank()) return

                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        BackgroundSyncEngine.deliverCalendarReminderIfNeeded(
                            context = appCtx,
                            reminderKey = reminderKey,
                            notifId = notifId,
                            title = title,
                            body = body
                        )
                    } finally {
                        pending.finish()
                    }
                }
            }

            NotificationSyncScheduler.ACTION_BACKGROUND_SYNC -> {
                val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
                if (token.isNullOrBlank()) {
                    NotificationSyncScheduler.cancelPeriodicSync(appCtx)
                    return
                }

                // Re-arm the next wake-up immediately so the chain never breaks.
                NotificationSyncScheduler.scheduleNextSyncAlarm(appCtx)

                if (!isNetworkAvailable(appCtx)) {
                    BackgroundSyncEngine.evaluateCachedCalendarReminders(appCtx)
                    NotificationSyncScheduler.enqueueImmediateFallbackSync(appCtx)
                    return
                }

                val pending = goAsync()
                val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "unicep:bg_sync")?.apply {
                    setReferenceCounted(false)
                    runCatching { acquire(10_000L) }
                }

                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val outcome = withTimeoutOrNull(7_500L) {
                            BackgroundSyncEngine.sync(appCtx, fastOnly = true)
                        }
                        if (outcome == null ||
                            outcome == SyncOutcome.RETRY_NEEDED ||
                            BackgroundSyncEngine.isFullSyncDue()
                        ) {
                            NotificationSyncScheduler.enqueueImmediateFallbackSync(appCtx)
                        }
                    } finally {
                        runCatching {
                            if (wakeLock != null && wakeLock.isHeld) {
                                wakeLock.release()
                            }
                        }
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
