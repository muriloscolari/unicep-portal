package me.muriloscolari.unicep.platform

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
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
            fileName.endsWith(".webp", ignoreCase = true)
    }
    val isHtml = remember(mimeType, fileName, bytes) {
        mimeType.contains("html", ignoreCase = true) ||
            fileName.endsWith(".html", ignoreCase = true) ||
            fileName.endsWith(".htm", ignoreCase = true) ||
            bytes.looksLikeHtml()
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
                        val imageBitmap = remember(bytes) {
                            runCatching {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (imageBitmap != null) {
                            ZoomableImageView(imageBitmap = imageBitmap, fileName = fileName)
                        } else {
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
                    }
                    isHtml && bytes != null && bytes.isNotEmpty() -> {
                        val html = remember(bytes) { bytes.decodeToString() }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    webViewClient = WebViewClient()
                                    loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                                }
                            }
                        )
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
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    webViewClient = WebViewClient()
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

private class PdfRenderSession(
    private val tempFile: File,
    private val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer
) {
    val mutex = Mutex()
    val pageCount: Int = renderer.pageCount

    fun close() {
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
    val density = LocalDensity.current
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
        session?.close()
        session = loaded.getOrNull()
        renderError = loaded.exceptionOrNull()?.message
    }

    DisposableEffect(Unit) {
        onDispose {
            session?.close()
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

    val renderTargetWidthPx = remember(containerSize.width, density) {
        val w = if (containerSize.width > 0) containerSize.width else 1080
        w.coerceIn(720, 1800)
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
                runCatching {
                    session.renderer.openPage(pageIndex).use { page ->
                        val aspect = page.width.toFloat() / page.height.coerceAtLeast(1).toFloat()
                        val w = targetWidthPx.coerceAtLeast(320)
                        val h = (w / aspect).toInt().coerceAtLeast(1)
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
                text = "Tamanho: $sizeLabel • Este formato não possui pré-visualização nativa embutida.",
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
                Text("Baixar na pasta configurada")
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

private fun openTempFileExternally(
    ctx: android.content.Context,
    fileName: String,
    mimeType: String,
    bytes: ByteArray
) {
    runCatching {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "arquivo" }
        val file = File(dir, safeName)
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, fileName))
    }
}
