package me.muriloscolari.unicep.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CalendarReminderWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminderKey = inputData.getString(KEY_REMINDER_KEY)?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val notifId = inputData.getInt(KEY_NOTIF_ID, 90_001)
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val body = inputData.getString(KEY_BODY).orEmpty()

        BackgroundSyncEngine.deliverCalendarReminderIfNeeded(
            context = appContext,
            reminderKey = reminderKey,
            notifId = notifId,
            title = title,
            body = body
        )
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
