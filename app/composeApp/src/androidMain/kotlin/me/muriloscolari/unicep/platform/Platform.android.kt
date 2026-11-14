package me.muriloscolari.unicep.platform

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

actual object PlatformBridge {
    private var appContext: Context? = null
    private var secureStore: AndroidSecureStore? = null
    private var activityRef: WeakReference<Activity>? = null

    fun initContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            secureStore = AndroidSecureStore(context.applicationContext)
        }
    }

    fun bindActivity(activity: Activity) {
        initContext(activity)
        activityRef = WeakReference(activity)
    }

    actual fun capabilities(): DeviceCapabilities {
        val ctx = appContext
        val sdkOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val lowRam = (ctx?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        val powerSave = (ctx?.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

        val reason = when {
            !sdkOk -> "Android < 12"
            lowRam -> "Dispositivo com pouca RAM"
            powerSave -> "Modo economia de energia ativo"
            else -> null
        }

        return DeviceCapabilities(
            supportsAppleEffects = reason == null,
            fallbackReason = reason,
            defaultServerUrl = "https://unicep.muriloscolari.me",
            isWeb = false
        )
    }

    actual fun getString(key: String): String? = secureStore?.getString(key)

    actual fun putString(key: String, value: String) {
        secureStore?.putString(key, value)
    }

    actual fun remove(key: String) {
        secureStore?.remove(key)
    }

    actual fun openExternalUrl(url: String) {
        val ctx = appContext ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    actual fun scheduleGradeSync() {
        val ctx = appContext ?: return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<GradeSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "unicep_grade_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    actual fun cancelGradeSync() {
        val ctx = appContext ?: return
        WorkManager.getInstance(ctx).cancelUniqueWork("unicep_grade_sync")
    }

    actual fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val act = activityRef?.get() ?: return
        if (ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    actual fun createHttpEngine(): HttpClientEngine = OkHttp.create()
}
