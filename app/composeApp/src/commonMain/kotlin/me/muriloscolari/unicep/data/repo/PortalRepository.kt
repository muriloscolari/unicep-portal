package me.muriloscolari.unicep.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    const val CACHE_OWNER_RA = "cache_owner_ra"
    const val CACHE_UPDATED_AT_MS = "cache_updated_at_ms"
    const val CACHE_DASHBOARD = "cache_dashboard_json"
    const val CACHE_CURRICULUM = "cache_curriculum_json"
    const val CACHE_SCHEDULE = "cache_schedule_json"
    const val CACHE_FINANCE = "cache_finance_json"
    const val CACHE_SERVICES = "cache_services_json"
    const val FREQ_ALERT_THRESHOLD = "freq_alert_threshold"
    const val FREQ_ALERT_NOTIFIED = "freq_alert_notified_json"
}

data class PortalUiState(
    val initializing: Boolean = true,
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val isOffline: Boolean = false,
    val lastSyncAtMillis: Long? = null,
    val error: String? = null,
    val serverUrl: String = PlatformBridge.capabilities().defaultServerUrl,
    val stylePreference: DesignStylePreference = DesignStylePreference.AUTO,
    val freqAlertThreshold: Int = 80,
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
    private val cacheJson = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(
        PortalUiState(
            serverUrl = PlatformBridge.getString(PrefKeys.SERVER_URL)
                ?.takeIf { it.isNotBlank() }
                ?: PlatformBridge.capabilities().defaultServerUrl,
            stylePreference = loadStylePreference(),
            freqAlertThreshold = readFrequencyAlertThreshold()
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

    fun setFrequencyAlertThreshold(threshold: Int) {
        val clean = if (threshold <= 0) 0 else threshold.coerceIn(75, 95)
        saveFrequencyAlertThreshold(clean)
        _state.update { it.copy(freqAlertThreshold = clean) }
        _state.value.dashboard?.currentReport?.items?.let { items ->
            evaluateFrequencyAlerts(items, clean)
        }
    }

    fun clearBanner() {
        _state.update { it.copy(statusBanner = null, error = null) }
    }

    private inline fun <reified T> readCached(key: String): T? {
        val raw = PlatformBridge.getString(key) ?: return null
        return runCatching { cacheJson.decodeFromString<T>(raw) }.getOrNull()
    }

    private inline fun <reified T> writeCached(key: String, value: T) {
        runCatching { cacheJson.encodeToString(value) }.getOrNull()?.let {
            PlatformBridge.putString(key, it)
        }
    }

    private fun clearAllCache() {
        listOf(
            PrefKeys.CACHE_OWNER_RA,
            PrefKeys.CACHE_UPDATED_AT_MS,
            PrefKeys.CACHE_DASHBOARD,
            PrefKeys.CACHE_CURRICULUM,
            PrefKeys.CACHE_SCHEDULE,
            PrefKeys.CACHE_FINANCE,
            PrefKeys.CACHE_SERVICES,
            PrefKeys.FREQ_ALERT_NOTIFIED
        ).forEach { PlatformBridge.remove(it) }
    }

    private fun restoreCacheForRa(ra: String?): Boolean {
        if (ra.isNullOrBlank()) return false
        val owner = PlatformBridge.getString(PrefKeys.CACHE_OWNER_RA)
        if (owner != ra) return false
        val dash = readCached<DashboardResponse>(PrefKeys.CACHE_DASHBOARD) ?: return false
        val curr = readCached<CurriculumResponse>(PrefKeys.CACHE_CURRICULUM)
        val sched = readCached<ScheduleResponse>(PrefKeys.CACHE_SCHEDULE)
        val fin = readCached<FinanceResponse>(PrefKeys.CACHE_FINANCE)
        val srv = readCached<ServicesResponse>(PrefKeys.CACHE_SERVICES)
        val syncedAt = PlatformBridge.getString(PrefKeys.CACHE_UPDATED_AT_MS)?.toLongOrNull()

        val gradeId = dash.gradePeriods.firstOrNull()?.id.orEmpty()
        val reportId = findMatchingReportPeriodId(gradeId, dash.reportPeriods)
            ?: dash.reportPeriods.firstOrNull()?.id.orEmpty()

        _state.update {
            it.copy(
                initializing = false,
                loading = false,
                loggedIn = true,
                isOffline = false,
                lastSyncAtMillis = syncedAt,
                error = null,
                dashboard = dash,
                selectedGradePeriodId = gradeId,
                grades = GradesResponse(period = gradeId, items = dash.currentGrades),
                selectedReportPeriodId = reportId,
                report = dash.currentReport,
                curriculum = curr,
                schedule = sched,
                finance = fin,
                services = srv,
                notifications = dash.unreadNotifications
            )
        }
        return true
    }

    suspend fun tryAutoLogin() {
        val server = _state.value.serverUrl
        val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
        val ra = PlatformBridge.getString(PrefKeys.RA)
        val pass = PlatformBridge.getString(PrefKeys.PASSWORD)
        val perfil = PlatformBridge.getString(PrefKeys.PERFIL) ?: "alunograd"
        val hasCache = !token.isNullOrBlank() && restoreCacheForRa(ra)

        if (!token.isNullOrBlank()) {
            val loaded = runCatching { loadAllInitial(server, token, background = hasCache) }
            if (loaded.isSuccess) {
                PlatformBridge.scheduleGradeSync()
                return
            }
            val err = loaded.exceptionOrNull()
            if (err !is UnauthorizedException) {
                if (hasCache) {
                    _state.update { it.copy(initializing = false, loading = false, isOffline = true) }
                } else {
                    _state.update {
                        it.copy(
                            initializing = false,
                            loggedIn = false,
                            error = err?.message
                        )
                    }
                }
                return
            }
        }

        if (!ra.isNullOrBlank() && !pass.isNullOrBlank()) {
            val loginOk = login(server, ra, pass, perfil, isAuto = true, hasCache = hasCache)
            if (!loginOk && hasCache) {
                _state.update { it.copy(initializing = false, loading = false, loggedIn = true, isOffline = true) }
            }
        } else if (hasCache) {
            _state.update { it.copy(initializing = false, loading = false, loggedIn = true, isOffline = true) }
        } else {
            _state.update { it.copy(initializing = false, loggedIn = false) }
        }
    }

    suspend fun login(
        serverUrl: String,
        ra: String,
        password: String,
        perfil: String,
        isAuto: Boolean = false,
        hasCache: Boolean = false
    ): Boolean {
        val cleanUrl = serverUrl.trim().trimEnd('/').ifBlank { PlatformBridge.capabilities().defaultServerUrl }
        val cleanRa = ra.trim()
        val prevOwner = PlatformBridge.getString(PrefKeys.CACHE_OWNER_RA)
        if (!prevOwner.isNullOrBlank() && prevOwner != cleanRa) {
            clearAllCache()
        }
        _state.update { it.copy(loading = !hasCache, error = null, serverUrl = cleanUrl) }
        return runCatching {
            val resp = api.login(cleanUrl, LoginRequest(ra = cleanRa, password = password, perfil = perfil))
            PlatformBridge.putString(PrefKeys.SERVER_URL, cleanUrl)
            PlatformBridge.putString(PrefKeys.JWT_TOKEN, resp.token)
            PlatformBridge.putString(PrefKeys.RA, cleanRa)
            PlatformBridge.putString(PrefKeys.PERFIL, perfil)
            if (!PlatformBridge.capabilities().isWeb) {
                // On Android, keep encrypted credentials in Keystore for silent auto-login.
                PlatformBridge.putString(PrefKeys.PASSWORD, password)
            }
            PlatformBridge.requestNotificationPermissionIfNeeded()
            PlatformBridge.scheduleGradeSync()
            loadAllInitial(cleanUrl, resp.token, background = hasCache)
            true
        }.getOrElse { err ->
            if (!hasCache) {
                _state.update {
                    it.copy(
                        initializing = false,
                        loading = false,
                        loggedIn = false,
                        error = if (isAuto) null else (err.message ?: "Erro ao conectar")
                    )
                }
            }
            false
        }
    }

    suspend fun logout() {
        val server = _state.value.serverUrl
        val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
        if (!token.isNullOrBlank()) {
            runCatching { api.logout(server, token) }
        }
        PlatformBridge.remove(PrefKeys.JWT_TOKEN)
        PlatformBridge.remove(PrefKeys.PASSWORD)
        clearAllCache()
        PlatformBridge.cancelGradeSync()
        _state.update {
            PortalUiState(
                initializing = false,
                loggedIn = false,
                serverUrl = it.serverUrl,
                stylePreference = it.stylePreference,
                freqAlertThreshold = it.freqAlertThreshold
            )
        }
    }

    private suspend fun loadAllInitial(serverUrl: String, token: String, background: Boolean = false) {
        val dash = api.dashboard(serverUrl, token)
        val notifs = runCatching { api.notifications(serverUrl, token).items }.getOrDefault(dash.unreadNotifications)
        val gradeId = dash.gradePeriods.firstOrNull()?.id.orEmpty()
        val reportId = findMatchingReportPeriodId(gradeId, dash.reportPeriods)
            ?: dash.reportPeriods.firstOrNull()?.id.orEmpty()
        val nowMs = PlatformBridge.currentTimeMillis()
        val ownerRa = dash.student.ra.ifBlank { PlatformBridge.getString(PrefKeys.RA).orEmpty() }
        if (ownerRa.isNotBlank()) {
            PlatformBridge.putString(PrefKeys.CACHE_OWNER_RA, ownerRa)
        }
        PlatformBridge.putString(PrefKeys.CACHE_UPDATED_AT_MS, nowMs.toString())
        writeCached(PrefKeys.CACHE_DASHBOARD, dash)

        _state.update {
            it.copy(
                initializing = false,
                loading = if (background) it.loading else false,
                loggedIn = true,
                isOffline = false,
                lastSyncAtMillis = nowMs,
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
        evaluateFrequencyAlerts(dash.currentReport.items, _state.value.freqAlertThreshold)
    }

    suspend fun refreshDashboard() = withToken { server, token ->
        _state.update { it.copy(loading = true, error = null) }
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
        if (!force && _state.value.curriculum != null && _state.value.schedule != null && !_state.value.isOffline) return@withToken
        _state.update { it.copy(loading = true) }
        val curr = runCatching { api.curriculum(server, token) }.getOrNull()
        val sched = runCatching { api.schedule(server, token) }.getOrNull()
        val ownerRa = _state.value.dashboard?.student?.ra ?: PlatformBridge.getString(PrefKeys.RA).orEmpty()
        var syncedNow: Long? = null
        if (ownerRa.isNotBlank() && (curr != null || sched != null)) {
            if (curr != null) writeCached(PrefKeys.CACHE_CURRICULUM, curr)
            if (sched != null) writeCached(PrefKeys.CACHE_SCHEDULE, sched)
            val now = PlatformBridge.currentTimeMillis()
            PlatformBridge.putString(PrefKeys.CACHE_OWNER_RA, ownerRa)
            PlatformBridge.putString(PrefKeys.CACHE_UPDATED_AT_MS, now.toString())
            syncedNow = now
        }
        val gotFresh = curr != null || sched != null
        _state.update {
            it.copy(
                loading = false,
                isOffline = if (gotFresh) false else (it.dashboard != null),
                lastSyncAtMillis = syncedNow ?: it.lastSyncAtMillis,
                curriculum = curr ?: it.curriculum,
                schedule = sched ?: it.schedule
            )
        }
    }

    suspend fun loadFinanceAndServicesIfNeeded(force: Boolean = false) = withToken { server, token ->
        if (!force && _state.value.finance != null && _state.value.services != null && !_state.value.isOffline) return@withToken
        _state.update { it.copy(loading = true) }
        val fin = runCatching { api.finance(server, token) }.getOrNull()
        val srv = runCatching { api.services(server, token) }.getOrNull()
        val notifs = runCatching { api.notifications(server, token).items }.getOrNull()
        val ownerRa = _state.value.dashboard?.student?.ra ?: PlatformBridge.getString(PrefKeys.RA).orEmpty()
        var syncedNow: Long? = null
        if (ownerRa.isNotBlank() && (fin != null || srv != null)) {
            if (fin != null) writeCached(PrefKeys.CACHE_FINANCE, fin)
            if (srv != null) writeCached(PrefKeys.CACHE_SERVICES, srv)
            val now = PlatformBridge.currentTimeMillis()
            PlatformBridge.putString(PrefKeys.CACHE_OWNER_RA, ownerRa)
            PlatformBridge.putString(PrefKeys.CACHE_UPDATED_AT_MS, now.toString())
            syncedNow = now
        }
        val gotFresh = fin != null || srv != null || notifs != null
        _state.update {
            it.copy(
                loading = false,
                isOffline = if (gotFresh) false else (it.dashboard != null),
                lastSyncAtMillis = syncedNow ?: it.lastSyncAtMillis,
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

    suspend fun openPortalLink(path: String, proxy: Boolean) {
        if (!proxy) {
            PlatformBridge.openExternalUrl(path)
            return
        }
        withToken { server, token ->
            val ticket = api.createProxyTicket(server, token, path).ticket
            PlatformBridge.openExternalUrl(api.buildProxyUrlWithTicket(server, ticket, path))
        }
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
                    val hasCache = _state.value.dashboard != null
                    if (hasCache && err !is UnauthorizedException) {
                        _state.update { it.copy(loading = false, isOffline = true, error = null) }
                    } else {
                        _state.update { it.copy(loading = false, loggedIn = false, error = err.message) }
                    }
                }
            } else {
                _state.update { it.copy(loading = false, loggedIn = false, error = "Sessão expirada. Entre novamente.") }
            }
        } catch (err: Exception) {
            val hasCache = _state.value.dashboard != null
            if (hasCache) {
                _state.update { it.copy(loading = false, isOffline = true, error = null) }
            } else {
                _state.update { it.copy(loading = false, error = err.message ?: "Erro de comunicação") }
            }
        }
    }
}
