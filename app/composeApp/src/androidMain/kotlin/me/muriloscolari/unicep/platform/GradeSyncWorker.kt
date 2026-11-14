package me.muriloscolari.unicep.platform

import android.Manifest
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

        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 4)
        }
        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                appContext,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val manager = NotificationManagerCompat.from(appContext)
        freshOnes.forEach { n ->
            val title = if (n.kind == "grade") "Nova nota: ${n.disciplineName}" else "Atualização: ${n.disciplineName}"
            val oldTxt = if (n.oldValue.isBlank()) "—" else n.oldValue
            val body = "${n.field}: $oldTxt → ${n.newValue}"
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .apply { if (pending != null) setContentIntent(pending) }
                .build()
            manager.notify(n.id.toInt(), notification)
        }

        PlatformBridge.putString(PrefKeys.LAST_NOTIFIED_ID, freshOnes.last().id.toString())
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notas e Faltas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações automáticas quando o professor lança ou altera notas"
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "grades_updates"
    }
}
