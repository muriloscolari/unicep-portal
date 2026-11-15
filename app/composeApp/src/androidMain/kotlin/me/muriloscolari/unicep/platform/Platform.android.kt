package me.muriloscolari.unicep.platform

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

actual object PlatformBridge {
    private var appContext: Context? = null
    private var secureStore: AndroidSecureStore? = null
    private var activityRef: WeakReference<Activity>? = null
    private var backHandler: (() -> Boolean)? = null
    private var backEnabled: Boolean = false
    private var predictiveBackListener: ((PredictiveBackState) -> Unit)? = null
    private var registeredBackCallback: OnBackPressedCallback? = null

    fun initContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            secureStore = AndroidSecureStore(context.applicationContext)
            GradeSyncWorker.ensureChannel(context.applicationContext)
        }
    }

    fun bindActivity(activity: Activity) {
        initContext(activity)
        activityRef = WeakReference(activity)
        if (activity is ComponentActivity) {
            registeredBackCallback?.remove()
            val callback = object : OnBackPressedCallback(backEnabled) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    predictiveBackListener?.invoke(
                        PredictiveBackState(
                            active = true,
                            progress = backEvent.progress,
                            swipeEdge = backEvent.swipeEdge
                        )
                    )
                    performHaptic(PortalHapticType.IMPACT_LIGHT, 0.5f)
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    predictiveBackListener?.invoke(
                        PredictiveBackState(
                            active = true,
                            progress = backEvent.progress,
                            swipeEdge = backEvent.swipeEdge
                        )
                    )
                }

                override fun handleOnBackCancelled() {
                    predictiveBackListener?.invoke(PredictiveBackState())
                }

                override fun handleOnBackPressed() {
                    predictiveBackListener?.invoke(PredictiveBackState())
                    val consumed = backHandler?.invoke() ?: false
                    if (consumed) {
                        performHaptic(PortalHapticType.SELECTION, 0.65f)
                    } else {
                        isEnabled = false
                        activity.onBackPressedDispatcher.onBackPressed()
                        isEnabled = backEnabled
                    }
                }
            }
            activity.onBackPressedDispatcher.addCallback(activity, callback)
            registeredBackCallback = callback
        }
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
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true) && !clean.startsWith("http://", ignoreCase = true)) {
            return
        }
        val ctx = appContext ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    actual fun shareText(title: String, text: String): Boolean {
        val ctx = activityRef?.get() ?: appContext ?: return false
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, title).apply {
                if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    actual fun exportPng(fileName: String, data: ExportCardData): Boolean {
        val ctx = activityRef?.get() ?: appContext ?: return false
        return runCatching {
            val bitmap = renderExportBitmap(data)
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "unicep_export" }
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "$safeName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, data.title)
                clipData = ClipData.newRawUri(data.title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, data.title).apply {
                if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private var activePrintWebView: WebView? = null

    actual fun printDocument(title: String, data: ExportCardData): Boolean {
        val act = activityRef?.get() ?: return false
        val html = buildExportPrintHtml(data)
        act.runOnUiThread {
            runCatching {
                val webView = WebView(act)
                activePrintWebView = webView
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val pm = act.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                        if (pm != null && view != null) {
                            val adapter = view.createPrintDocumentAdapter(title)
                            pm.print(title, adapter, PrintAttributes.Builder().build())
                        }
                        activePrintWebView = null
                    }
                }
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        }
        return true
    }

    private fun renderExportBitmap(data: ExportCardData): Bitmap {
        val width = 1080
        val pad = 48f
        val innerPad = 28f
        val contentWidth = (width - pad * 2).toInt()
        val cardTextWidth = (contentWidth - innerPad * 2).toInt()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 26f
        }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#007AFF")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 25f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 22f
        }

        fun layoutFor(text: String, paint: TextPaint, maxW: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW.coerceAtLeast(100))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1f)
                .build()

        val titleLayout = layoutFor(data.title, titlePaint, contentWidth)
        val subtitleLayout = if (data.subtitle.isNotBlank()) layoutFor(data.subtitle, subtitlePaint, contentWidth) else null

        data class MeasuredSection(
            val heading: StaticLayout,
            val badge: StaticLayout?,
            val lines: List<StaticLayout>,
            val cardHeight: Float
        )

        val measured = data.sections.map { sec ->
            val h = layoutFor(sec.heading, headingPaint, cardTextWidth)
            val b = if (sec.badge.isNotBlank()) layoutFor(sec.badge, badgePaint, cardTextWidth) else null
            val ls = sec.lines.map { layoutFor(it, linePaint, cardTextWidth) }
            val ch = innerPad * 2 +
                h.height +
                (if (b != null) 8f + b.height else 0f) +
                ls.sumOf { (10f + it.height).toDouble() }.toFloat()
            MeasuredSection(h, b, ls, ch)
        }

        val footerLayout = if (data.footer.isNotBlank()) layoutFor(data.footer, footerPaint, contentWidth) else null
        val totalHeight = (pad +
            titleLayout.height +
            (if (subtitleLayout != null) 8f + subtitleLayout.height else 0f) +
            28f +
            measured.sumOf { (it.cardHeight + 18f).toDouble() }.toFloat() +
            (if (footerLayout != null) 14f + footerLayout.height else 0f) +
            pad).toInt().coerceAtLeast(240)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F2F4F8"))

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#007AFF")
        }

        var y = pad
        canvas.drawRoundRect(RectF(pad, y, pad + 8f, y + titleLayout.height), 4f, 4f, accentBarPaint)
        canvas.save()
        canvas.translate(pad + 22f, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height

        if (subtitleLayout != null) {
            y += 8f
            canvas.save()
            canvas.translate(pad + 22f, y)
            subtitleLayout.draw(canvas)
            canvas.restore()
            y += subtitleLayout.height
        }
        y += 28f

        measured.forEach { sec ->
            val rect = RectF(pad, y, width - pad, y + sec.cardHeight)
            canvas.drawRoundRect(rect, 24f, 24f, cardBgPaint)
            canvas.drawRoundRect(rect, 24f, 24f, cardBorderPaint)

            var cy = y + innerPad
            canvas.save()
            canvas.translate(pad + innerPad, cy)
            sec.heading.draw(canvas)
            canvas.restore()
            cy += sec.heading.height

            if (sec.badge != null) {
                cy += 8f
                canvas.save()
                canvas.translate(pad + innerPad, cy)
                sec.badge.draw(canvas)
                canvas.restore()
                cy += sec.badge.height
            }

            sec.lines.forEach { lineLayout ->
                cy += 10f
                canvas.save()
                canvas.translate(pad + innerPad, cy)
                lineLayout.draw(canvas)
                canvas.restore()
                cy += lineLayout.height
            }

            y += sec.cardHeight + 18f
        }

        if (footerLayout != null) {
            y += 14f
            canvas.save()
            canvas.translate(pad, y)
            footerLayout.draw(canvas)
            canvas.restore()
        }

        return bitmap
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

    actual fun postSystemNotification(id: Int, title: String, body: String, openTab: Int): Boolean {
        val ctx = appContext ?: return false
        val posted = GradeSyncWorker.showLocalNotification(ctx, id, title, body, openTab)
        if (!posted) {
            requestNotificationPermissionIfNeeded()
        }
        return posted
    }

    actual fun initNavigationHistory(initialRoute: String) {}

    actual fun pushNavigationRoute(route: String) {}

    actual fun setPopRouteListener(listener: (String) -> Unit) {}

    actual fun setAndroidBackHandler(handler: (() -> Boolean)?) {
        backHandler = handler
        if (handler == null) {
            backEnabled = false
            registeredBackCallback?.isEnabled = false
        }
    }

    actual fun setAndroidBackEnabled(enabled: Boolean) {
        backEnabled = enabled
        registeredBackCallback?.isEnabled = enabled && backHandler != null
    }

    actual fun setAndroidPredictiveBackListener(listener: ((PredictiveBackState) -> Unit)?) {
        predictiveBackListener = listener
    }

    actual fun performHaptic(type: PortalHapticType, intensity: Float) {
        val ctx = appContext ?: return
        val scale = intensity.coerceIn(0.2f, 1f)
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val primitive = when (type) {
                    PortalHapticType.BOUNDARY_THUD,
                    PortalHapticType.IMPACT_HEAVY -> VibrationEffect.Composition.PRIMITIVE_THUD
                    PortalHapticType.IMPACT_MEDIUM -> VibrationEffect.Composition.PRIMITIVE_CLICK
                    PortalHapticType.IMPACT_LIGHT -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                    PortalHapticType.SELECTION -> VibrationEffect.Composition.PRIMITIVE_TICK
                }
                if (vibrator.areAllPrimitivesSupported(primitive)) {
                    val scaledStrength = when (type) {
                        PortalHapticType.BOUNDARY_THUD -> (0.45f + 0.55f * scale).coerceIn(0.35f, 1f)
                        PortalHapticType.IMPACT_HEAVY -> (0.55f + 0.45f * scale).coerceIn(0.4f, 1f)
                        PortalHapticType.IMPACT_MEDIUM -> (0.4f + 0.45f * scale).coerceIn(0.35f, 0.9f)
                        PortalHapticType.IMPACT_LIGHT -> (0.3f + 0.35f * scale).coerceIn(0.25f, 0.7f)
                        PortalHapticType.SELECTION -> (0.28f + 0.32f * scale).coerceIn(0.25f, 0.65f)
                    }
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(primitive, scaledStrength)
                        .compose()
                    vibrator.vibrate(effect)
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val predefined = when (type) {
                    PortalHapticType.BOUNDARY_THUD,
                    PortalHapticType.IMPACT_HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                    PortalHapticType.IMPACT_MEDIUM -> VibrationEffect.EFFECT_CLICK
                    PortalHapticType.IMPACT_LIGHT,
                    PortalHapticType.SELECTION -> VibrationEffect.EFFECT_TICK
                }
                vibrator.vibrate(VibrationEffect.createPredefined(predefined))
                return
            }

            val durationMs = when (type) {
                PortalHapticType.BOUNDARY_THUD -> (14L + (14f * scale).toLong())
                PortalHapticType.IMPACT_HEAVY -> 22L
                PortalHapticType.IMPACT_MEDIUM -> 14L
                PortalHapticType.IMPACT_LIGHT -> 9L
                PortalHapticType.SELECTION -> 8L
            }
            val amplitude = when (type) {
                PortalHapticType.BOUNDARY_THUD -> (90 + (150 * scale).toInt()).coerceIn(60, 255)
                PortalHapticType.IMPACT_HEAVY -> 220
                PortalHapticType.IMPACT_MEDIUM -> 150
                PortalHapticType.IMPACT_LIGHT -> 95
                PortalHapticType.SELECTION -> 85
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }

    actual fun currentAppVersion(): String {
        val ctx = appContext ?: return "1.2.0"
        return AppUpdater.currentVersion(ctx)
    }

    actual suspend fun checkForAppUpdate(): AppUpdateInfo? {
        val ctx = appContext ?: return null
        return AppUpdater.checkLatest(ctx)
    }

    actual suspend fun downloadAndInstallAppUpdate(apkUrl: String, onProgress: (Float) -> Unit): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Contexto Android indisponível."))
        return AppUpdater.downloadAndInstall(ctx, apkUrl, onProgress)
    }

    actual fun createHttpEngine(): HttpClientEngine = OkHttp.create()

    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun isReduceMotionEnabled(): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            android.provider.Settings.Global.getFloat(
                ctx.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}
