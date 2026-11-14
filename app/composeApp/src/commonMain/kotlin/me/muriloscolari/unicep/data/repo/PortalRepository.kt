package me.muriloscolari.unicep.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.muriloscolari.unicep.data.api.CurriculumResponse
import me.muriloscolari.unicep.data.api.DashboardResponse
import me.muriloscolari.unicep.data.api.FinanceResponse
import me.muriloscolari.unicep.data.api.GradesResponse
import me.muriloscolari.unicep.data.api.LoginRequest
import me.muriloscolari.unicep.data.api.NotificationItem
import me.muriloscolari.unicep.data.api.PortalApiClient
import me.muriloscolari.unicep.data.api.ReportCardResponse
import me.muriloscolari.unicep.data.api.ScheduleResponse
import me.muriloscolari.unicep.data.api.ServicesResponse
import me.muriloscolari.unicep.data.api.UnauthorizedException
import me.muriloscolari.unicep.platform.DesignStylePreference
import me.muriloscolari.unicep.platform.PlatformBridge

object PrefKeys {
    const val SERVER_URL = "server_url"
    const val JWT_TOKEN = "jwt_token"
    const val RA = "student_ra"
    const val PASSWORD = "student_password"
    const val PERFIL = "student_perfil"
    const val STYLE_PREF = "design_style_pref"
    const val LAST_NOTIFIED_ID = "last_notified_id"
}

data class PortalUiState(
    val initializing: Boolean = true,
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val serverUrl: String = PlatformBridge.capabilities().defaultServerUrl,
    val stylePreference: DesignStylePreference = DesignStylePreference.AUTO,
    val dashboard: DashboardResponse? = null,
    val selectedGradePeriodId: String = "",
    val grades: GradesResponse? = null,
    val selectedReportPeriodId: String = "",
    val report: ReportCardResponse? = null,
    val curriculum: CurriculumResponse? = null,
    val schedule: ScheduleResponse? = null,
    val finance: FinanceResponse? = null,
    val services: ServicesResponse? = null,
    val notifications: List<NotificationItem> = emptyList(),
    val statusBanner: String? = null
)

class PortalRepository(private val api: PortalApiClient = PortalApiClient()) {
    private val _state = MutableStateFlow(
        PortalUiState(
            serverUrl = PlatformBridge.getString(PrefKeys.SERVER_URL)
                ?.takeIf { it.isNotBlank() }
                ?: PlatformBridge.capabilities().defaultServerUrl,
            stylePreference = loadStylePreference()
        )
    )
    val state: StateFlow<PortalUiState> = _state.asStateFlow()

    private fun loadStylePreference(): DesignStylePreference {
        val raw = PlatformBridge.getString(PrefKeys.STYLE_PREF)
        return DesignStylePreference.entries.firstOrNull { it.name == raw } ?: DesignStylePreference.AUTO
    }

    fun setStylePreference(pref: DesignStylePreference) {
        PlatformBridge.putString(PrefKeys.STYLE_PREF, pref.name)
        _state.update { it.copy(stylePreference = pref) }
    }

    fun clearBanner() {
        _state.update { it.copy(statusBanner = null, error = null) }
    }

    suspend fun tryAutoLogin() {
        val server = _state.value.serverUrl
        val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
        val ra = PlatformBridge.getString(PrefKeys.RA)
        val pass = PlatformBridge.getString(PrefKeys.PASSWORD)
        val perfil = PlatformBridge.getString(PrefKeys.PERFIL) ?: "alunograd"

        if (!token.isNullOrBlank()) {
            val loaded = runCatching { loadAllInitial(server, token) }
            if (loaded.isSuccess) {
                PlatformBridge.scheduleGradeSync()
                return
            }
            if (loaded.exceptionOrNull() !is UnauthorizedException) {
                _state.update {
                    it.copy(
                        initializing = false,
                        loggedIn = false,
                        error = loaded.exceptionOrNull()?.message
                    )
                }
                return
            }
        }

        if (!ra.isNullOrBlank() && !pass.isNullOrBlank()) {
            login(server, ra, pass, perfil, isAuto = true)
        } else {
            _state.update { it.copy(initializing = false, loggedIn = false) }
        }
    }

    suspend fun login(
        serverUrl: String,
        ra: String,
        password: String,
        perfil: String,
        isAuto: Boolean = false
    ) {
        val cleanUrl = serverUrl.trim().trimEnd('/').ifBlank { PlatformBridge.capabilities().defaultServerUrl }
        _state.update { it.copy(loading = true, error = null, serverUrl = cleanUrl) }
        runCatching {
            val resp = api.login(cleanUrl, LoginRequest(ra = ra.trim(), password = password, perfil = perfil))
            PlatformBridge.putString(PrefKeys.SERVER_URL, cleanUrl)
            PlatformBridge.putString(PrefKeys.JWT_TOKEN, resp.token)
            PlatformBridge.putString(PrefKeys.RA, ra.trim())
            PlatformBridge.putString(PrefKeys.PERFIL, perfil)
            if (!PlatformBridge.capabilities().isWeb) {
                // On Android, keep encrypted credentials in Keystore for silent auto-login.
                PlatformBridge.putString(PrefKeys.PASSWORD, password)
            }
            PlatformBridge.requestNotificationPermissionIfNeeded()
            PlatformBridge.scheduleGradeSync()
            loadAllInitial(cleanUrl, resp.token)
        }.onFailure { err ->
            _state.update {
                it.copy(
                    initializing = false,
                    loading = false,
                    loggedIn = false,
                    error = if (isAuto) null else (err.message ?: "Erro ao conectar")
                )
            }
        }
    }

    fun logout() {
        PlatformBridge.remove(PrefKeys.JWT_TOKEN)
        PlatformBridge.remove(PrefKeys.PASSWORD)
        PlatformBridge.cancelGradeSync()
        _state.update {
            PortalUiState(
                initializing = false,
                loggedIn = false,
                serverUrl = it.serverUrl,
                stylePreference = it.stylePreference
            )
        }
    }

    private suspend fun loadAllInitial(serverUrl: String, token: String) {
        val dash = api.dashboard(serverUrl, token)
        val notifs = runCatching { api.notifications(serverUrl, token).items }.getOrDefault(dash.unreadNotifications)
        val gradeId = dash.gradePeriods.firstOrNull()?.id.orEmpty()
        val reportId = findMatchingReportPeriodId(gradeId, dash.reportPeriods)
            ?: dash.reportPeriods.firstOrNull()?.id.orEmpty()
        _state.update {
            it.copy(
                initializing = false,
                loading = false,
                loggedIn = true,
                error = null,
                dashboard = dash,
                selectedGradePeriodId = gradeId,
                grades = GradesResponse(period = gradeId, items = dash.currentGrades),
                selectedReportPeriodId = reportId,
                report = dash.currentReport,
                notifications = notifs
            )
        }
        notifyNewUnread(notifs)
    }

    suspend fun refreshDashboard() = withToken { server, token ->
        loadAllInitial(server, token)
    }

    suspend fun selectSemesterPeriod(gradePeriodId: String) = withToken { server, token ->
        val reportPeriods = _state.value.dashboard?.reportPeriods.orEmpty()
        val reportPeriodId = findMatchingReportPeriodId(gradePeriodId, reportPeriods)
        _state.update {
            it.copy(
                loading = true,
                selectedGradePeriodId = gradePeriodId,
                selectedReportPeriodId = reportPeriodId ?: it.selectedReportPeriodId
            )
        }
        val gradesRes = api.grades(server, token, gradePeriodId)
        val reportRes = if (!reportPeriodId.isNullOrBlank()) {
            runCatching { api.report(server, token, reportPeriodId) }.getOrNull()
        } else null
        _state.update {
            it.copy(
                loading = false,
                grades = gradesRes,
                report = reportRes ?: it.report
            )
        }
    }

    private fun findMatchingReportPeriodId(
        gradePeriodId: String,
        reportPeriods: List<me.muriloscolari.unicep.data.api.PeriodOption>
    ): String? {
        // gradePeriodId format: "<codigoMatricula>@@<sem>@@<ano>"
        val parts = gradePeriodId.split("@@")
        if (parts.size == 3) {
            val sem = parts[1]
            val ano = parts[2]
            reportPeriods.firstOrNull {
                it.id.contains("ano=$ano") && it.id.contains("sem=$sem")
            }?.let { return it.id }
        }
        return reportPeriods.firstOrNull()?.id
    }

    suspend fun loadAcademicIfNeeded(force: Boolean = false) = withToken { server, token ->
        if (!force && _state.value.curriculum != null && _state.value.schedule != null) return@withToken
        _state.update { it.copy(loading = true) }
        val curr = runCatching { api.curriculum(server, token) }.getOrNull()
        val sched = runCatching { api.schedule(server, token) }.getOrNull()
        _state.update { it.copy(loading = false, curriculum = curr ?: it.curriculum, schedule = sched ?: it.schedule) }
    }

    suspend fun loadFinanceAndServicesIfNeeded(force: Boolean = false) = withToken { server, token ->
        if (!force && _state.value.finance != null && _state.value.services != null) return@withToken
        _state.update { it.copy(loading = true) }
        val fin = runCatching { api.finance(server, token) }.getOrNull()
        val srv = runCatching { api.services(server, token) }.getOrNull()
        val notifs = runCatching { api.notifications(server, token).items }.getOrNull()
        _state.update {
            it.copy(
                loading = false,
                finance = fin ?: it.finance,
                services = srv ?: it.services,
                notifications = notifs ?: it.notifications
            )
        }
    }

    suspend fun openAvaSso() = withToken { server, token ->
        val res = api.avaSso(server, token)
        PlatformBridge.openExternalUrl(res.url)
    }

    fun openPortalLink(path: String, proxy: Boolean) {
        val server = _state.value.serverUrl
        val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN).orEmpty()
        val target = if (proxy) api.buildProxyUrl(server, token, path) else path
        PlatformBridge.openExternalUrl(target)
    }

    suspend fun syncNotificationsNow() = withToken { server, token ->
        _state.update { it.copy(loading = true) }
        val res = api.syncNow(server, token)
        val notifs = api.notifications(server, token).items
        notifyNewUnread(notifs)
        _state.update {
            it.copy(
                loading = false,
                notifications = notifs,
                statusBanner = if (res.created > 0) "${res.created} nova(s) atualização(ões) encontrada(s)" else "Nenhuma nota nova no momento"
            )
        }
    }

    suspend fun markAllNotificationsRead() = withToken { server, token ->
        api.markNotificationsRead(server, token)
        val notifs = api.notifications(server, token).items
        _state.update { it.copy(notifications = notifs) }
    }

    suspend fun sendTestNotification() = withToken { server, token ->
        api.testNotification(server, token)
        val notifs = api.notifications(server, token).items
        val latest = notifs.maxByOrNull { it.id }
        val posted = PlatformBridge.postSystemNotification(
            id = (latest?.id ?: 9999L).toInt(),
            title = "UNICEP Portal • Teste de Notificação",
            body = "Notificações ativas neste aparelho! Exemplo: P1 → 9,5.",
            openTab = 2
        )
        latest?.let { PlatformBridge.putString(PrefKeys.LAST_NOTIFIED_ID, it.id.toString()) }
        _state.update {
            it.copy(
                notifications = notifs,
                statusBanner = if (posted) {
                    "Notificação enviada para o sistema do aparelho"
                } else {
                    "Notificação salva no painel (habilite a permissão no sistema para exibir pop-up)"
                }
            )
        }
    }

    private fun notifyNewUnread(notifs: List<NotificationItem>) {
        val lastSeenId = PlatformBridge.getString(PrefKeys.LAST_NOTIFIED_ID)?.toLongOrNull() ?: 0L
        val freshOnes = notifs.filter { it.readAt == null && it.id > lastSeenId }.sortedBy { it.id }
        if (freshOnes.isEmpty()) return
        var anyPosted = false
        freshOnes.forEach { n ->
            val title = if (n.kind == "grade") "Nova nota: ${n.disciplineName}" else "Atualização: ${n.disciplineName}"
            val oldTxt = if (n.oldValue.isBlank()) "—" else n.oldValue
            val body = "${n.field}: $oldTxt → ${n.newValue}"
            if (PlatformBridge.postSystemNotification(n.id.toInt(), title, body, openTab = 2)) {
                anyPosted = true
            }
        }
        if (anyPosted) {
            PlatformBridge.putString(PrefKeys.LAST_NOTIFIED_ID, freshOnes.last().id.toString())
        }
    }

    private suspend fun withToken(block: suspend (server: String, token: String) -> Unit) {
        val server = _state.value.serverUrl
        val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
        if (token.isNullOrBlank()) {
            _state.update { it.copy(loggedIn = false) }
            return
        }
        try {
            block(server, token)
        } catch (_: UnauthorizedException) {
            val ra = PlatformBridge.getString(PrefKeys.RA)
            val pass = PlatformBridge.getString(PrefKeys.PASSWORD)
            val perfil = PlatformBridge.getString(PrefKeys.PERFIL) ?: "alunograd"
            if (!ra.isNullOrBlank() && !pass.isNullOrBlank()) {
                runCatching {
                    val fresh = api.login(server, LoginRequest(ra, pass, perfil))
                    PlatformBridge.putString(PrefKeys.JWT_TOKEN, fresh.token)
                    block(server, fresh.token)
                }.onFailure { err ->
                    _state.update { it.copy(loading = false, loggedIn = false, error = err.message) }
                }
            } else {
                _state.update { it.copy(loading = false, loggedIn = false, error = "Sessão expirada. Entre novamente.") }
            }
        } catch (err: Exception) {
            _state.update { it.copy(loading = false, error = err.message ?: "Erro de comunicação") }
        }
    }
}
