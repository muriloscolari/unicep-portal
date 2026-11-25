package me.muriloscolari.unicep.platform

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.muriloscolari.unicep.ui.designsystem.CopyTextButton
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.util.isPlainTextDocumentFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

private fun ByteArray?.isPdfSignature(): Boolean {
    if (this == null || size < 4) return false
    return this[0] == 0x25.toByte() &&
        this[1] == 0x50.toByte() &&
        this[2] == 0x44.toByte() &&
        this[3] == 0x46.toByte()
}

private fun ByteArray?.looksLikeHtml(): Boolean {
    if (this == null || isEmpty()) return false
    val head = copyOfRange(0, size.coerceAtMost(512)).decodeToString().trimStart().lowercase()
    return head.startsWith("<!doctype html") || head.startsWith("<html") || head.startsWith("<head") || head.startsWith("<body")
}

private fun ByteArray.containsZeroByte(): Boolean {
    val limit = minOf(size, 4096)
    for (i in 0 until limit) {
        if (this[i] == 0.toByte()) return true
    }
    return false
}

@Composable
actual fun PlatformDocumentViewer(
    title: String,
    courseName: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray?,
    fallbackUrl: String,
    downloading: Boolean,
    onDownload: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val ctx = LocalContext.current
    val isPdf = remember(mimeType, fileName, bytes) {
        mimeType.contains("pdf", ignoreCase = true) ||
            fileName.endsWith(".pdf", ignoreCase = true) ||
            bytes.isPdfSignature()
    }
    val isImage = remember(mimeType, fileName) {
        mimeType.startsWith("image/", ignoreCase = true) ||
            fileName.endsWith(".png", ignoreCase = true) ||
            fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) ||
            fileName.endsWith(".webp", ignoreCase = true) ||
            fileName.endsWith(".gif", ignoreCase = true) ||
            fileName.endsWith(".bmp", ignoreCase = true)
    }
    val isHtml = remember(mimeType, fileName, bytes) {
        mimeType.contains("html", ignoreCase = true) ||
            fileName.endsWith(".html", ignoreCase = true) ||
            fileName.endsWith(".htm", ignoreCase = true) ||
            bytes.looksLikeHtml()
    }
    val isPlainText = remember(mimeType, fileName, bytes, isPdf, isImage, isHtml) {
        !isPdf && !isImage && !isHtml &&
            bytes != null && bytes.isNotEmpty() && bytes.size <= 2 * 1024 * 1024 &&
            (mimeType.startsWith("text/", ignoreCase = true) || isPlainTextDocumentFile(fileName)) &&
            !bytes.containsZeroByte()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(
                color = colors.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceSecondary)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = PortalIcons.Close,
                            contentDescription = "Fechar visualizador",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.ifBlank { fileName },
                            style = appleType.headline,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$courseName • $fileName",
                            style = appleType.caption1,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onDownload,
                        enabled = !downloading,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = PortalIcons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (downloading) "Salvando..." else "Baixar",
                            style = appleType.footnote.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            // Content body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                when {
                    isPdf && bytes != null && bytes.isNotEmpty() -> {
                        NativePdfRendererView(bytes = bytes)
                    }
                    isImage && bytes != null && bytes.isNotEmpty() -> {
                        val decodedImageState by produceState<Pair<Boolean, ImageBitmap?>>(
                            initialValue = true to null,
                            key1 = bytes
                        ) {
                            val bmp = withContext(Dispatchers.IO) {
                                decodeScaledBitmap(bytes, maxDim = 2048)?.asImageBitmap()
                            }
                            value = false to bmp
                        }
                        val (decoding, imageBitmap) = decodedImageState
                        when {
                            decoding -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.accent)
                            }
                            imageBitmap != null -> ZoomableImageView(imageBitmap = imageBitmap, fileName = fileName)
                            else -> UnsupportedDocumentFallback(
                                fileName = fileName,
                                sizeBytes = bytes.size,
                                downloading = downloading,
                                onDownload = onDownload,
                                onOpenExternal = {
                                    openTempFileExternally(ctx, fileName, mimeType, bytes)
                                }
                            )
                        }
                    }
                    isHtml && bytes != null && bytes.isNotEmpty() -> {
                        val safeHtml = remember(bytes) {
                            injectHtmlCsp(bytes.decodeToString())
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    configureSandboxedWebView(allowSubresourcesFrom = null)
                                    loadDataWithBaseURL("about:blank", safeHtml, "text/html", "utf-8", null)
                                }
                            }
                        )
                    }
                    isPlainText && bytes != null -> {
                        val textContent = remember(bytes) {
                            bytes.decodeToString().replace("\r\n", "\n").replace("\r", "\n")
                        }
                        SelectableTextDocumentView(text = textContent)
                    }
                    bytes != null && bytes.isNotEmpty() -> {
                        UnsupportedDocumentFallback(
                            fileName = fileName,
                            sizeBytes = bytes.size,
                            downloading = downloading,
                            onDownload = onDownload,
                            onOpenExternal = {
                                openTempFileExternally(ctx, fileName, mimeType, bytes)
                            }
                        )
                    }
                    fallbackUrl.isNotBlank() -> {
                        val allowedUrl = remember(fallbackUrl) { Uri.parse(fallbackUrl) }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    configureSandboxedWebView(allowSubresourcesFrom = allowedUrl)
                                    loadUrl(fallbackUrl)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun injectHtmlCsp(rawHtml: String): String {
    val cspMeta = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:;\">"
    val headMatch = Regex("(?i)<head[^>]*>").find(rawHtml)
    return if (headMatch != null) {
        val insertAt = headMatch.range.last + 1
        rawHtml.substring(0, insertAt) + cspMeta + rawHtml.substring(insertAt)
    } else {
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\">$cspMeta</head><body>$rawHtml</body></html>"
    }
}

private fun WebView.configureSandboxedWebView(allowSubresourcesFrom: Uri?) {
    settings.javaScriptEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return true
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val reqUri = request?.url ?: return null
            val scheme = reqUri.scheme?.lowercase().orEmpty()
            if (scheme == "about" || scheme == "data") return null
            if (allowSubresourcesFrom != null &&
                reqUri.scheme.equals(allowSubresourcesFrom.scheme, ignoreCase = true) &&
                reqUri.host.equals(allowSubresourcesFrom.host, ignoreCase = true) &&
                reqUri.port == allowSubresourcesFrom.port &&
                reqUri.path == allowSubresourcesFrom.path
            ) {
                return null
            }
            return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
    }
}

private fun decodeScaledBitmap(bytes: ByteArray, maxDim: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val outW = bounds.outWidth
        val outH = bounds.outHeight
        if (outW <= 0 || outH <= 0) return null
        var sampleSize = 1
        while (outW / sampleSize > maxDim || outH / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()
}

private fun pruneStaleCacheFiles(dir: File, maxAgeMillis: Long = 3_600_000L) {
    runCatching {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff) {
                f.delete()
            }
        }
    }
}

private class PdfRenderSession(
    private val tempFile: File,
    private val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer
) {
    val mutex = Mutex()
    val pageCount: Int = renderer.pageCount

    @Volatile
    var closed: Boolean = false
        private set

    fun closeAsync() {
        if (closed) return
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    runCatching { renderer.close() }
                    runCatching { pfd.close() }
                    runCatching { tempFile.delete() }
                }
            }
        }
    }

    fun closeNow() {
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        runCatching { tempFile.delete() }
    }
}

@Composable
private fun NativePdfRendererView(bytes: ByteArray) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val ctx = LocalContext.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()

    var session by remember { mutableStateOf<PdfRenderSession?>(null) }
    var renderError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bytes) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val cacheDir = File(ctx.cacheDir, "pdf_preview").apply { mkdirs() }
                pruneStaleCacheFiles(cacheDir)
                val file = File.createTempFile("doc_", ".pdf", cacheDir)
                FileOutputStream(file).use { out ->
                    out.write(bytes)
                    out.flush()
                }
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                PdfRenderSession(file, pfd, renderer)
            }
        }
        val nextSession = loaded.getOrNull()
        if (!coroutineContext.isActive) {
            nextSession?.closeNow()
            return@LaunchedEffect
        }
        session?.closeAsync()
        session = nextSession
        renderError = loaded.exceptionOrNull()?.message
    }

    DisposableEffect(Unit) {
        onDispose {
            session?.closeAsync()
            session = null
        }
    }

    val currentSession = session
    if (renderError != null) {
        UnsupportedDocumentFallback(
            fileName = "Documento PDF",
            sizeBytes = bytes.size,
            downloading = false,
            onDownload = {},
            onOpenExternal = {
                openTempFileExternally(ctx, "documento.pdf", "application/pdf", bytes)
            }
        )
        return
    }
    if (currentSession == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent)
        }
        return
    }

    val currentPage by remember(listState, currentSession.pageCount) {
        derivedStateOf {
            (listState.firstVisibleItemIndex + 1).coerceIn(1, currentSession.pageCount.coerceAtLeast(1))
        }
    }

    val zoomBucket = when {
        scale >= 2.7f -> 3
        scale >= 1.6f -> 2
        else -> 1
    }
    val renderTargetWidthPx = remember(containerSize.width, zoomBucket) {
        val base = if (containerSize.width > 0) containerSize.width else 1080
        (base * zoomBucket).coerceIn(720, 3000)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E5EA).let { if (colors.isDark) Color(0xFF1C1C1E) else it })
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(containerSize) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.1f
                        }
                    }
                )
            }
            .pointerInput(containerSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2 || scale > 1.02f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                            val maxX = ((containerSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                            val maxY = ((containerSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                            val newOffset = if (newScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            }
                            if (pressedCount >= 2 || zoomChange != 1f || (scale > 1.02f && panChange != Offset.Zero)) {
                                scale = newScale
                                offset = newOffset
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = scale <= 1.05f,
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            items(
                count = currentSession.pageCount,
                key = { pageIdx -> pageIdx }
            ) { pageIndex ->
                PdfPageCard(
                    session = currentSession,
                    pageIndex = pageIndex,
                    targetWidthPx = renderTargetWidthPx
                )
            }
        }

        // Page indicator & zoom reset floating pill
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "Página $currentPage de ${currentSession.pageCount}",
                    style = appleType.caption1.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
            if (scale > 1.05f) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.accent,
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        scale = 1f
                        offset = Offset.Zero
                    }
                ) {
                    Text(
                        text = "${(scale * 100).toInt()}% • Ajustar",
                        style = appleType.caption1.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPageCard(
    session: PdfRenderSession,
    pageIndex: Int,
    targetWidthPx: Int
) {
    val pageState by produceState<Pair<ImageBitmap, Float>?>(
        initialValue = null,
        key1 = session,
        key2 = pageIndex,
        key3 = targetWidthPx
    ) {
        value = withContext(Dispatchers.IO) {
            session.mutex.withLock {
                if (session.closed) return@withLock null
                runCatching {
                    session.renderer.openPage(pageIndex).use { page ->
                        val aspect = (page.width.toFloat() / page.height.coerceAtLeast(1).toFloat()).coerceIn(0.2f, 5f)
                        val maxHeight = 4096
                        var w = targetWidthPx.coerceAtLeast(320)
                        var h = (w / aspect).toInt().coerceAtLeast(1)
                        if (h > maxHeight) {
                            h = maxHeight
                            w = (h * aspect).toInt().coerceAtLeast(320)
                        }
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp.asImageBitmap() to aspect
                    }
                }.getOrNull()
            }
        }
    }

    val rendered = pageState
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (rendered != null) Modifier.aspectRatio(rendered.second)
                else Modifier.height(420.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        if (rendered != null) {
            Image(
                bitmap = rendered.first,
                contentDescription = "Página ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ZoomableImageView(imageBitmap: ImageBitmap, fileName: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.2f
                        }
                    }
                )
            }
            .pointerInput(boxSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1.02f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                            val pan = event.calculatePan()
                            val maxX = ((boxSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                            val maxY = ((boxSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                            scale = newScale
                            offset = if (newScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun UnsupportedDocumentFallback(
    fileName: String,
    sizeBytes: Int,
    downloading: Boolean,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val sizeLabel = remember(sizeBytes) {
        when {
            sizeBytes >= 1024 * 1024 -> "${((sizeBytes / (1024.0 * 1024.0)) * 10).toInt() / 10.0} MB"
            sizeBytes >= 1024 -> "${sizeBytes / 1024} KB"
            else -> "$sizeBytes B"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = fileName,
                style = appleType.title3,
                color = colors.textPrimary
            )
            Text(
                text = "$sizeLabel • Não é possível visualizar este formato aqui.",
                style = appleType.footnote,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onDownload,
                enabled = !downloading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White)
            ) {
                Icon(PortalIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Baixar arquivo")
            }
            OutlinedButton(
                onClick = onOpenExternal,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Abrir com outro aplicativo", color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun SelectableTextDocumentView(text: String) {
    val colors = LocalPortalColors.current
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            CopyTextButton(
                textToCopy = text,
                label = "Copiar texto",
                copiedLabel = "Copiado"
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = colors.surface
        ) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = text,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

private fun resolveExternalMimeType(fileName: String, serverMimeType: String): String {
    val cleanServer = serverMimeType.substringBefore(';').trim().lowercase()
    if (cleanServer.isNotBlank() && cleanServer != "application/octet-stream") {
        return cleanServer
    }
    val cleanFile = fileName.trim().substringBefore('?').substringBefore('#').substringAfterLast('/')
    val dotIdx = cleanFile.lastIndexOf('.')
    val ext = if (dotIdx > 0 && dotIdx < cleanFile.lastIndex) cleanFile.substring(dotIdx + 1).lowercase() else ""
    if (ext.isNotBlank()) {
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mapped.isNullOrBlank()) return mapped
        val fallback = when (ext) {
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> null
        }
        if (fallback != null) return fallback
    }
    return "*/*"
}

private fun openTempFileExternally(
    ctx: android.content.Context,
    fileName: String,
    mimeType: String,
    bytes: ByteArray
) {
    runCatching {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        pruneStaleCacheFiles(dir)
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "arquivo" }
        val file = File(dir, safeName)
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val resolvedMime = resolveExternalMimeType(fileName, mimeType)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, fileName))
    }
}
