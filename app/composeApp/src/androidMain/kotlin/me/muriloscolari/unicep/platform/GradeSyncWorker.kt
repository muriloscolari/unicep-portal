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
import me.muriloscolari.unicep.data.api.LoginRequest
import me.muriloscolari.unicep.data.api.PortalApiClient
import me.muriloscolari.unicep.data.api.UnauthorizedException
import me.muriloscolari.unicep.data.repo.PrefKeys

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

        if (unread.isEmpty()) return Result.success()

        val lastSeenId = PlatformBridge.getString(PrefKeys.LAST_NOTIFIED_ID)?.toLongOrNull() ?: 0L
        val freshOnes = unread.filter { it.id > lastSeenId }.sortedBy { it.id }
        if (freshOnes.isEmpty()) return Result.success()

        var anyPosted = false
        freshOnes.forEach { n ->
            val title = if (n.kind == "grade") "Nova nota: ${n.disciplineName}" else "Atualização: ${n.disciplineName}"
            val oldTxt = if (n.oldValue.isBlank()) "—" else n.oldValue
            val body = "${n.field}: $oldTxt → ${n.newValue}"
            if (showLocalNotification(appContext, n.id.toInt(), title, body, openTab = 2)) {
                anyPosted = true
            }
        }

        if (anyPosted) {
            PlatformBridge.putString(PrefKeys.LAST_NOTIFIED_ID, freshOnes.last().id.toString())
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "grades_updates_v2"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Notas e Faltas",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificações imediatas quando o professor lança ou altera notas"
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }

        fun showLocalNotification(
            context: Context,
            id: Int,
            title: String,
            body: String,
            openTab: Int = 2
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

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
