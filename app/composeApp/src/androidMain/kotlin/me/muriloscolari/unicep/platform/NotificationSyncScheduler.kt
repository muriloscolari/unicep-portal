package me.muriloscolari.unicep.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.repo.PrefKeys
import me.muriloscolari.unicep.data.repo.ScheduledCalendarReminder
import java.util.concurrent.TimeUnit

internal object NotificationSyncScheduler {
    const val ACTION_BACKGROUND_SYNC = "me.muriloscolari.unicep.ACTION_BACKGROUND_SYNC"
    const val ACTION_CALENDAR_REMINDER = "me.muriloscolari.unicep.ACTION_CALENDAR_REMINDER"
    const val SYNC_ALARM_INTERVAL_MS = 10L * 60_000L

    private const val REQ_CODE_BACKGROUND_SYNC = 41_001
    private const val UNIQUE_PERIODIC_WORK = "unicep_grade_sync"
    private const val UNIQUE_NOW_WORK = "unicep_grade_sync_now"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun schedulePeriodicSync(context: Context) {
        val appCtx = context.applicationContext
        PlatformBridge.initContext(appCtx)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<GradeSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        scheduleNextSyncAlarm(appCtx, SYNC_ALARM_INTERVAL_MS)
    }

    fun scheduleNextSyncAlarm(context: Context, delayMs: Long = SYNC_ALARM_INTERVAL_MS) {
        val appCtx = context.applicationContext
        PlatformBridge.initContext(appCtx)
        val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
        if (token.isNullOrBlank()) {
            cancelSyncAlarm(appCtx)
            return
        }

        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildSyncAlarmPendingIntent(appCtx, create = true) ?: return
        val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(60_000L)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun cancelPeriodicSync(context: Context) {
        val appCtx = context.applicationContext
        val wm = WorkManager.getInstance(appCtx)
        wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
        wm.cancelUniqueWork(UNIQUE_NOW_WORK)
        cancelSyncAlarm(appCtx)
    }

    fun enqueueImmediateFallbackSync(context: Context) {
        val appCtx = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val builder = OneTimeWorkRequestBuilder<GradeSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(appCtx).enqueueUniqueWork(
            UNIQUE_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            builder.build()
        )
    }

    fun scheduleCalendarAlarms(context: Context, triggers: List<ScheduledCalendarReminder>) {
        val appCtx = context.applicationContext
        PlatformBridge.initContext(appCtx)
        cancelCalendarAlarms(appCtx)
        if (triggers.isEmpty()) return

        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val scheduledIds = mutableListOf<Int>()

        triggers.forEach { rem ->
            val intent = Intent(appCtx, NotificationSyncReceiver::class.java).apply {
                action = ACTION_CALENDAR_REMINDER
                putExtra(CalendarReminderWorker.KEY_EVENT_ID, rem.eventId)
                putExtra(CalendarReminderWorker.KEY_REMINDER_KEY, rem.reminderKey)
                putExtra(CalendarReminderWorker.KEY_NOTIF_ID, rem.notifId)
                putExtra(CalendarReminderWorker.KEY_TITLE, rem.title)
                putExtra(CalendarReminderWorker.KEY_BODY, rem.body)
            }
            val pi = PendingIntent.getBroadcast(
                appCtx,
                rem.notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (canExact) {
                runCatching {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rem.triggerAtMillis, pi)
                }.onFailure {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rem.triggerAtMillis, pi)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rem.triggerAtMillis, pi)
            }
            scheduledIds += rem.notifId
        }

        if (scheduledIds.isNotEmpty()) {
            PlatformBridge.putStringSync(PrefKeys.CALENDAR_ALARM_IDS, json.encodeToString(scheduledIds))
        }
    }

    fun cancelCalendarAlarms(context: Context) {
        val appCtx = context.applicationContext
        PlatformBridge.initContext(appCtx)
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val raw = PlatformBridge.getString(PrefKeys.CALENDAR_ALARM_IDS)
        if (am != null && !raw.isNullOrBlank()) {
            val ids = runCatching { json.decodeFromString<List<Int>>(raw) }.getOrNull().orEmpty()
            ids.forEach { reqCode ->
                val intent = Intent(appCtx, NotificationSyncReceiver::class.java).apply {
                    action = ACTION_CALENDAR_REMINDER
                }
                val pi = PendingIntent.getBroadcast(
                    appCtx,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pi != null) {
                    am.cancel(pi)
                    pi.cancel()
                }
            }
        }
        PlatformBridge.remove(PrefKeys.CALENDAR_ALARM_IDS)
    }

    private fun cancelSyncAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildSyncAlarmPendingIntent(context, create = false) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun buildSyncAlarmPendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, NotificationSyncReceiver::class.java).apply {
            action = ACTION_BACKGROUND_SYNC
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQ_CODE_BACKGROUND_SYNC, intent, flags)
    }
}
