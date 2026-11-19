package me.muriloscolari.unicep.platform

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
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
import android.os.Environment
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.DocumentsContract
import android.provider.MediaStore
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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.repo.computeUpcomingReminderTriggers

actual object PlatformBridge {
    private val localDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private var appContext: Context? = null
    private var secureStore: AndroidSecureStore? = null
    private var activityRef: WeakReference<Activity>? = null
    private var backHandler: (() -> Boolean)? = null
    private var backEnabled: Boolean = false
    private var predictiveBackListener: ((PredictiveBackState) -> Unit)? = null
    private var openTabListener: ((Int) -> Unit)? = null
    private var registeredBackCallback: OnBackPressedCallback? = null
    private var folderPickerTrigger: (() -> Unit)? = null
    private var pendingFolderContinuation: CancellableContinuation<String?>? = null

    private const val PREF_TREE_URI = "virtual_download_tree_uri"
    private const val PREF_FOLDER_LABEL = "virtual_download_folder_label"
    private const val DEFAULT_DOWNLOAD_LABEL = "Downloads/UnicepPortal"
    private const val CALENDAR_REMINDER_TAG = "unicep_calendar_reminder"

    fun bindFolderPicker(trigger: () -> Unit) {
        folderPickerTrigger = trigger
    }

    fun onFolderPicked(uri: Uri?) {
        val cont = pendingFolderContinuation
        pendingFolderContinuation = null
        val ctx = appContext
        if (uri == null || ctx == null) {
            cont?.resume(null)
            return
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, flags) }
        val label = deriveTreeFolderLabel(ctx, uri)
        putString(PREF_TREE_URI, uri.toString())
        putString(PREF_FOLDER_LABEL, label)
        cont?.resume(label)
    }

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

    actual fun openExternalUrl(url: String): Boolean {
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true) && !clean.startsWith("http://", ignoreCase = true)) {
            return false
        }
        val ctx = appContext ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            ctx.startActivity(intent)
            true
        }.getOrDefault(false)
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

    actual fun syncCalendarReminders(events: List<UserCalendarEvent>) {
        val ctx = appContext ?: return
        val wm = WorkManager.getInstance(ctx)
        wm.cancelAllWorkByTag(CALENDAR_REMINDER_TAG)

        val nowMs = System.currentTimeMillis()
        val offsetMins = currentLocalDateTime().utcOffsetMinutes
        val triggers = computeUpcomingReminderTriggers(
            events = events,
            nowMillis = nowMs,
            utcOffsetMinutes = offsetMins
        ).take(60)

        triggers.forEach { rem ->
            val delayMs = (rem.triggerAtMillis - nowMs).coerceAtLeast(1_000L)
            val data = workDataOf(
                CalendarReminderWorker.KEY_EVENT_ID to rem.eventId,
                CalendarReminderWorker.KEY_REMINDER_KEY to rem.reminderKey,
                CalendarReminderWorker.KEY_NOTIF_ID to rem.notifId,
                CalendarReminderWorker.KEY_TITLE to rem.title,
                CalendarReminderWorker.KEY_BODY to rem.body
            )
            val req = OneTimeWorkRequestBuilder<CalendarReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(CALENDAR_REMINDER_TAG)
                .setInputData(data)
                .build()
            wm.enqueueUniqueWork(
                "cal_rem_${rem.reminderKey}",
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }

    actual fun cancelAllCalendarReminders() {
        val ctx = appContext ?: return
        WorkManager.getInstance(ctx).cancelAllWorkByTag(CALENDAR_REMINDER_TAG)
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

    actual fun currentLocalDateTime(): LocalDateTimeInfo {
        val zdt = java.time.ZonedDateTime.now()
        return LocalDateTimeInfo(
            dayOfWeekIso = zdt.dayOfWeek.value,
            hour = zdt.hour,
            minute = zdt.minute,
            year = zdt.year,
            month = zdt.monthValue,
            day = zdt.dayOfMonth,
            utcOffsetMinutes = zdt.offset.totalSeconds / 60
        )
    }

    actual fun formatEpochMillisLocal(millis: Long): String {
        if (millis <= 0L) return ""
        return runCatching {
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .format(localDateFormatter)
        }.getOrDefault("")
    }

    actual fun setOpenTabListener(listener: ((Int) -> Unit)?) {
        openTabListener = listener
    }

    actual fun dispatchOpenTab(tab: Int) {
        openTabListener?.invoke(tab)
    }

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

    actual fun supportsInAppDocumentViewer(): Boolean = true

    actual fun supportsCustomDownloadFolder(): Boolean = true

    actual fun isUsingCustomDownloadFolder(): Boolean {
        val rawUri = getString(PREF_TREE_URI)?.takeIf { it.isNotBlank() } ?: return false
        val ctx = appContext ?: return false
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return false
        return ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }

    actual fun getDownloadFolderLabel(): String {
        val rawUri = getString(PREF_TREE_URI)?.takeIf { it.isNotBlank() } ?: return DEFAULT_DOWNLOAD_LABEL
        val savedLabel = getString(PREF_FOLDER_LABEL)?.takeIf { it.isNotBlank() }
        val ctx = appContext ?: return savedLabel ?: DEFAULT_DOWNLOAD_LABEL
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return DEFAULT_DOWNLOAD_LABEL
        val hasPerm = ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return if (hasPerm) (savedLabel ?: deriveTreeFolderLabel(ctx, uri)) else DEFAULT_DOWNLOAD_LABEL
    }

    actual suspend fun pickDownloadFolder(): String? {
        val trigger = folderPickerTrigger ?: return null
        val act = activityRef?.get() ?: return null
        return suspendCancellableCoroutine { cont ->
            pendingFolderContinuation?.cancel()
            pendingFolderContinuation = cont
            cont.invokeOnCancellation {
                if (pendingFolderContinuation === cont) {
                    pendingFolderContinuation = null
                }
            }
            act.runOnUiThread {
                runCatching { trigger() }.onFailure {
                    if (pendingFolderContinuation === cont) {
                        pendingFolderContinuation = null
                        cont.resume(null)
                    }
                }
            }
        }
    }

    actual fun clearDownloadFolder() {
        val ctx = appContext
        val rawUri = getString(PREF_TREE_URI)
        if (ctx != null && !rawUri.isNullOrBlank()) {
            runCatching {
                val uri = Uri.parse(rawUri)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                ctx.contentResolver.releasePersistableUriPermission(uri, flags)
            }
        }
        remove(PREF_TREE_URI)
        remove(PREF_FOLDER_LABEL)
    }

    actual fun triggerBrowserDownload(url: String, fileName: String) {
        openExternalUrl(url)
    }

    actual suspend fun saveDownloadedFile(
        courseName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext Result.failure(IllegalStateException("Contexto Android indisponível."))
        val safeCourse = sanitizeFolderSegment(courseName, fallback = "Sala Virtual")
        val cleanMime = mimeType.substringBefore(';').trim().ifBlank { inferMimeType(fileName, bytes) }
        val safeFile = ensureFileExtension(sanitizeFileNameSegment(fileName, fallback = "arquivo"), cleanMime, bytes)

        val customUriStr = getString(PREF_TREE_URI)?.takeIf { it.isNotBlank() }
        if (customUriStr != null) {
            val treeUri = runCatching { Uri.parse(customUriStr) }.getOrNull()
            val hasPerm = treeUri != null &&
                ctx.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }
            if (treeUri != null && hasPerm) {
                return@withContext saveIntoSafTree(
                    resolver = ctx.contentResolver,
                    treeUri = treeUri,
                    folderLabel = getDownloadFolderLabel(),
                    courseFolder = safeCourse,
                    fileName = safeFile,
                    mimeType = cleanMime,
                    bytes = bytes
                )
            }
            return@withContext Result.failure(IllegalStateException("Permissão da pasta selecionada expirou. Escolha a pasta novamente na Central."))
        }

        saveIntoDefaultDownloads(ctx, safeCourse, safeFile, cleanMime, bytes)
    }

    private fun deriveTreeFolderLabel(ctx: Context, treeUri: Uri): String {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (!rootDocId.isNullOrBlank()) {
            val cleaned = rootDocId.substringAfter(':', rootDocId).trim('/')
            if (cleaned.isNotBlank()) return cleaned
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            runCatching {
                ctx.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            }
        }
        return "Pasta personalizada"
    }

    private fun saveIntoSafTree(
        resolver: ContentResolver,
        treeUri: Uri,
        folderLabel: String,
        courseFolder: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> = runCatching {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val courseDocId = findOrCreateSafSubfolder(resolver, treeUri, rootDocId, courseFolder)
            ?: throw IllegalStateException("Não foi possível criar a pasta '$courseFolder'.")
        val existingNames = listSafChildNames(resolver, treeUri, courseDocId)
        val uniqueName = makeUniqueFileName(fileName, existingNames)
        val courseDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, courseDocId)
        val fileUri = DocumentsContract.createDocument(resolver, courseDocUri, mimeType, uniqueName)
            ?: throw IllegalStateException("Não foi possível criar o arquivo '$uniqueName'.")
        resolver.openOutputStream(fileUri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IllegalStateException("Não foi possível gravar em '$uniqueName'.")
        "$folderLabel/$courseFolder/$uniqueName"
    }

    private fun findOrCreateSafSubfolder(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        folderName: String
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else ""
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                        name.equals(folderName, ignoreCase = true) &&
                        !docId.isNullOrBlank()
                    ) {
                        return docId
                    }
                }
            }
        }
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val created = DocumentsContract.createDocument(
            resolver,
            parentDocUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            folderName
        ) ?: return null
        return DocumentsContract.getDocumentId(created)
    }

    private fun listSafChildNames(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String
    ): Set<String> {
        val names = mutableSetOf<String>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (idx >= 0) {
                        cursor.getString(idx)?.let { names.add(it.lowercase()) }
                    }
                }
            }
        }
        return names
    }

    private fun saveIntoDefaultDownloads(
        ctx: Context,
        courseFolder: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relPath = "${Environment.DIRECTORY_DOWNLOADS}/UnicepPortal/$courseFolder"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Falha ao criar arquivo em Downloads.")
            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw IllegalStateException("Falha ao gravar arquivo em Downloads.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            } catch (e: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw e
            }
            "Downloads/UnicepPortal/$courseFolder/$fileName"
        } else {
            val canWritePublic = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val baseDir = if (canWritePublic) {
                @Suppress("DEPRECATION")
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
            }
            val dir = File(baseDir, "UnicepPortal/$courseFolder").apply { mkdirs() }
            val existing = dir.list()?.map { it.lowercase() }?.toSet().orEmpty()
            val unique = makeUniqueFileName(fileName, existing)
            val target = File(dir, unique)
            FileOutputStream(target).use { out ->
                out.write(bytes)
                out.flush()
            }
            if (canWritePublic) {
                "Downloads/UnicepPortal/$courseFolder/$unique"
            } else {
                "Arquivos do app/Download/UnicepPortal/$courseFolder/$unique"
            }
        }
    }

    private fun sanitizeFolderSegment(raw: String, fallback: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(64)
            .trim()
        return cleaned.ifBlank { fallback }
    }

    private fun sanitizeFileNameSegment(raw: String, fallback: String): String {
        val cleaned = raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(120)
            .trim()
        return cleaned.ifBlank { fallback }
    }

    private fun inferMimeType(fileName: String, bytes: ByteArray): String {
        if (bytes.size >= 4 &&
            bytes[0] == 0x25.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() &&
            bytes[3] == 0x46.toByte()
        ) {
            return "application/pdf"
        }
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun ensureFileExtension(name: String, mimeType: String, bytes: ByteArray): String {
        if (name.substringAfterLast('/', name).contains('.')) return name
        val effectiveMime = if (mimeType == "application/octet-stream") inferMimeType(name, bytes) else mimeType
        val ext = when {
            effectiveMime.contains("pdf") -> ".pdf"
            effectiveMime.contains("html") -> ".html"
            effectiveMime.contains("png") -> ".png"
            effectiveMime.contains("jpeg") -> ".jpg"
            effectiveMime.contains("webp") -> ".webp"
            effectiveMime.contains("wordprocessingml") -> ".docx"
            effectiveMime.contains("spreadsheetml") -> ".xlsx"
            effectiveMime.contains("presentationml") -> ".pptx"
            effectiveMime.contains("zip") -> ".zip"
            effectiveMime.startsWith("text/") -> ".txt"
            else -> ""
        }
        return name + ext
    }

    private fun makeUniqueFileName(desired: String, existingLower: Set<String>): String {
        if (!existingLower.contains(desired.lowercase())) return desired
        val dot = desired.lastIndexOf('.')
        val stem = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        for (i in 1..99) {
            val candidate = "$stem ($i)$ext"
            if (!existingLower.contains(candidate.lowercase())) return candidate
        }
        return "${stem}_${System.currentTimeMillis()}$ext"
    }
}
