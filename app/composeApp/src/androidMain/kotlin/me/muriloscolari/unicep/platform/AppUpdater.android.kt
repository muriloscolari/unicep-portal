package me.muriloscolari.unicep.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val FORGEJO_LATEST_RELEASE_URL =
    "https://git.muriloscolari.me/api/v1/repos/murilo/unicep-portal/releases/latest"
private const val FORGEJO_ALLOWED_PREFIX = "https://git.muriloscolari.me/"

@Serializable
private data class ForgejoReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ForgejoAssetDto> = emptyList()
)

@Serializable
private data class ForgejoAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L
)

internal object AppUpdater {
    private val json = Json { ignoreUnknownKeys = true }

    fun currentVersion(context: Context): String {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "1.2.0"
        }.getOrDefault("1.2.0")
    }

    suspend fun checkLatest(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(FORGEJO_LATEST_RELEASE_URL).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<ForgejoReleaseDto>(body)
                if (release.draft || release.prerelease) return@withContext null

                val current = normalizeVersion(currentVersion(context))
                val latest = normalizeVersion(release.tagName)
                if (!isNewerVersion(latest, current)) return@withContext null

                val apkAsset = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true) &&
                        it.browserDownloadUrl.startsWith(FORGEJO_ALLOWED_PREFIX)
                } ?: return@withContext null

                AppUpdateInfo(
                    currentVersion = current,
                    latestVersion = latest,
                    releaseName = release.name.ifBlank { "v$latest" },
                    releaseNotes = release.body.trim(),
                    apkUrl = apkAsset.browserDownloadUrl,
                    apkSizeBytes = apkAsset.size,
                    htmlUrl = release.htmlUrl
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(apkUrl.startsWith(FORGEJO_ALLOWED_PREFIX)) {
                "URL de atualização não autorizada."
            }

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "unicep-portal-update.apk")
            val tmpFile = File(updatesDir, "unicep-portal-update.apk.tmp")

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            try {
                if (conn.responseCode !in 200..299) {
                    error("Falha ao baixar APK (HTTP ${conn.responseCode}).")
                }
                val finalUrl = conn.url.toString()
                require(finalUrl.startsWith(FORGEJO_ALLOWED_PREFIX)) {
                    "Redirecionamento externo bloqueado."
                }
                val total = conn.contentLengthLong.coerceAtLeast(0L)
                var downloaded = 0L
                val buf = ByteArray(16 * 1024)
                conn.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (apkFile.exists()) apkFile.delete()
                if (!tmpFile.renameTo(apkFile)) {
                    tmpFile.copyTo(apkFile, overwrite = true)
                    tmpFile.delete()
                }
                onProgress(1f)
            } finally {
                conn.disconnect()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                error("Permita instalar apps desta fonte nas Configurações e toque em Atualizar novamente.")
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }
    }

    private fun normalizeVersion(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .trim()
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }
}
