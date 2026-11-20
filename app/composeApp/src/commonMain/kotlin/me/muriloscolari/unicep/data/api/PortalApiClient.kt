package me.muriloscolari.unicep.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.platform.PlatformBridge

class UnauthorizedException(message: String = "Sessão expirada") : Exception(message)
class ApiException(
    message: String,
    val code: String = "",
    val statusCode: Int = 0
) : Exception(message)

class DownloadedFilePayload(
    val bytes: ByteArray,
    val contentType: String,
    val suggestedFileName: String
)

class PortalApiClient(
    private val connectTimeoutMs: Long = 15_000,
    private val requestTimeoutMs: Long = 90_000,
    private val socketTimeoutMs: Long = 90_000
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(PlatformBridge.createHttpEngine()) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = connectTimeoutMs
            requestTimeoutMillis = requestTimeoutMs
            socketTimeoutMillis = socketTimeoutMs
        }
    }

    fun close() {
        client.close()
    }

    suspend fun login(baseUrl: String, req: LoginRequest): LoginResponse {
        val resp = client.post("${cleanBase(baseUrl)}/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }
        return parseOrThrow(resp)
    }

    suspend fun dashboard(baseUrl: String, token: String): DashboardResponse =
        getAuthed(baseUrl, "/api/dashboard", token)

    suspend fun grades(baseUrl: String, token: String, periodId: String): GradesResponse {
        val resp = client.get("${cleanBase(baseUrl)}/api/grades") {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (periodId.isNotBlank()) parameter("period", periodId)
        }
        return parseOrThrow(resp)
    }

    suspend fun report(baseUrl: String, token: String, periodQuery: String): ReportCardResponse {
        val resp = client.get("${cleanBase(baseUrl)}/api/report") {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (periodQuery.isNotBlank()) parameter("period", periodQuery)
        }
        return parseOrThrow(resp)
    }

    suspend fun curriculum(baseUrl: String, token: String): CurriculumResponse =
        getAuthed(baseUrl, "/api/curriculum", token)

    suspend fun schedule(baseUrl: String, token: String): ScheduleResponse =
        getAuthed(baseUrl, "/api/schedule", token)

    suspend fun finance(baseUrl: String, token: String): FinanceResponse =
        getAuthed(baseUrl, "/api/finance", token)

    suspend fun services(baseUrl: String, token: String): ServicesResponse =
        getAuthed(baseUrl, "/api/services", token)

    suspend fun avaSso(baseUrl: String, token: String): AvaSsoResponse {
        val resp = client.post("${cleanBase(baseUrl)}/api/services/ava-sso") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return parseOrThrow(resp)
    }

    suspend fun notifications(baseUrl: String, token: String, unreadOnly: Boolean = false): NotificationsResponse {
        val resp = client.get("${cleanBase(baseUrl)}/api/notifications") {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (unreadOnly) parameter("unread", "true")
        }
        return parseOrThrow(resp)
    }

    suspend fun markNotificationsRead(baseUrl: String, token: String, ids: List<Long> = emptyList()) {
        val resp = client.post("${cleanBase(baseUrl)}/api/notifications/read") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(MarkReadRequest(ids))
        }
        if (resp.status == HttpStatusCode.Unauthorized) throw UnauthorizedException()
        if (resp.status.value >= 400) throw ApiException("Falha ao marcar notificações")
    }

    suspend fun syncNow(baseUrl: String, token: String): SyncResponse {
        val resp = client.post("${cleanBase(baseUrl)}/api/notifications/sync") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return parseOrThrow(resp)
    }

    suspend fun testNotification(baseUrl: String, token: String) {
        val resp = client.post("${cleanBase(baseUrl)}/api/notifications/test") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (resp.status == HttpStatusCode.Unauthorized) throw UnauthorizedException()
    }

    suspend fun logout(baseUrl: String, token: String) {
        client.post("${cleanBase(baseUrl)}/api/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun createProxyTicket(baseUrl: String, token: String, path: String): ProxyTicketResponse {
        val resp = client.post("${cleanBase(baseUrl)}/api/proxy/ticket") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ProxyTicketRequest(path))
        }
        return parseOrThrow(resp)
    }

    fun buildProxyUrlWithTicket(baseUrl: String, ticket: String, path: String): String =
        "${cleanBase(baseUrl)}/api/proxy?path=${path.encodeURLParameter()}&ticket=${ticket.encodeURLParameter()}"

    suspend fun virtualOverview(baseUrl: String, token: String, forceRefresh: Boolean = false): VirtualOverviewResponse {
        val resp = client.get("${cleanBase(baseUrl)}/api/d2l/overview") {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (forceRefresh) parameter("refresh", "1")
        }
        return parseOrThrow(resp)
    }

    suspend fun virtualCourseDetail(baseUrl: String, token: String, orgUnitId: Long): VirtualCourseDetailResponse =
        getAuthed(baseUrl, "/api/d2l/courses/$orgUnitId", token)

    suspend fun getD2LSettings(baseUrl: String, token: String): VirtualSettingsPayload =
        getAuthed(baseUrl, "/api/d2l/settings", token)

    suspend fun updateD2LSettings(baseUrl: String, token: String, pollingEnabled: Boolean): VirtualSettingsPayload {
        val resp = client.patch("${cleanBase(baseUrl)}/api/d2l/settings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(VirtualSettingsPayload(pollingEnabled = pollingEnabled))
        }
        return parseOrThrow(resp)
    }

    suspend fun calendarEvents(baseUrl: String, token: String): CalendarEventsResponse =
        getAuthed(baseUrl, "/api/calendar/events", token)

    suspend fun createCalendarEvent(baseUrl: String, token: String, input: CalendarEventInput): CalendarEventSingleResponse {
        val resp = client.post("${cleanBase(baseUrl)}/api/calendar/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(input)
        }
        return parseOrThrow(resp)
    }

    suspend fun updateCalendarEvent(baseUrl: String, token: String, id: Long, input: CalendarEventInput): CalendarEventSingleResponse {
        val resp = client.put("${cleanBase(baseUrl)}/api/calendar/events/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(input)
        }
        return parseOrThrow(resp)
    }

    suspend fun deleteCalendarEvent(baseUrl: String, token: String, id: Long) {
        val resp = client.delete("${cleanBase(baseUrl)}/api/calendar/events/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (resp.status == HttpStatusCode.Unauthorized) throw UnauthorizedException()
        if (resp.status.value >= 400) {
            val errBody = runCatching { resp.body<ErrorResponse>() }.getOrNull()
            throw ApiException(
                message = errBody?.error ?: "Erro ao excluir evento (HTTP ${resp.status.value})",
                code = errBody?.code.orEmpty(),
                statusCode = resp.status.value
            )
        }
    }

    suspend fun createD2LTicket(baseUrl: String, token: String, path: String): ProxyTicketResponse {
        val resp = client.post("${cleanBase(baseUrl)}/api/d2l/ticket") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ProxyTicketRequest(path))
        }
        return parseOrThrow(resp)
    }

    fun buildD2LFileUrlWithTicket(
        baseUrl: String,
        ticket: String,
        path: String,
        download: Boolean = false,
        fileName: String = ""
    ): String {
        val base = "${cleanBase(baseUrl)}/api/d2l/file?path=${path.encodeURLParameter()}&ticket=${ticket.encodeURLParameter()}"
        if (!download) return base
        val namePart = fileName.trim().takeIf { it.isNotEmpty() }?.let { "&name=${it.encodeURLParameter()}" } ?: ""
        return "$base&download=1$namePart"
    }

    suspend fun downloadFileBytes(url: String): DownloadedFilePayload {
        val resp = client.get(url) {
            timeout {
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }
        }
        if (resp.status == HttpStatusCode.Unauthorized) {
            throw UnauthorizedException()
        }
        if (resp.status.value >= 400) {
            val errBody = runCatching { resp.body<ErrorResponse>() }.getOrNull()
            throw ApiException(
                message = errBody?.error ?: "Erro ao baixar arquivo (HTTP ${resp.status.value})",
                code = errBody?.code.orEmpty(),
                statusCode = resp.status.value
            )
        }
        val bytes = resp.body<ByteArray>()
        val contentType = resp.headers[HttpHeaders.ContentType]
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        val disposition = resp.headers[HttpHeaders.ContentDisposition]
        return DownloadedFilePayload(
            bytes = bytes,
            contentType = contentType,
            suggestedFileName = parseDispositionFilename(disposition)
        )
    }

    private fun parseDispositionFilename(disposition: String?): String {
        if (disposition.isNullOrBlank()) return ""
        val utf8Match = Regex("""filename\*\s*=\s*UTF-8''([^;\r\n]+)""", RegexOption.IGNORE_CASE)
            .find(disposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!utf8Match.isNullOrBlank()) {
            return decodePercentUtf8(utf8Match)
        }
        val quoted = Regex("""filename\s*=\s*"([^"\r\n]+)"""", RegexOption.IGNORE_CASE)
            .find(disposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!quoted.isNullOrBlank()) return quoted
        return Regex("""filename\s*=\s*([^;\r\n]+)""", RegexOption.IGNORE_CASE)
            .find(disposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
            .orEmpty()
    }

    private fun decodePercentUtf8(input: String): String {
        val bytes = ArrayList<Byte>(input.length)
        var i = 0
        while (i < input.length) {
            if (input[i] == '%' && i + 2 < input.length) {
                val hex = input.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.add(hex.toByte())
                    i += 3
                    continue
                }
            }
            bytes.addAll(input[i].toString().encodeToByteArray().toList())
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private suspend inline fun <reified T> getAuthed(baseUrl: String, path: String, token: String): T {
        val resp = client.get("${cleanBase(baseUrl)}$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return parseOrThrow(resp)
    }

    private suspend inline fun <reified T> parseOrThrow(resp: HttpResponse): T {
        if (resp.status == HttpStatusCode.Unauthorized) {
            throw UnauthorizedException()
        }
        if (resp.status.value >= 400) {
            val errBody = runCatching { resp.body<ErrorResponse>() }.getOrNull()
            throw ApiException(
                message = errBody?.error ?: "Erro HTTP ${resp.status.value}",
                code = errBody?.code.orEmpty(),
                statusCode = resp.status.value
            )
        }
        return resp.body()
    }

    private fun cleanBase(url: String): String = url.trim().trimEnd('/')
}
