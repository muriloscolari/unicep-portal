package me.muriloscolari.unicep.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
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
class ApiException(message: String) : Exception(message)

class PortalApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(PlatformBridge.createHttpEngine()) {
        install(ContentNegotiation) {
            json(json)
        }
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
            val msg = runCatching { resp.body<ErrorResponse>().error }.getOrNull()
                ?: "Erro HTTP ${resp.status.value}"
            throw ApiException(msg)
        }
        return resp.body()
    }

    private fun cleanBase(url: String): String = url.trim().trimEnd('/')
}
