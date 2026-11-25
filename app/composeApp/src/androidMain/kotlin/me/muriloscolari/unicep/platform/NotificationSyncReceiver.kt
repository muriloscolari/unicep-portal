package me.muriloscolari.unicep.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
                NotificationSyncScheduler.cancelSyncAlarm(appCtx)
                val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
                if (!token.isNullOrBlank()) {
                    NotificationSyncScheduler.schedulePeriodicSync(appCtx)
                    val rawEvents = PlatformBridge.getString(PrefKeys.CACHE_CALENDAR_EVENTS)
                    if (!rawEvents.isNullOrBlank()) {
                        val events = runCatching {
                            json.decodeFromString<List<UserCalendarEvent>>(rawEvents)
                        }.getOrNull().orEmpty()
                        if (events.isNotEmpty()) {
                            // Reboot / time change clears system alarms; clear signature so they are re-armed.
                            PlatformBridge.remove(PrefKeys.CALENDAR_ALARMS_SIGNATURE)
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
                // Legacy alarm from <= 1.12.2: disarm permanently and rely solely on WorkManager.
                NotificationSyncScheduler.cancelSyncAlarm(appCtx)
                val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
                if (token.isNullOrBlank()) {
                    NotificationSyncScheduler.cancelPeriodicSync(appCtx)
                } else {
                    NotificationSyncScheduler.schedulePeriodicSync(appCtx)
                }
            }
        }
    }
}
