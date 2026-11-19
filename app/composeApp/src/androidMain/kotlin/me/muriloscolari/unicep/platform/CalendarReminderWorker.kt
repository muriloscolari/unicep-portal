package me.muriloscolari.unicep.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.repo.PrefKeys

class CalendarReminderWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun doWork(): Result {
        PlatformBridge.initContext(appContext)
        val reminderKey = inputData.getString(KEY_REMINDER_KEY)?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val notifId = inputData.getInt(KEY_NOTIF_ID, 90_001)
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val body = inputData.getString(KEY_BODY).orEmpty()

        val prevMap: MutableMap<String, Boolean> = PlatformBridge.getString(PrefKeys.CALENDAR_REMINDERS_NOTIFIED)
            ?.let { raw -> runCatching { json.decodeFromString<Map<String, Boolean>>(raw) }.getOrNull() }
            ?.toMutableMap()
            ?: mutableMapOf()

        if (prevMap[reminderKey] == true) {
            return Result.success()
        }

        val posted = GradeSyncWorker.showCalendarNotification(appContext, notifId, title, body)
        if (posted) {
            prevMap[reminderKey] = true
            PlatformBridge.putString(PrefKeys.CALENDAR_REMINDERS_NOTIFIED, json.encodeToString(prevMap))
        }
        return Result.success()
    }

    companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_REMINDER_KEY = "reminder_key"
        const val KEY_NOTIF_ID = "notif_id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }
}
