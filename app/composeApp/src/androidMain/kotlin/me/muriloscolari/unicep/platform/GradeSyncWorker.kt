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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.api.LoginRequest
import me.muriloscolari.unicep.data.api.PortalApiClient
import me.muriloscolari.unicep.data.api.UnauthorizedException
import me.muriloscolari.unicep.data.repo.PrefKeys
import me.muriloscolari.unicep.data.repo.evaluateCalendarReminders
import me.muriloscolari.unicep.data.repo.evaluateFrequencyAlerts
import me.muriloscolari.unicep.data.repo.evaluateVirtualDeadlineAlerts
import me.muriloscolari.unicep.data.repo.readFrequencyAlertThreshold
import me.muriloscolari.unicep.data.repo.readVirtualDeadlineLeadHours
import me.muriloscolari.unicep.data.repo.readVirtualNotifyNewAssignment

class GradeSyncWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        PlatformBridge.initContext(appContext)
        val server = PlatformBridge.getString(PrefKeys.SERVER_URL)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        var token = PlatformBridge.getString(PrefKeys.JWT_TOKEN) ?: return Result.success()

        val api = PortalApiClient()
        val unread = try {
            api.notifications(server, token, unreadOnly = true).items
        } catch (_: UnauthorizedException) {
            val ra = PlatformBridge.getString(PrefKeys.RA) ?: return Result.success()
            val pass = PlatformBridge.getString(PrefKeys.PASSWORD) ?: return Result.success()
            val perfil = PlatformBridge.getString(PrefKeys.PERFIL) ?: "alunograd"
            val fresh = runCatching { api.login(server, LoginRequest(ra, pass, perfil)) }.getOrNull()
                ?: return Result.retry()
            token = fresh.token
            PlatformBridge.putString(PrefKeys.JWT_TOKEN, token)
            runCatching { api.notifications(server, token, unreadOnly = true).items }.getOrElse { return Result.retry() }
        } catch (_: Exception) {
            return Result.retry()
        }

        val lastSeenId = PlatformBridge.getString(PrefKeys.LAST_NOTIFIED_ID)?.toLongOrNull() ?: 0L
        val freshOnes = unread.filter { it.id > lastSeenId }.sortedBy { it.id }
        val notifyVirtualNew = readVirtualNotifyNewAssignment()
        if (freshOnes.isNotEmpty()) {
            var maxProcessedId = lastSeenId
            var anyHandled = false
            freshOnes.forEach { n ->
                val isD2L = n.kind == "d2l_assignment" ||
                    n.kind == "d2l_announcement" ||
                    n.kind == "d2l_content" ||
                    n.kind == "d2l_quiz"
                if (isD2L) {
                    if (!notifyVirtualNew) {
                        maxProcessedId = maxOf(maxProcessedId, n.id)
                        anyHandled = true
                        return@forEach
                    }
                    val prefix = when (n.kind) {
                        "d2l_announcement" -> "Novo aviso"
                        "d2l_content" -> "Novo material"
                        "d2l_quiz" -> "Novo questionário"
                        else -> "Nova atividade"
                    }
                    val title = "$prefix: ${n.disciplineName.ifBlank { "Sala Virtual" }}"
                    val body = if (n.newValue.isNotBlank()) "${n.field} • ${n.newValue}" else n.field
                    if (showLocalNotification(appContext, n.id.toInt(), title, body, openTab = AppTabIndex.VIRTUAL)) {
                        maxProcessedId = maxOf(maxProcessedId, n.id)
                        anyHandled = true
                    }
                    return@forEach
                }
                val title = if (n.kind == "grade") "Nova nota: ${n.disciplineName}" else "Atualização: ${n.disciplineName}"
                val oldTxt = if (n.oldValue.isBlank()) "—" else n.oldValue
                val body = "${n.field}: $oldTxt → ${n.newValue}"
                if (showLocalNotification(appContext, n.id.toInt(), title, body, openTab = AppTabIndex.CENTRAL)) {
                    maxProcessedId = maxOf(maxProcessedId, n.id)
                    anyHandled = true
                }
            }
            if (anyHandled) {
                PlatformBridge.putString(PrefKeys.LAST_NOTIFIED_ID, maxProcessedId.toString())
            }
        }

        val freqThreshold = readFrequencyAlertThreshold()
        if (freqThreshold > 0) {
            runCatching { api.dashboard(server, token) }.getOrNull()?.let { dash ->
                evaluateFrequencyAlerts(dash.currentReport.items, freqThreshold)
            }
        }

        val leadHours = readVirtualDeadlineLeadHours()
        if (leadHours.isNotEmpty()) {
            runCatching { api.virtualOverview(server, token) }.getOrNull()?.let { ov ->
                evaluateVirtualDeadlineAlerts(ov.upcomingAssignments, leadHours)
            }
        }

        runCatching { api.calendarEvents(server, token).events }.getOrNull()?.let { events ->
            runCatching {
                PlatformBridge.putString(PrefKeys.CACHE_CALENDAR_EVENTS, Json.encodeToString(events))
            }
            PlatformBridge.syncCalendarReminders(events)
            evaluateCalendarReminders(
                events = events,
                utcOffsetMinutes = PlatformBridge.currentLocalDateTime().utcOffsetMinutes
            )
        }
        return Result.success()
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
