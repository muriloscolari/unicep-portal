package me.muriloscolari.unicep.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.muriloscolari.unicep.data.repo.PrefKeys

class GradeSyncWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        PlatformBridge.initContext(appContext)
        return when (BackgroundSyncEngine.sync(appContext, fastOnly = false)) {
            SyncOutcome.SUCCESS -> {
                if (!PlatformBridge.getString(PrefKeys.JWT_TOKEN).isNullOrBlank()) {
                    NotificationSyncScheduler.scheduleNextSyncAlarm(appContext)
                }
                Result.success()
            }
            SyncOutcome.NO_SESSION -> Result.success()
            SyncOutcome.RETRY_NEEDED -> {
                if (!PlatformBridge.getString(PrefKeys.JWT_TOKEN).isNullOrBlank()) {
                    NotificationSyncScheduler.scheduleNextSyncAlarm(appContext)
                }
                Result.retry()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "grades_updates_v2"
        const val CALENDAR_CHANNEL_ID = "calendar_reminders_v1"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val gradesChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Notas, Faltas e Sala Virtual",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificações quando o professor lança notas, faltas, atividades, materiais ou avisos"
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                val calendarChannel = NotificationChannel(
                    CALENDAR_CHANNEL_ID,
                    "Lembretes do Calendário",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Lembretes agendados para eventos, provas e compromissos"
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(gradesChannel)
                nm.createNotificationChannel(calendarChannel)
            }
        }

        fun showLocalNotification(
            context: Context,
            id: Int,
            title: String,
            body: String,
            openTab: Int = AppTabIndex.CENTRAL
        ): Boolean = buildAndPostNotification(context, CHANNEL_ID, id, title, body, openTab)

        fun showCalendarNotification(
            context: Context,
            id: Int,
            title: String,
            body: String
        ): Boolean = buildAndPostNotification(context, CALENDAR_CHANNEL_ID, id, title, body, AppTabIndex.CALENDAR)

        private fun buildAndPostNotification(
            context: Context,
            channelId: String,
            id: Int,
            title: String,
            body: String,
            openTab: Int
        ): Boolean {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_tab", openTab)
            }
            val pending = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    id,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val customIconId = context.resources.getIdentifier("ic_stat_unicep", "drawable", context.packageName)
            val smallIcon = if (customIconId != 0) customIconId else android.R.drawable.ic_dialog_info

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .apply { if (pending != null) setContentIntent(pending) }
                .build()

            NotificationManagerCompat.from(context).notify(id, notification)
            return true
        }
    }
}
