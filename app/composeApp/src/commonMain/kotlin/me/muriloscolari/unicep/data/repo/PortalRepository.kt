package me.muriloscolari.unicep.data.repo

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.api.ApiException
import me.muriloscolari.unicep.data.api.CalendarEventInput
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
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.platform.AppTabIndex
import me.muriloscolari.unicep.platform.DesignStylePreference
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.util.shouldDirectDownloadOnWeb

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
    const val CACHE_VIRTUAL_OVERVIEW = "cache_virtual_overview_json"
    const val FREQ_ALERT_THRESHOLD = "freq_alert_threshold"
    const val FREQ_ALERT_NOTIFIED = "freq_alert_notified_json"
    const val VIRTUAL_NOTIFY_NEW_ASSIGNMENT = "virtual_notify_new_assignment"
    const val VIRTUAL_DEADLINE_LEAD_HOURS = "virtual_deadline_lead_hours_json"
    const val VIRTUAL_DEADLINE_NOTIFIED = "virtual_deadline_notified_json"
    const val VIRTUAL_DEADLINE_NOTIF_IDS = "virtual_deadline_notif_ids_json"
    const val VIRTUAL_DOWNLOAD_TREE_URI = "virtual_download_tree_uri"
    const val VIRTUAL_DOWNLOAD_FOLDER_LABEL = "virtual_download_folder_label"
    const val CACHE_CALENDAR_EVENTS = "cache_calendar_events_json"
    const val CALENDAR_REMINDERS_NOTIFIED = "calendar_reminders_notified_json"
    const val LAST_BG_FULL_SYNC_MS = "last_bg_full_sync_ms"
    const val CALENDAR_ALARM_IDS = "calendar_alarm_ids_json"
    const val BATTERY_OPT_PROMPTED = "battery_opt_prompted"
}

@Immutable
data class VirtualDownloadRequest(
    val path: String,
    val fileName: String,
    val courseName: String
)

@Immutable
data class VirtualDocumentViewerState(
    val title: String,
    val courseName: String,
    val fileName: String,
    val path: String,
    val mimeType: String,
    val bytes: ByteArray? = null,
    val fallbackUrl: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VirtualDocumentViewerState) return false
        return title == other.title &&
            courseName == other.courseName &&
            fileName == other.fileName &&
            path == other.path &&
            mimeType == other.mimeType &&
            fallbackUrl == other.fallbackUrl &&
            bytes?.size == other.bytes?.size
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (bytes?.size ?: 0)
        return result
    }
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
    val downloadFolderLabel: String = PlatformBridge.getDownloadFolderLabel(),
    val virtualNotifyNewAssignment: Boolean = true,
    val virtualDeadlineLeadHours: List<Int> = DEFAULT_VIRTUAL_DEADLINE_LEAD_HOURS,
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
    val statusBanner: String? = null,
    val virtualOverview: VirtualOverviewResponse? = null,
    val virtualCourseDetails: Map<Long, VirtualCourseDetailResponse> = emptyMap(),
    val virtualLoading: Boolean = false,
    val virtualError: String? = null,
    val virtualOverviewTab: Int = 0,
    val virtualSearchQuery: String = "",
    val virtualSelectedSemester: String = "",
    val virtualAssignmentFilterIndex: Int = 0,
    val activeDocumentViewer: VirtualDocumentViewerState? = null,
    val documentLoadingLabel: String? = null,
    val downloadingFileKey: String? = null,
    val batchDownloadDone: Int = 0,
    val batchDownloadTotal: Int = 0,
    val calendarEvents: List<UserCalendarEvent> = emptyList(),
    val calendarLoading: Boolean = false,
    val calendarSaving: Boolean = false,
    val calendarError: String? = null
)

private const val VIRTUAL_OVERVIEW_FRESH_MS = 90_000L
private const val VIRTUAL_COURSE_FRESH_MS = 90_000L

class PortalRepository(private val api: PortalApiClient = PortalApiClient()) {
    private val cacheJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var virtualOverviewSyncedAtMs: Long = 0L
    private var forceNextVirtualOverviewRefresh: Boolean = false
    private val virtualCourseSyncedAtMs = mutableMapOf<Long, Long>()
    private var scheduleSyncedInSession: Boolean = false
    private var academicSyncedInSession: Boolean = false
    private var financeServicesSyncedInSession: Boolean = false
    private var calendarEventsSyncedInSession: Boolean = false

    private fun resetSessionFreshness() {
        virtualOverviewSyncedAtMs = 0L
        forceNextVirtualOverviewRefresh = false
        virtualCourseSyncedAtMs.clear()
        scheduleSyncedInSession = false
        academicSyncedInSession = false
        financeServicesSyncedInSession = false
        calendarEventsSyncedInSession = false
    }

    private fun isVirtualOverviewFresh(nowMs: Long): Boolean =
        virtualOverviewSyncedAtMs > 0L && (nowMs - virtualOverviewSyncedAtMs) < VIRTUAL_OVERVIEW_FRESH_MS

    private val _state = MutableStateFlow(
        PortalUiState(
            serverUrl = PlatformBridge.getString(PrefKeys.SERVER_URL)
                ?.takeIf { it.isNotBlank() }
                ?: PlatformBridge.capabilities().defaultServerUrl,
            stylePreference = loadStylePreference(),
            freqAlertThreshold = readFrequencyAlertThreshold(),
            downloadFolderLabel = PlatformBridge.getDownloadFolderLabel(),
            virtualNotifyNewAssignment = readVirtualNotifyNewAssignment(),
            virtualDeadlineLeadHours = readVirtualDeadlineLeadHours()
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

    suspend fun setVirtualNotifyNewAssignment(enabled: Boolean) {
        saveVirtualNotifyNewAssignment(enabled)
        _state.update { it.copy(virtualNotifyNewAssignment = enabled) }
        withToken { server, token ->
            runCatching { api.updateD2LSettings(server, token, enabled) }
        }
    }

    fun setVirtualOverviewTab(tab: Int) {
        _state.update { it.copy(virtualOverviewTab = tab.coerceIn(0, 3)) }
    }

    fun setVirtualSearchQuery(query: String) {
        _state.update { it.copy(virtualSearchQuery = query) }
    }

    fun setVirtualSelectedSemester(semester: String) {
        _state.update { it.copy(virtualSelectedSemester = semester) }
    }

    fun setVirtualAssignmentFilterIndex(index: Int) {
        _state.update { it.copy(virtualAssignmentFilterIndex = index.coerceIn(0, 2)) }
    }

    fun toggleVirtualDeadlineLeadHour(hours: Int) {
        val current = _state.value.virtualDeadlineLeadHours
        val updated = if (current.contains(hours)) current - hours else current + hours
        updateVirtualDeadlineLeadHours(updated)
    }

    fun addVirtualDeadlineLeadHour(hours: Int) {
        val clean = hours.coerceIn(1, 336)
        updateVirtualDeadlineLeadHours(_state.value.virtualDeadlineLeadHours + clean)
    }

    fun removeVirtualDeadlineLeadHour(hours: Int) {
        updateVirtualDeadlineLeadHours(_state.value.virtualDeadlineLeadHours - hours)
    }

    private fun updateVirtualDeadlineLeadHours(hoursList: List<Int>) {
        val saved = saveVirtualDeadlineLeadHours(hoursList)
        _state.update { it.copy(virtualDeadlineLeadHours = saved) }
        _state.value.virtualOverview?.upcomingAssignments?.let { items ->
            evaluateVirtualDeadlineAlerts(items, saved)
        }
    }

    suspend fun pickDownloadFolder() {
        val picked = PlatformBridge.pickDownloadFolder()
        val label = PlatformBridge.getDownloadFolderLabel()
        _state.update {
            it.copy(
                downloadFolderLabel = label,
                statusBanner = if (picked != null) "Pasta padrão: $label" else it.statusBanner
            )
        }
    }

    fun clearDownloadFolder() {
        PlatformBridge.clearDownloadFolder()
        val label = PlatformBridge.getDownloadFolderLabel()
        _state.update {
            it.copy(
                downloadFolderLabel = label,
                statusBanner = "Pasta restaurada para $label"
            )
        }
    }

    fun closeDocumentViewer() {
        _state.update { it.copy(activeDocumentViewer = null) }
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
            PrefKeys.LAST_NOTIFIED_ID,
            PrefKeys.CACHE_OWNER_RA,
            PrefKeys.CACHE_UPDATED_AT_MS,
            PrefKeys.CACHE_DASHBOARD,
            PrefKeys.CACHE_CURRICULUM,
            PrefKeys.CACHE_SCHEDULE,
            PrefKeys.CACHE_FINANCE,
            PrefKeys.CACHE_SERVICES,
            PrefKeys.CACHE_VIRTUAL_OVERVIEW,
            PrefKeys.FREQ_ALERT_THRESHOLD,
            PrefKeys.FREQ_ALERT_NOTIFIED,
            PrefKeys.VIRTUAL_NOTIFY_NEW_ASSIGNMENT,
            PrefKeys.VIRTUAL_DEADLINE_LEAD_HOURS,
            PrefKeys.VIRTUAL_DEADLINE_NOTIFIED,
            PrefKeys.VIRTUAL_DEADLINE_NOTIF_IDS,
            PrefKeys.CACHE_CALENDAR_EVENTS,
            PrefKeys.CALENDAR_REMINDERS_NOTIFIED,
            PrefKeys.LAST_BG_FULL_SYNC_MS,
            PrefKeys.CALENDAR_ALARM_IDS
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
        val virt = readCached<VirtualOverviewResponse>(PrefKeys.CACHE_VIRTUAL_OVERVIEW)
            ?.let { normalizeVirtualOverviewForTime(it, PlatformBridge.currentTimeMillis()) }
        val cal = readCached<List<UserCalendarEvent>>(PrefKeys.CACHE_CALENDAR_EVENTS).orEmpty()
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
                virtualOverview = virt,
                calendarEvents = cal,
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
            PlatformBridge.scheduleGradeSync()
            val loaded = runCatching { loadAllInitial(server, token, background = hasCache) }
            if (loaded.isSuccess) {
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
            resetSessionFreshness()
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
        resetSessionFreshness()
        PlatformBridge.clearDownloadFolder()
        PlatformBridge.cancelAllCalendarReminders()
        PlatformBridge.cancelGradeSync()
        _state.update {
            PortalUiState(
                initializing = false,
                loggedIn = false,
                serverUrl = it.serverUrl,
                stylePreference = it.stylePreference,
                downloadFolderLabel = PlatformBridge.getDownloadFolderLabel()
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
        if (!force && academicSyncedInSession && _state.value.curriculum != null && _state.value.schedule != null && !_state.value.isOffline) return@withToken
        _state.update { it.copy(loading = true) }
        val curr = runCatching { api.curriculum(server, token) }.onFailure { if (it is UnauthorizedException) throw it }.getOrNull()
        val sched = runCatching { api.schedule(server, token) }.onFailure { if (it is UnauthorizedException) throw it }.getOrNull()
        if (sched != null) {
            scheduleSyncedInSession = true
        }
        if (curr != null && sched != null) {
            academicSyncedInSession = true
        }
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

    suspend fun preloadHomeContextIfNeeded(force: Boolean = false) = withToken { server, token ->
        val nowMs = PlatformBridge.currentTimeMillis()
        val needSchedule = force || _state.value.schedule == null || !scheduleSyncedInSession
        val forceVirt = force || forceNextVirtualOverviewRefresh
        val needVirtual = forceVirt || _state.value.virtualOverview == null || !isVirtualOverviewFresh(nowMs)
        val needCalendar = force || !calendarEventsSyncedInSession
        if (!needSchedule && !needVirtual && !needCalendar) return@withToken

        val ownerRa = _state.value.dashboard?.student?.ra ?: PlatformBridge.getString(PrefKeys.RA).orEmpty()
        if (needSchedule) {
            val sched = runCatching { api.schedule(server, token) }.onFailure { if (it is UnauthorizedException) throw it }.getOrNull()
            if (sched != null) {
                scheduleSyncedInSession = true
                if (ownerRa.isNotBlank()) writeCached(PrefKeys.CACHE_SCHEDULE, sched)
                _state.update { it.copy(schedule = sched) }
            }
        }
        if (needVirtual) {
            val ov = runCatching { api.virtualOverview(server, token, forceRefresh = forceVirt) }.onFailure { if (it is UnauthorizedException) throw it }.getOrNull()
            if (ov != null) {
                val syncedAt = PlatformBridge.currentTimeMillis()
                val cleanOv = normalizeVirtualOverviewForTime(ov, syncedAt)
                virtualOverviewSyncedAtMs = syncedAt
                forceNextVirtualOverviewRefresh = false
                if (ownerRa.isNotBlank()) writeCached(PrefKeys.CACHE_VIRTUAL_OVERVIEW, cleanOv)
                _state.update { it.copy(virtualOverview = cleanOv) }
                evaluateVirtualDeadlineAlerts(cleanOv.upcomingAssignments)
            }
        }
        if (needCalendar) {
            val cal = runCatching { api.calendarEvents(server, token).events }.onFailure { if (it is UnauthorizedException) throw it }.getOrNull()
            if (cal != null) {
                calendarEventsSyncedInSession = true
                if (ownerRa.isNotBlank()) writeCached(PrefKeys.CACHE_CALENDAR_EVENTS, cal)
                _state.update { it.copy(calendarEvents = cal) }
                PlatformBridge.syncCalendarReminders(cal)
                evaluateCalendarReminders(
                    events = cal,
                    utcOffsetMinutes = PlatformBridge.currentLocalDateTime().utcOffsetMinutes
                )
            }
        }
    }

    suspend fun loadFinanceAndServicesIfNeeded(force: Boolean = false) = withToken { server, token ->
        if (!force && financeServicesSyncedInSession && _state.value.finance != null && _state.value.services != null && !_state.value.isOffline) return@withToken
        _state.update { it.copy(loading = true) }
        val fin = runCatching { api.finance(server, token) }.getOrNull()
        val srv = runCatching { api.services(server, token) }.getOrNull()
        val notifs = runCatching { api.notifications(server, token).items }.getOrNull()
        if (fin != null && srv != null) {
            financeServicesSyncedInSession = true
        }
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

    suspend fun loadVirtualOverviewIfNeeded(force: Boolean = false) = withToken { server, token ->
        val nowMs = PlatformBridge.currentTimeMillis()
        val forceRefresh = force || forceNextVirtualOverviewRefresh
        if (!forceRefresh && _state.value.virtualOverview != null && !_state.value.isOffline && isVirtualOverviewFresh(nowMs)) return@withToken
        _state.update { it.copy(virtualLoading = true, virtualError = null) }
        try {
            val result = runCatching { api.virtualOverview(server, token, forceRefresh = forceRefresh) }
            val err = result.exceptionOrNull()
            if (err is UnauthorizedException) throw err
            val ov = result.getOrNull()
            if (ov != null) {
                val syncedAt = PlatformBridge.currentTimeMillis()
                val cleanOv = normalizeVirtualOverviewForTime(ov, syncedAt)
                virtualOverviewSyncedAtMs = syncedAt
                forceNextVirtualOverviewRefresh = false
                val ownerRa = _state.value.dashboard?.student?.ra ?: PlatformBridge.getString(PrefKeys.RA).orEmpty()
                if (ownerRa.isNotBlank()) {
                    writeCached(PrefKeys.CACHE_VIRTUAL_OVERVIEW, cleanOv)
                }
                runCatching { api.getD2LSettings(server, token) }.getOrNull()?.let { s ->
                    saveVirtualNotifyNewAssignment(s.pollingEnabled)
                    _state.update { it.copy(virtualNotifyNewAssignment = s.pollingEnabled) }
                }
                _state.update {
                    it.copy(
                        isOffline = false,
                        virtualError = if (cleanOv.complete) null else "Algumas disciplinas não responderam por completo.",
                        virtualOverview = cleanOv
                    )
                }
                evaluateVirtualDeadlineAlerts(cleanOv.upcomingAssignments)
            } else {
                val msg = formatVirtualError(err, "Não foi possível carregar a Sala Virtual")
                _state.update { it.copy(virtualError = msg) }
            }
        } finally {
            _state.update { it.copy(virtualLoading = false) }
        }
    }

    suspend fun loadVirtualCourse(orgUnitId: Long, force: Boolean = false) = withToken { server, token ->
        val nowMs = PlatformBridge.currentTimeMillis()
        val lastSynced = virtualCourseSyncedAtMs[orgUnitId] ?: 0L
        if (!force &&
            _state.value.virtualCourseDetails.containsKey(orgUnitId) &&
            !_state.value.isOffline &&
            lastSynced > 0L &&
            (nowMs - lastSynced) < VIRTUAL_COURSE_FRESH_MS
        ) {
            return@withToken
        }
        _state.update { it.copy(virtualLoading = true, virtualError = null) }
        try {
            val result = runCatching { api.virtualCourseDetail(server, token, orgUnitId) }
            val err = result.exceptionOrNull()
            if (err is UnauthorizedException) throw err
            val detail = result.getOrNull()
            if (detail != null) {
                val syncedAt = PlatformBridge.currentTimeMillis()
                virtualCourseSyncedAtMs[orgUnitId] = syncedAt
                val reconciledOv = _state.value.virtualOverview?.let {
                    reconcileVirtualOverviewWithCourseDetail(it, detail, syncedAt)
                }
                if (reconciledOv != null) {
                    val ownerRa = _state.value.dashboard?.student?.ra ?: PlatformBridge.getString(PrefKeys.RA).orEmpty()
                    if (ownerRa.isNotBlank()) {
                        writeCached(PrefKeys.CACHE_VIRTUAL_OVERVIEW, reconciledOv)
                    }
                    evaluateVirtualDeadlineAlerts(reconciledOv.upcomingAssignments)
                }
                _state.update {
                    it.copy(
                        isOffline = false,
                        virtualError = null,
                        virtualCourseDetails = it.virtualCourseDetails + (orgUnitId to detail),
                        virtualOverview = reconciledOv ?: it.virtualOverview
                    )
                }
            } else {
                val msg = formatVirtualError(err, "Não foi possível carregar os detalhes da disciplina")
                _state.update { it.copy(virtualError = msg) }
            }
        } finally {
            _state.update { it.copy(virtualLoading = false) }
        }
    }

    private fun formatVirtualError(err: Throwable?, fallback: String): String {
        val apiErr = err as? ApiException
        if (apiErr != null) {
            return when (apiErr.code) {
                "d2l_auth_failed" -> "${apiErr.message ?: fallback} Se você alterou sua senha recentemente, saia e entre novamente."
                else -> apiErr.message ?: fallback
            }
        }
        return err?.message ?: fallback
    }

    suspend fun openD2LFile(
        path: String,
        title: String = "",
        courseName: String = "",
        fileName: String = ""
    ) {
        val targetName = fileName.ifBlank { title }
        if (!PlatformBridge.supportsInAppDocumentViewer() && shouldDirectDownloadOnWeb(targetName)) {
            downloadD2LFile(path, targetName, courseName)
            return
        }
        withToken { server, token ->
            val displayTitle = title.ifBlank { fileName.ifBlank { "Arquivo da Sala Virtual" } }
            if (!PlatformBridge.supportsInAppDocumentViewer()) {
                val result = runCatching {
                    val ticket = api.createD2LTicket(server, token, path).ticket
                    PlatformBridge.openExternalUrl(api.buildD2LFileUrlWithTicket(server, ticket, path))
                }
                val err = result.exceptionOrNull()
                if (err is UnauthorizedException) throw err
                if (err != null) {
                    _state.update { it.copy(statusBanner = err.message ?: "Não foi possível abrir o arquivo") }
                } else if (result.getOrNull() == false) {
                    _state.update {
                        it.copy(statusBanner = "Pop-up bloqueado pelo navegador. Use o botão Baixar ao lado ou libere pop-ups para este site.")
                    }
                }
                return@withToken
            }

            _state.update { it.copy(documentLoadingLabel = displayTitle) }
            val result = runCatching {
                val ticket = api.createD2LTicket(server, token, path).ticket
                val url = api.buildD2LFileUrlWithTicket(server, ticket, path)
                api.downloadFileBytes(url)
            }
            val err = result.exceptionOrNull()
            if (err is UnauthorizedException) {
                _state.update { it.copy(documentLoadingLabel = null) }
                throw err
            }
            val payload = result.getOrNull()
            if (payload == null) {
                _state.update {
                    it.copy(
                        documentLoadingLabel = null,
                        statusBanner = err?.message ?: "Não foi possível abrir o arquivo"
                    )
                }
                return@withToken
            }

            val resolvedName = resolveFileName(fileName, payload.suggestedFileName, title, payload.contentType)
            _state.update {
                it.copy(
                    documentLoadingLabel = null,
                    activeDocumentViewer = VirtualDocumentViewerState(
                        title = displayTitle,
                        courseName = courseName.ifBlank { "Sala Virtual" },
                        fileName = resolvedName,
                        path = path,
                        mimeType = payload.contentType,
                        bytes = payload.bytes,
                        fallbackUrl = ""
                    )
                )
            }
        }
    }

    suspend fun downloadActiveDocument() {
        val viewer = _state.value.activeDocumentViewer ?: return
        val bytes = viewer.bytes
        if (bytes != null) {
            _state.update { it.copy(downloadingFileKey = viewer.path) }
            val saved = PlatformBridge.saveDownloadedFile(
                courseName = viewer.courseName,
                fileName = viewer.fileName,
                mimeType = viewer.mimeType,
                bytes = bytes
            )
            _state.update {
                it.copy(
                    downloadingFileKey = null,
                    statusBanner = saved.fold(
                        onSuccess = { dest -> "Salvo em $dest" },
                        onFailure = { e -> e.message ?: "Erro ao salvar arquivo" }
                    )
                )
            }
            return
        }
        downloadD2LFile(viewer.path, viewer.fileName, viewer.courseName)
    }

    suspend fun downloadD2LFile(
        path: String,
        fileName: String,
        courseName: String
    ) = withToken { server, token ->
        val safeCourse = courseName.ifBlank { "Sala Virtual" }
        _state.update { it.copy(downloadingFileKey = path) }

        if (!PlatformBridge.supportsInAppDocumentViewer()) {
            val cleanName = fileName.ifBlank { "arquivo" }
            val result = runCatching {
                val ticket = api.createD2LTicket(server, token, path).ticket
                val dlUrl = api.buildD2LFileUrlWithTicket(server, ticket, path, download = true, fileName = cleanName)
                PlatformBridge.triggerBrowserDownload(dlUrl, cleanName)
            }
            val err = result.exceptionOrNull()
            if (err is UnauthorizedException) {
                _state.update { it.copy(downloadingFileKey = null) }
                throw err
            }
            _state.update {
                it.copy(
                    downloadingFileKey = null,
                    statusBanner = if (err == null) "Download iniciado: $cleanName" else (err.message ?: "Erro ao baixar arquivo")
                )
            }
            return@withToken
        }

        val result = runCatching {
            val ticket = api.createD2LTicket(server, token, path).ticket
            val url = api.buildD2LFileUrlWithTicket(server, ticket, path, download = true, fileName = fileName)
            val payload = api.downloadFileBytes(url)
            val resolvedName = resolveFileName(fileName, payload.suggestedFileName, "", payload.contentType)
            PlatformBridge.saveDownloadedFile(safeCourse, resolvedName, payload.contentType, payload.bytes).getOrThrow()
        }
        val err = result.exceptionOrNull()
        if (err is UnauthorizedException) {
            _state.update { it.copy(downloadingFileKey = null) }
            throw err
        }
        _state.update {
            it.copy(
                downloadingFileKey = null,
                statusBanner = result.fold(
                    onSuccess = { dest -> "Salvo em $dest" },
                    onFailure = { e -> e.message ?: "Não foi possível salvar o arquivo" }
                )
            )
        }
    }

    suspend fun downloadD2LFilesBatch(items: List<VirtualDownloadRequest>) = withToken { server, token ->
        val cleanItems = items.distinctBy { it.path }.filter { it.path.isNotBlank() }
        if (cleanItems.isEmpty()) return@withToken
        _state.update {
            it.copy(
                batchDownloadDone = 0,
                batchDownloadTotal = cleanItems.size
            )
        }
        var succeeded = 0
        var failed = 0
        val failedNames = mutableListOf<String>()
        var lastErrorMsg: String? = null

        suspend fun attemptOne(item: VirtualDownloadRequest): Result<String> {
            val first = runCatching {
                val ticket = api.createD2LTicket(server, token, item.path).ticket
                val url = api.buildD2LFileUrlWithTicket(server, ticket, item.path, download = true, fileName = item.fileName)
                val payload = api.downloadFileBytes(url)
                val resolvedName = resolveFileName(item.fileName, payload.suggestedFileName, "", payload.contentType)
                PlatformBridge.saveDownloadedFile(
                    courseName = item.courseName.ifBlank { "Sala Virtual" },
                    fileName = resolvedName,
                    mimeType = payload.contentType,
                    bytes = payload.bytes
                ).getOrThrow()
            }
            val apiErr = first.exceptionOrNull() as? ApiException
            if (apiErr != null && apiErr.statusCode == 429) {
                delay(1_500L)
                return runCatching {
                    val ticket = api.createD2LTicket(server, token, item.path).ticket
                    val url = api.buildD2LFileUrlWithTicket(server, ticket, item.path, download = true, fileName = item.fileName)
                    val payload = api.downloadFileBytes(url)
                    val resolvedName = resolveFileName(item.fileName, payload.suggestedFileName, "", payload.contentType)
                    PlatformBridge.saveDownloadedFile(
                        courseName = item.courseName.ifBlank { "Sala Virtual" },
                        fileName = resolvedName,
                        mimeType = payload.contentType,
                        bytes = payload.bytes
                    ).getOrThrow()
                }
            }
            return first
        }

        try {
            cleanItems.forEachIndexed { idx, item ->
                if (idx > 0) delay(120L)
                val res = attemptOne(item)
                val err = res.exceptionOrNull()
                if (err is UnauthorizedException) throw err
                if (err == null) {
                    succeeded++
                } else {
                    failed++
                    failedNames += item.fileName.ifBlank { "arquivo ${idx + 1}" }
                    lastErrorMsg = err.message
                }
                _state.update { it.copy(batchDownloadDone = idx + 1) }
            }
        } finally {
            val folder = PlatformBridge.getDownloadFolderLabel()
            val summary = when {
                succeeded > 0 && failed == 0 -> "$succeeded arquivo(s) salvo(s) em $folder"
                succeeded > 0 -> {
                    val sample = failedNames.take(2).joinToString(", ")
                    val suffix = if (failedNames.size > 2) " +${failedNames.size - 2}" else ""
                    "$succeeded salvo(s), $failed falha(s): $sample$suffix"
                }
                else -> lastErrorMsg ?: "Não foi possível baixar os arquivos"
            }
            _state.update {
                it.copy(
                    batchDownloadDone = 0,
                    batchDownloadTotal = 0,
                    statusBanner = summary
                )
            }
        }
    }

    private fun resolveFileName(preferred: String, serverName: String, titleFallback: String, contentType: String): String {
        val raw = preferred.trim()
            .ifBlank { serverName.trim() }
            .ifBlank { titleFallback.trim() }
            .ifBlank { "arquivo" }
        if (raw.contains('.')) return raw
        val ext = when {
            contentType.contains("pdf") -> ".pdf"
            contentType.contains("html") -> ".html"
            contentType.contains("png") -> ".png"
            contentType.contains("jpeg") || contentType.contains("jpg") -> ".jpg"
            contentType.contains("wordprocessingml") -> ".docx"
            contentType.contains("spreadsheetml") -> ".xlsx"
            contentType.contains("presentationml") -> ".pptx"
            contentType.contains("zip") -> ".zip"
            contentType.startsWith("text/") -> ".txt"
            else -> ""
        }
        return raw + ext
    }

    fun openSalaVirtualWeb(url: String = "https://salavirtual.unicep.edu.br/d2l/home") {
        virtualOverviewSyncedAtMs = 0L
        virtualCourseSyncedAtMs.clear()
        forceNextVirtualOverviewRefresh = true
        PlatformBridge.openExternalUrl(url)
    }

    suspend fun refreshVirtualOnResume(selectedCourseId: Long?) {
        val nowMs = PlatformBridge.currentTimeMillis()
        _state.update { current ->
            val ov = current.virtualOverview ?: return@update current
            val normalized = normalizeVirtualOverviewForTime(ov, nowMs)
            if (normalized === ov) current else current.copy(virtualOverview = normalized)
        }
        if (selectedCourseId != null) {
            loadVirtualCourse(selectedCourseId, force = forceNextVirtualOverviewRefresh)
        } else {
            loadVirtualOverviewIfNeeded(force = false)
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
                statusBanner = if (res.created > 0) "${res.created} nova(s) atualização(ões) encontrada(s)" else "Nenhuma atualização nova no momento"
            )
        }
    }

    suspend fun loadCalendarContextIfNeeded(force: Boolean = false) = withToken { server, token ->
        val nowMs = PlatformBridge.currentTimeMillis()
        val needEvents = force || !calendarEventsSyncedInSession || _state.value.isOffline
        val forceVirt = force || forceNextVirtualOverviewRefresh
        val needVirtual = forceVirt || _state.value.virtualOverview == null || _state.value.isOffline || !isVirtualOverviewFresh(nowMs)
        if (!needEvents && !needVirtual) return@withToken

        _state.update { it.copy(calendarLoading = true, calendarError = null) }
        try {
            val eventsRes = if (needEvents) {
                runCatching { api.calendarEvents(server, token) }.also { r ->
                    if (r.exceptionOrNull() is UnauthorizedException) throw r.exceptionOrNull()!!
                }
            } else null

            val ovRes = if (needVirtual) {
                runCatching { api.virtualOverview(server, token, forceRefresh = forceVirt) }.also { r ->
                    if (r.exceptionOrNull() is UnauthorizedException) throw r.exceptionOrNull()!!
                }
            } else null

            val ownerRa = _state.value.dashboard?.student?.ra ?: PlatformBridge.getString(PrefKeys.RA).orEmpty()
            val freshEvents = eventsRes?.getOrNull()?.events
            val freshOv = ovRes?.getOrNull()?.let {
                normalizeVirtualOverviewForTime(it, PlatformBridge.currentTimeMillis())
            }

            if (freshEvents != null) {
                calendarEventsSyncedInSession = true
                if (ownerRa.isNotBlank()) writeCached(PrefKeys.CACHE_CALENDAR_EVENTS, freshEvents)
                PlatformBridge.syncCalendarReminders(freshEvents)
                evaluateCalendarReminders(
                    events = freshEvents,
                    utcOffsetMinutes = PlatformBridge.currentLocalDateTime().utcOffsetMinutes
                )
            }
            if (freshOv != null) {
                virtualOverviewSyncedAtMs = PlatformBridge.currentTimeMillis()
                forceNextVirtualOverviewRefresh = false
                if (ownerRa.isNotBlank()) writeCached(PrefKeys.CACHE_VIRTUAL_OVERVIEW, freshOv)
                evaluateVirtualDeadlineAlerts(freshOv.upcomingAssignments)
            }

            val eventsErr = eventsRes?.exceptionOrNull()
            _state.update {
                it.copy(
                    isOffline = if (freshEvents != null || freshOv != null) false else it.isOffline,
                    calendarEvents = freshEvents ?: it.calendarEvents,
                    virtualOverview = freshOv ?: it.virtualOverview,
                    calendarError = if (eventsRes != null && freshEvents == null) {
                        eventsErr?.message ?: "Não foi possível carregar os eventos do calendário"
                    } else null
                )
            }
        } finally {
            _state.update { it.copy(calendarLoading = false) }
        }
    }

    suspend fun createCalendarEvent(input: CalendarEventInput): Boolean {
        var success = false
        withToken { server, token ->
            _state.update { it.copy(calendarSaving = true, calendarError = null) }
            try {
                val created = api.createCalendarEvent(server, token, input).event
                val updatedList = (_state.value.calendarEvents + created)
                    .sortedWith(compareBy<UserCalendarEvent> { it.startAtMillis }.thenBy { it.id })
                writeCached(PrefKeys.CACHE_CALENDAR_EVENTS, updatedList)
                PlatformBridge.syncCalendarReminders(updatedList)
                evaluateCalendarReminders(
                    events = updatedList,
                    utcOffsetMinutes = PlatformBridge.currentLocalDateTime().utcOffsetMinutes
                )
                _state.update {
                    it.copy(
                        calendarEvents = updatedList,
                        statusBanner = "Evento adicionado ao calendário"
                    )
                }
                success = true
            } catch (e: ApiException) {
                _state.update { it.copy(statusBanner = e.message ?: "Não foi possível salvar o evento") }
            } finally {
                _state.update { it.copy(calendarSaving = false) }
            }
        }
        return success
    }

    suspend fun updateCalendarEvent(id: Long, input: CalendarEventInput): Boolean {
        var success = false
        withToken { server, token ->
            _state.update { it.copy(calendarSaving = true, calendarError = null) }
            try {
                val updated = api.updateCalendarEvent(server, token, id, input).event
                val updatedList = _state.value.calendarEvents
                    .map { if (it.id == id) updated else it }
                    .sortedWith(compareBy<UserCalendarEvent> { it.startAtMillis }.thenBy { it.id })
                writeCached(PrefKeys.CACHE_CALENDAR_EVENTS, updatedList)
                PlatformBridge.syncCalendarReminders(updatedList)
                evaluateCalendarReminders(
                    events = updatedList,
                    utcOffsetMinutes = PlatformBridge.currentLocalDateTime().utcOffsetMinutes
                )
                _state.update {
                    it.copy(
                        calendarEvents = updatedList,
                        statusBanner = "Evento atualizado"
                    )
                }
                success = true
            } catch (e: ApiException) {
                _state.update { it.copy(statusBanner = e.message ?: "Não foi possível atualizar o evento") }
            } finally {
                _state.update { it.copy(calendarSaving = false) }
            }
        }
        return success
    }

    suspend fun deleteCalendarEvent(id: Long): Boolean {
        var success = false
        withToken { server, token ->
            _state.update { it.copy(calendarSaving = true, calendarError = null) }
            try {
                api.deleteCalendarEvent(server, token, id)
                val updatedList = _state.value.calendarEvents.filterNot { it.id == id }
                writeCached(PrefKeys.CACHE_CALENDAR_EVENTS, updatedList)
                PlatformBridge.syncCalendarReminders(updatedList)
                _state.update {
                    it.copy(
                        calendarEvents = updatedList,
                        statusBanner = "Evento removido do calendário"
                    )
                }
                success = true
            } catch (e: ApiException) {
                _state.update { it.copy(statusBanner = e.message ?: "Não foi possível excluir o evento") }
            } finally {
                _state.update { it.copy(calendarSaving = false) }
            }
        }
        return success
    }

    fun evaluateDueCalendarReminders() {
        val events = _state.value.calendarEvents
        if (events.isEmpty()) return
        evaluateCalendarReminders(
            events = events,
            utcOffsetMinutes = PlatformBridge.currentLocalDateTime().utcOffsetMinutes
        )
    }

    suspend fun markAllNotificationsRead() = withToken { server, token ->
        api.markNotificationsRead(server, token)
        val notifs = api.notifications(server, token).items
        _state.update { it.copy(notifications = notifs) }
    }

    suspend fun refreshNotificationsSilently() {
        val server = _state.value.serverUrl
        val token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
        if (!_state.value.loggedIn || token.isNullOrBlank()) return
        val notifs = runCatching { api.notifications(server, token).items }.getOrNull() ?: return
        notifyNewUnread(notifs)
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
            openTab = AppTabIndex.CENTRAL
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
        val notifyVirtualNew = readVirtualNotifyNewAssignment()
        var maxProcessedId = lastSeenId
        var anyHandled = false
        freshOnes.forEach { n ->
            val isD2L = n.kind == "d2l_assignment" ||
                n.kind == "d2l_announcement" ||
                n.kind == "d2l_content" ||
                n.kind == "d2l_quiz"
            if (isD2L) {
                if (!notifyVirtualNew) {
                    maxProcessedId = maxOf(maxProcessedId, n.id)
                    anyHandled = true
                    return@forEach
                }
                val prefix = when (n.kind) {
                    "d2l_announcement" -> "Novo aviso"
                    "d2l_content" -> "Novo material"
                    "d2l_quiz" -> "Novo questionário"
                    else -> "Nova atividade"
                }
                val title = "$prefix: ${n.disciplineName.ifBlank { "Sala Virtual" }}"
                val body = if (n.newValue.isNotBlank()) "${n.field} • ${n.newValue}" else n.field
                if (PlatformBridge.postSystemNotification(n.id.toInt(), title, body, openTab = AppTabIndex.VIRTUAL)) {
                    maxProcessedId = maxOf(maxProcessedId, n.id)
                    anyHandled = true
                }
                return@forEach
            }
            val title = if (n.kind == "grade") "Nova nota: ${n.disciplineName}" else "Atualização: ${n.disciplineName}"
            val oldTxt = if (n.oldValue.isBlank()) "—" else n.oldValue
            val body = "${n.field}: $oldTxt → ${n.newValue}"
            if (PlatformBridge.postSystemNotification(n.id.toInt(), title, body, openTab = AppTabIndex.CENTRAL)) {
                maxProcessedId = maxOf(maxProcessedId, n.id)
                anyHandled = true
            }
        }
        if (anyHandled) {
            PlatformBridge.putString(PrefKeys.LAST_NOTIFIED_ID, maxProcessedId.toString())
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
