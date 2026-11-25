package me.muriloscolari.unicep

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.muriloscolari.unicep.data.api.ReportCardResponse
import me.muriloscolari.unicep.data.api.StudentInfo
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualAnnouncement
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.repo.CalendarDate
import me.muriloscolari.unicep.data.repo.GlobalSearchResult
import me.muriloscolari.unicep.data.repo.GlobalSearchResultKind
import me.muriloscolari.unicep.data.repo.PortalRepository
import me.muriloscolari.unicep.data.repo.PrefKeys
import me.muriloscolari.unicep.data.repo.buildCalendarSubjectSuggestions
import me.muriloscolari.unicep.data.repo.buildSubjectVirtualLinks
import me.muriloscolari.unicep.data.repo.parseIsoDate
import me.muriloscolari.unicep.platform.AppUpdateInfo
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PlatformDocumentViewer
import me.muriloscolari.unicep.platform.PredictiveBackState
import me.muriloscolari.unicep.platform.WebShortcutAction
import me.muriloscolari.unicep.ui.designsystem.AppleActivityIndicator
import me.muriloscolari.unicep.ui.designsystem.AppleAlert
import me.muriloscolari.unicep.ui.designsystem.AppleMotion
import me.muriloscolari.unicep.ui.designsystem.BackOverlayController
import me.muriloscolari.unicep.ui.designsystem.GlassEdgeBarBackdrop
import me.muriloscolari.unicep.ui.designsystem.GlassSurface
import me.muriloscolari.unicep.ui.designsystem.LocalAppleContentInsets
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalBackOverlayController
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalGlassEnabled
import me.muriloscolari.unicep.ui.designsystem.LocalHazeState
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.LocalPredictiveBackProgress
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalTheme
import me.muriloscolari.unicep.ui.designsystem.predictiveBackSurfaceTransform
import me.muriloscolari.unicep.ui.screens.AcademicScreen
import me.muriloscolari.unicep.ui.screens.CalendarScreen
import me.muriloscolari.unicep.ui.screens.CentralScreen
import me.muriloscolari.unicep.ui.screens.LoginScreen
import me.muriloscolari.unicep.ui.screens.SemesterScreen
import me.muriloscolari.unicep.ui.screens.SplashLoadingScreen
import me.muriloscolari.unicep.ui.screens.VirtualScreen
import me.muriloscolari.unicep.ui.screens.currentLocalCalendarDate
import me.muriloscolari.unicep.ui.screens.isFrequencyAtLimit
import me.muriloscolari.unicep.ui.screens.mergeUnifiedSubjects
import me.muriloscolari.unicep.ui.util.toDisplayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(initialTab: Int = 0) {
    val repo = remember { PortalRepository() }
    val state by repo.state.collectAsState()
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(MainTab.entries.getOrElse(initialTab) { MainTab.SEMESTER }) }
    var courseSubTab by remember { mutableStateOf(0) }
    var selectedVirtualCourseId by remember { mutableStateOf<Long?>(null) }
    var virtualCourseSubTab by remember { mutableStateOf(0) }
    var selectedVirtualAssignment by remember { mutableStateOf<VirtualAssignmentItem?>(null) }
    var selectedVirtualAnnouncement by remember { mutableStateOf<VirtualAnnouncement?>(null) }
    var selectedSubjectCode by remember { mutableStateOf<String?>(null) }
    var showRiskyAttendanceModal by remember { mutableStateOf(false) }
    var showGlobalSearchModal by remember { mutableStateOf(false) }
    var showAllNotificationsModal by remember { mutableStateOf(false) }
    var confirmLogoutDialog by remember { mutableStateOf(false) }
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(PlatformBridge.isIgnoringBatteryOptimizations())
    }
    var selectedCalendarDate by remember { mutableStateOf(currentLocalCalendarDate()) }
    var showCalendarEditor by remember { mutableStateOf(false) }
    var editingCalendarEvent by remember { mutableStateOf<UserCalendarEvent?>(null) }
    var calendarDraftDate by remember { mutableStateOf<CalendarDate?>(null) }
    var confirmDeleteCalendarEvent by remember { mutableStateOf<UserCalendarEvent?>(null) }
    var suppressHistoryPush by remember { mutableStateOf(false) }
    val initialWebRoute = remember { PlatformBridge.readInitialNavigationRoute() }
    var appliedInitialWebRoute by remember { mutableStateOf(initialWebRoute.isNullOrBlank()) }
    val backOverlayController = remember { BackOverlayController() }
    var predictiveBackState by remember { mutableStateOf(PredictiveBackState()) }

    val mainPagerState = rememberPagerState(
        initialPage = currentTab.ordinal,
        pageCount = { MainTab.entries.size }
    )

    val calendarSubjectSuggestions = remember(state.grades, state.virtualOverview) {
        buildCalendarSubjectSuggestions(
            gradeNames = state.grades?.items.orEmpty().map { it.name.toDisplayTitle() },
            virtualCourseNames = state.virtualOverview?.courses.orEmpty().map { it.name.toDisplayTitle() }
        )
    }

    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    val semesterListState = rememberLazyListState()
    val courseListState = rememberLazyListState()
    val virtualListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()
    val centralListState = rememberLazyListState()

    fun activeListStateFor(tab: MainTab): LazyListState = when (tab) {
        MainTab.SEMESTER -> semesterListState
        MainTab.COURSE -> courseListState
        MainTab.VIRTUAL -> virtualListState
        MainTab.CALENDAR -> calendarListState
        MainTab.CENTRAL -> centralListState
    }

    var tabBarMinimized by remember { mutableStateOf(false) }

    fun checkAppUpdate(manual: Boolean) {
        if (PlatformBridge.capabilities().isWeb || updateChecking) return
        scope.launch {
            updateChecking = true
            if (manual) updateStatusMessage = null
            val info = PlatformBridge.checkForAppUpdate()
            updateChecking = false
            if (info != null) {
                availableUpdate = info
                updateStatusMessage = "Nova versão v${info.latestVersion} disponível no Forgejo."
                showUpdateDialog = true
            } else if (manual) {
                availableUpdate = null
                updateStatusMessage = "Você já está na versão mais recente (v${PlatformBridge.currentAppVersion()})."
            }
        }
    }

    fun installAppUpdate() {
        val info = availableUpdate ?: return
        if (updateDownloading) return
        scope.launch {
            updateDownloading = true
            updateProgress = 0f
            updateStatusMessage = "Baixando v${info.latestVersion}..."
            val res = PlatformBridge.downloadAndInstallAppUpdate(info.apkUrl) { progress ->
                updateProgress = progress
            }
            updateDownloading = false
            res.onSuccess {
                updateStatusMessage = "Instalador do Android aberto."
                showUpdateDialog = false
            }.onFailure { err ->
                updateStatusMessage = err.message ?: "Não foi possível instalar a atualização."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!PlatformBridge.capabilities().isWeb) {
            checkAppUpdate(manual = false)
        }
    }

    val navHaptics = LocalHapticFeedback.current

    fun openVirtualCourse(orgUnitId: Long, subTab: Int = 0) {
        selectedSubjectCode = null
        showRiskyAttendanceModal = false
        showGlobalSearchModal = false
        showAllNotificationsModal = false
        showCalendarEditor = false
        editingCalendarEvent = null
        selectedVirtualAssignment = null
        selectedVirtualAnnouncement = null
        tabBarMinimized = false
        currentTab = MainTab.VIRTUAL
        selectedVirtualCourseId = orgUnitId
        virtualCourseSubTab = subTab
    }

    fun openVirtualAssignment(item: VirtualAssignmentItem) {
        selectedSubjectCode = null
        showRiskyAttendanceModal = false
        showGlobalSearchModal = false
        tabBarMinimized = false
        currentTab = MainTab.VIRTUAL
        selectedVirtualCourseId = item.orgUnitId
        virtualCourseSubTab = 0 // Atividades
        selectedVirtualAssignment = item
    }

    fun navigateToTab(tab: MainTab) {
        navHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val sameTabRoot = tab == currentTab &&
            selectedSubjectCode == null &&
            !showRiskyAttendanceModal &&
            !showGlobalSearchModal &&
            !showAllNotificationsModal &&
            !showCalendarEditor &&
            confirmDeleteCalendarEvent == null &&
            selectedVirtualCourseId == null &&
            selectedVirtualAssignment == null &&
            selectedVirtualAnnouncement == null
        if (sameTabRoot) {
            tabBarMinimized = false
            scope.launch { activeListStateFor(tab).animateScrollToItem(0) }
        } else {
            selectedSubjectCode = null
            showRiskyAttendanceModal = false
            showGlobalSearchModal = false
            showAllNotificationsModal = false
            showCalendarEditor = false
            editingCalendarEvent = null
            confirmDeleteCalendarEvent = null
            selectedVirtualCourseId = null
            virtualCourseSubTab = 0
            selectedVirtualAssignment = null
            selectedVirtualAnnouncement = null
            tabBarMinimized = false
            currentTab = tab
        }
    }

    // Sincronização bidirecional entre currentTab e HorizontalPager principal
    LaunchedEffect(currentTab) {
        if (mainPagerState.currentPage != currentTab.ordinal) {
            mainPagerState.animateScrollToPage(currentTab.ordinal)
        }
    }
    LaunchedEffect(mainPagerState) {
        snapshotFlow { mainPagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                val target = MainTab.entries.getOrNull(settled) ?: return@collect
                if (target != currentTab) {
                    navHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    tabBarMinimized = false
                    currentTab = target
                }
            }
    }

    fun refreshCurrentTab() {
        scope.launch {
            when (currentTab) {
                MainTab.VIRTUAL -> {
                    val ou = selectedVirtualCourseId
                    if (ou != null) repo.loadVirtualCourse(ou, force = true)
                    else repo.loadVirtualOverviewIfNeeded(force = true)
                }
                MainTab.COURSE -> repo.loadAcademicIfNeeded(force = true)
                MainTab.CALENDAR -> repo.loadCalendarContextIfNeeded(force = true)
                MainTab.CENTRAL -> repo.loadFinanceAndServicesIfNeeded(force = true)
                MainTab.SEMESTER -> {
                    repo.refreshDashboard()
                    repo.preloadHomeContextIfNeeded(force = true)
                }
            }
        }
    }

    fun handleGlobalSearchResult(result: GlobalSearchResult) {
        showGlobalSearchModal = false
        when (result.kind) {
            GlobalSearchResultKind.SUBJECT -> {
                currentTab = MainTab.SEMESTER
                showRiskyAttendanceModal = false
                selectedSubjectCode = result.subjectCode
            }
            GlobalSearchResultKind.SCHEDULE,
            GlobalSearchResultKind.CURRICULUM -> {
                currentTab = MainTab.COURSE
                courseSubTab = result.courseSubTab ?: 0
            }
            GlobalSearchResultKind.VIRTUAL_COURSE -> {
                result.virtualCourseId?.let { openVirtualCourse(it, subTab = 0) }
            }
            GlobalSearchResultKind.VIRTUAL_ASSIGNMENT -> {
                result.virtualAssignment?.let { openVirtualAssignment(it) }
                    ?: result.virtualCourseId?.let { openVirtualCourse(it, subTab = 0) }
            }
            GlobalSearchResultKind.VIRTUAL_ANNOUNCEMENT -> {
                currentTab = MainTab.VIRTUAL
                selectedVirtualCourseId = result.virtualCourseId
                virtualCourseSubTab = 3 // Mais (Avisos)
                selectedVirtualAnnouncement = result.virtualAnnouncement
            }
            GlobalSearchResultKind.VIRTUAL_MATERIAL -> {
                val topic = result.virtualTopic
                val cName = result.virtualCourseName ?: "Sala Virtual"
                when {
                    topic != null && topic.downloadPath.isNotBlank() -> {
                        scope.launch { repo.openD2LFile(topic.downloadPath, topic.title, cName, topic.title) }
                    }
                    topic != null && (topic.externalUrl.isNotBlank() || topic.url.isNotBlank()) -> {
                        repo.openSalaVirtualWeb(topic.externalUrl.ifBlank { topic.url })
                    }
                    else -> {
                        result.virtualCourseId?.let { openVirtualCourse(it, subTab = 1) }
                    }
                }
            }
            GlobalSearchResultKind.CALENDAR_EVENT -> {
                currentTab = MainTab.CALENDAR
                result.calendarDateIso?.let { parseIsoDate(it) }?.let { selectedCalendarDate = it }
            }
        }
    }

    fun encodeRoute(): String = when (currentTab) {
        MainTab.SEMESTER -> when {
            showGlobalSearchModal -> "semestre/busca"
            !selectedSubjectCode.isNullOrBlank() -> "semestre/subject/$selectedSubjectCode"
            showRiskyAttendanceModal -> "semestre/frequencia-limite"
            else -> "semestre"
        }
        MainTab.COURSE -> "curso/$courseSubTab"
        MainTab.VIRTUAL -> {
            val ou = selectedVirtualCourseId
            if (ou != null) "virtual/course/$ou/$virtualCourseSubTab" else "virtual"
        }
        MainTab.CALENDAR -> "calendario"
        MainTab.CENTRAL -> if (showAllNotificationsModal) "central/alertas" else "central"
    }

    fun applyPopRoute(route: String) {
        suppressHistoryPush = true
        when {
            route == "semestre/busca" -> {
                currentTab = MainTab.SEMESTER
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
                showGlobalSearchModal = true
            }
            route.startsWith("semestre/subject/") -> {
                currentTab = MainTab.SEMESTER
                showRiskyAttendanceModal = false
                showGlobalSearchModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
                selectedSubjectCode = route.removePrefix("semestre/subject/").ifBlank { null }
            }
            route == "semestre/frequencia-limite" -> {
                currentTab = MainTab.SEMESTER
                selectedSubjectCode = null
                showGlobalSearchModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
                showRiskyAttendanceModal = true
            }
            route == "semestre" -> {
                currentTab = MainTab.SEMESTER
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showGlobalSearchModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
            }
            route.startsWith("curso") -> {
                currentTab = MainTab.COURSE
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
                courseSubTab = route.substringAfter('/', "0").toIntOrNull()?.coerceIn(0, 2) ?: 0
            }
            route.startsWith("virtual/course/") -> {
                currentTab = MainTab.VIRTUAL
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
                val parts = route.removePrefix("virtual/course/").split('/')
                selectedVirtualCourseId = parts.getOrNull(0)?.toLongOrNull()
                virtualCourseSubTab = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 3) ?: 0
            }
            route == "virtual" -> {
                currentTab = MainTab.VIRTUAL
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualCourseId = null
                virtualCourseSubTab = 0
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
            }
            route == "calendario" -> {
                currentTab = MainTab.CALENDAR
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showGlobalSearchModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
            }
            route == "central/alertas" -> {
                currentTab = MainTab.CENTRAL
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
                showAllNotificationsModal = true
            }
            route == "central" -> {
                currentTab = MainTab.CENTRAL
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
                showCalendarEditor = false
                editingCalendarEvent = null
                selectedVirtualAssignment = null
                selectedVirtualAnnouncement = null
            }
            else -> suppressHistoryPush = false
        }
    }

    fun dismissTopOverlay(): Boolean = when {
        state.activeDocumentViewer != null -> {
            repo.closeDocumentViewer()
            true
        }
        backOverlayController.popTop() -> true
        showUpdateDialog && !updateDownloading -> {
            showUpdateDialog = false
            true
        }
        confirmLogoutDialog -> {
            confirmLogoutDialog = false
            true
        }
        confirmDeleteCalendarEvent != null -> {
            confirmDeleteCalendarEvent = null
            true
        }
        showCalendarEditor -> {
            showCalendarEditor = false
            editingCalendarEvent = null
            true
        }
        showGlobalSearchModal -> {
            showGlobalSearchModal = false
            true
        }
        selectedVirtualAssignment != null -> {
            selectedVirtualAssignment = null
            true
        }
        selectedVirtualAnnouncement != null -> {
            selectedVirtualAnnouncement = null
            true
        }
        showAllNotificationsModal -> {
            showAllNotificationsModal = false
            true
        }
        selectedSubjectCode != null -> {
            selectedSubjectCode = null
            true
        }
        showRiskyAttendanceModal -> {
            showRiskyAttendanceModal = false
            true
        }
        else -> false
    }

    LaunchedEffect(Unit) {
        repo.tryAutoLogin()
    }

    val hasAnyOverlay = state.activeDocumentViewer != null ||
        backOverlayController.hasOverlay ||
        confirmLogoutDialog ||
        confirmDeleteCalendarEvent != null ||
        showCalendarEditor ||
        showUpdateDialog ||
        showGlobalSearchModal ||
        showAllNotificationsModal ||
        selectedSubjectCode != null ||
        showRiskyAttendanceModal ||
        selectedVirtualAssignment != null ||
        selectedVirtualAnnouncement != null
    val canPopInternally = state.loggedIn && (
        hasAnyOverlay ||
            (currentTab == MainTab.COURSE && courseSubTab > 0) ||
            (currentTab == MainTab.VIRTUAL && selectedVirtualCourseId != null) ||
            currentTab != MainTab.SEMESTER
        )

    LaunchedEffect(canPopInternally) {
        PlatformBridge.setAndroidBackEnabled(canPopInternally)
    }

    DisposableEffect(Unit) {
        PlatformBridge.initNavigationHistory(MainTab.entries.getOrElse(initialTab) { MainTab.SEMESTER }.route)
        PlatformBridge.setPopRouteListener { route -> applyPopRoute(route) }
        PlatformBridge.setOpenTabListener { tab ->
            MainTab.entries.getOrNull(tab)?.let { targetTab ->
                navigateToTab(targetTab)
            }
        }
        PlatformBridge.setAppResumeListener {
            ignoringBatteryOptimizations = PlatformBridge.isIgnoringBatteryOptimizations()
            if (state.loggedIn) {
                scope.launch {
                    when (currentTab) {
                        MainTab.VIRTUAL -> repo.refreshVirtualOnResume(selectedVirtualCourseId)
                        MainTab.SEMESTER -> repo.preloadHomeContextIfNeeded()
                        MainTab.CALENDAR -> repo.loadCalendarContextIfNeeded()
                        else -> Unit
                    }
                }
            }
        }
        PlatformBridge.setWebKeyShortcutListener { action ->
            when (action) {
                WebShortcutAction.ESCAPE -> dismissTopOverlay()
                WebShortcutAction.GLOBAL_SEARCH -> {
                    if (state.loggedIn) {
                        showGlobalSearchModal = !showGlobalSearchModal
                        true
                    } else false
                }
                WebShortcutAction.TAB_1,
                WebShortcutAction.TAB_2,
                WebShortcutAction.TAB_3,
                WebShortcutAction.TAB_4,
                WebShortcutAction.TAB_5 -> {
                    if (state.loggedIn) {
                        val idx = when (action) {
                            WebShortcutAction.TAB_1 -> 0
                            WebShortcutAction.TAB_2 -> 1
                            WebShortcutAction.TAB_3 -> 2
                            WebShortcutAction.TAB_4 -> 3
                            WebShortcutAction.TAB_5 -> 4
                            else -> 0
                        }
                        MainTab.entries.getOrNull(idx)?.let { navigateToTab(it) }
                        true
                    } else false
                }
            }
        }
        PlatformBridge.setAndroidPredictiveBackListener { predictiveBackState = it }
        PlatformBridge.setAndroidBackHandler {
            when {
                dismissTopOverlay() -> true
                currentTab == MainTab.VIRTUAL && selectedVirtualCourseId != null -> {
                    if (virtualCourseSubTab > 0) {
                        virtualCourseSubTab = 0
                    } else {
                        selectedVirtualCourseId = null
                    }
                    true
                }
                currentTab == MainTab.COURSE && courseSubTab > 0 -> {
                    courseSubTab = 0
                    true
                }
                currentTab != MainTab.SEMESTER -> {
                    currentTab = MainTab.SEMESTER
                    true
                }
                else -> false
            }
        }
        onDispose {
            PlatformBridge.setWebKeyShortcutListener(null)
            PlatformBridge.setAndroidPredictiveBackListener(null)
            PlatformBridge.setAndroidBackEnabled(false)
            PlatformBridge.setAndroidBackHandler(null)
            PlatformBridge.setOpenTabListener(null)
            PlatformBridge.setAppResumeListener(null)
        }
    }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn && PlatformBridge.supportsBackgroundBatterySettings()) {
            ignoringBatteryOptimizations = PlatformBridge.isIgnoringBatteryOptimizations()
        }
    }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn && !appliedInitialWebRoute) {
            appliedInitialWebRoute = true
            initialWebRoute?.let { applyPopRoute(it) }
        }
    }

    LaunchedEffect(state.loggedIn, currentTab, selectedVirtualCourseId, state.virtualOverview, showGlobalSearchModal) {
        val pageTitle = when {
            !state.loggedIn -> "Entrar · UNICEP Portal"
            showGlobalSearchModal -> "Busca · UNICEP Portal"
            currentTab == MainTab.VIRTUAL && selectedVirtualCourseId != null -> {
                val courseName = state.virtualOverview?.courses
                    ?.firstOrNull { it.orgUnitId == selectedVirtualCourseId }
                    ?.name
                    ?.toDisplayTitle()
                    ?.takeIf { it.isNotBlank() }
                if (courseName != null) "$courseName · UNICEP Portal" else "Sala Virtual · UNICEP Portal"
            }
            else -> "${currentTab.label} · UNICEP Portal"
        }
        PlatformBridge.setPageTitle(pageTitle)
    }

    LaunchedEffect(
        currentTab,
        courseSubTab,
        selectedVirtualCourseId,
        virtualCourseSubTab,
        selectedSubjectCode,
        showRiskyAttendanceModal,
        showGlobalSearchModal,
        showAllNotificationsModal,
        state.loggedIn,
        appliedInitialWebRoute
    ) {
        if (!state.loggedIn || !appliedInitialWebRoute) return@LaunchedEffect
        val route = encodeRoute()
        if (suppressHistoryPush) {
            suppressHistoryPush = false
        } else {
            PlatformBridge.pushNavigationRoute(route)
        }
    }

    LaunchedEffect(currentTab, state.loggedIn) {
        if (!state.loggedIn) return@LaunchedEffect
        when (currentTab) {
            MainTab.COURSE -> repo.loadAcademicIfNeeded()
            MainTab.VIRTUAL -> repo.loadVirtualOverviewIfNeeded()
            MainTab.CALENDAR -> repo.loadCalendarContextIfNeeded()
            MainTab.CENTRAL -> repo.loadFinanceAndServicesIfNeeded()
            MainTab.SEMESTER -> repo.preloadHomeContextIfNeeded()
        }
    }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn && PlatformBridge.capabilities().isWeb) {
            var tick = 0
            while (true) {
                repo.evaluateDueCalendarReminders()
                if (tick > 0 && tick % 5 == 0) {
                    repo.refreshNotificationsSilently()
                }
                tick++
                delay(60_000L)
            }
        }
    }

    LaunchedEffect(selectedVirtualCourseId, state.loggedIn) {
        val ou = selectedVirtualCourseId ?: return@LaunchedEffect
        if (state.loggedIn) {
            repo.loadVirtualCourse(ou)
        }
    }

    val effectiveReport = state.report ?: state.dashboard?.currentReport ?: ReportCardResponse()
    val gradeItems = state.grades?.items.orEmpty()
    val mergedSubjects = remember(gradeItems, effectiveReport.items) {
        mergeUnifiedSubjects(gradeItems, effectiveReport)
    }
    val subjectVirtualLinks = remember(mergedSubjects, state.virtualOverview, state.virtualCourseDetails) {
        buildSubjectVirtualLinks(mergedSubjects, state.virtualOverview, state.virtualCourseDetails)
    }
    val riskySubjects = remember(mergedSubjects) {
        mergedSubjects
            .filter { it.report?.isFrequencyAtLimit() == true }
            .sortedBy { it.report?.remainingAbsences ?: Int.MAX_VALUE }
    }
    val activeSubject = mergedSubjects.firstOrNull { it.code == selectedSubjectCode }
    val unreadCount = state.notifications.count { it.readAt == null }
    val allowMainSwipe = !hasAnyOverlay && !(currentTab == MainTab.VIRTUAL && selectedVirtualCourseId != null)

    val renderTabPager: @Composable () -> Unit = {
        HorizontalPager(
            state = mainPagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = allowMainSwipe,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            when (MainTab.entries.getOrElse(pageIndex) { MainTab.SEMESTER }) {
                MainTab.SEMESTER -> SemesterScreen(
                    dashboard = state.dashboard,
                    periods = state.dashboard?.gradePeriods.orEmpty(),
                    selectedPeriodId = state.selectedGradePeriodId,
                    grades = gradeItems,
                    report = effectiveReport,
                    unreadNotifications = state.notifications.filter { it.readAt == null },
                    selectedSubjectCode = selectedSubjectCode,
                    showRiskyAttendance = showRiskyAttendanceModal,
                    onSelectPeriod = { id ->
                        selectedSubjectCode = null
                        showRiskyAttendanceModal = false
                        scope.launch { repo.selectSemesterPeriod(id) }
                    },
                    onOpenSubject = { code ->
                        showRiskyAttendanceModal = false
                        selectedSubjectCode = code
                    },
                    onCloseSubject = { selectedSubjectCode = null },
                    onOpenRiskyAttendance = {
                        selectedSubjectCode = null
                        showRiskyAttendanceModal = true
                    },
                    onCloseRiskyAttendance = { showRiskyAttendanceModal = false },
                    onOpenNotifications = { navigateToTab(MainTab.CENTRAL) },
                    freqAlertThreshold = state.freqAlertThreshold,
                    schedule = state.schedule,
                    virtualOverview = state.virtualOverview,
                    virtualCourseDetails = state.virtualCourseDetails,
                    onOpenSearch = { showGlobalSearchModal = true },
                    onOpenSchedule = {
                        currentTab = MainTab.COURSE
                        courseSubTab = 2
                    },
                    onOpenVirtualAssignment = ::openVirtualAssignment,
                    onOpenVirtualCourse = { ou -> openVirtualCourse(ou, subTab = 0) },
                    onOpenVirtualTab = { navigateToTab(MainTab.VIRTUAL) },
                    loading = state.loading,
                    listState = semesterListState,
                    renderSheetsInPlace = false
                )

                MainTab.COURSE -> AcademicScreen(
                    curriculum = state.curriculum,
                    schedule = state.schedule,
                    selectedSubTab = courseSubTab,
                    onSelectSubTab = { courseSubTab = it },
                    onOpenProxy = { path -> scope.launch { repo.openPortalLink(path, true) } },
                    student = state.dashboard?.student ?: StudentInfo(),
                    loading = state.loading,
                    listState = courseListState
                )

                MainTab.VIRTUAL -> VirtualScreen(
                    overview = state.virtualOverview,
                    courseDetails = state.virtualCourseDetails,
                    selectedCourseId = selectedVirtualCourseId,
                    courseSubTab = virtualCourseSubTab,
                    overviewTab = state.virtualOverviewTab,
                    onSelectOverviewTab = { repo.setVirtualOverviewTab(it) },
                    searchQuery = state.virtualSearchQuery,
                    onSearchQueryChange = { repo.setVirtualSearchQuery(it) },
                    selectedSemester = state.virtualSelectedSemester,
                    onSelectSemester = { repo.setVirtualSelectedSemester(it) },
                    assignmentFilterIndex = state.virtualAssignmentFilterIndex,
                    onSelectAssignmentFilter = { repo.setVirtualAssignmentFilterIndex(it) },
                    loading = state.virtualLoading,
                    error = state.virtualError,
                    onRefreshOverview = { scope.launch { repo.loadVirtualOverviewIfNeeded(force = true) } },
                    onSelectCourse = { ou ->
                        virtualCourseSubTab = 0
                        selectedVirtualCourseId = ou
                    },
                    onBackToOverview = {
                        selectedVirtualCourseId = null
                        virtualCourseSubTab = 0
                    },
                    onSelectCourseSubTab = { virtualCourseSubTab = it },
                    onRefreshCourse = { ou -> scope.launch { repo.loadVirtualCourse(ou, force = true) } },
                    onSelectAssignment = { selectedVirtualAssignment = it },
                    onSelectAnnouncement = { selectedVirtualAnnouncement = it },
                    onViewD2LFile = { path, title, courseName, fileName ->
                        scope.launch { repo.openD2LFile(path, title, courseName, fileName) }
                    },
                    onDownloadD2LFile = { path, fileName, courseName ->
                        scope.launch { repo.downloadD2LFile(path, fileName, courseName) }
                    },
                    onDownloadBatch = { items ->
                        scope.launch { repo.downloadD2LFilesBatch(items) }
                    },
                    onOpenWeb = { url -> repo.openSalaVirtualWeb(url) },
                    downloadFolderLabel = state.downloadFolderLabel,
                    onPickDownloadFolder = { scope.launch { repo.pickDownloadFolder() } },
                    downloadingFileKey = state.downloadingFileKey,
                    batchDownloadDone = state.batchDownloadDone,
                    batchDownloadTotal = state.batchDownloadTotal,
                    listState = virtualListState,
                    selectedAssignment = selectedVirtualAssignment,
                    selectedAnnouncement = selectedVirtualAnnouncement,
                    onDismissAssignment = { selectedVirtualAssignment = null },
                    onDismissAnnouncement = { selectedVirtualAnnouncement = null },
                    renderSheetsInPlace = false
                )

                MainTab.CALENDAR -> CalendarScreen(
                    userEvents = state.calendarEvents,
                    virtualOverview = state.virtualOverview,
                    virtualCourseDetails = state.virtualCourseDetails,
                    selectedDate = selectedCalendarDate,
                    onSelectDate = { selectedCalendarDate = it },
                    onAddEvent = { date ->
                        calendarDraftDate = date
                        editingCalendarEvent = null
                        showCalendarEditor = true
                    },
                    onEditEvent = { ev ->
                        editingCalendarEvent = ev
                        calendarDraftDate = null
                        showCalendarEditor = true
                    },
                    onRequestDeleteEvent = { ev -> confirmDeleteCalendarEvent = ev },
                    onOpenVirtualAssignment = ::openVirtualAssignment,
                    onOpenWeb = { url -> repo.openSalaVirtualWeb(url) },
                    loading = state.calendarLoading,
                    listState = calendarListState
                )

                MainTab.CENTRAL -> CentralScreen(
                    dashboard = state.dashboard,
                    finance = state.finance,
                    services = state.services,
                    notifications = state.notifications,
                    stylePreference = state.stylePreference,
                    onSelectStyle = { repo.setStylePreference(it) },
                    onOpenAva = { scope.launch { repo.openAvaSso() } },
                    onOpenLink = { path, proxy -> scope.launch { repo.openPortalLink(path, proxy) } },
                    onSyncNotifications = { scope.launch { repo.syncNotificationsNow() } },
                    onMarkAllRead = { scope.launch { repo.markAllNotificationsRead() } },
                    onTestNotification = { scope.launch { repo.sendTestNotification() } },
                    freqAlertThreshold = state.freqAlertThreshold,
                    onSelectFreqAlertThreshold = { repo.setFrequencyAlertThreshold(it) },
                    bgSyncIntervalMinutes = state.bgSyncIntervalMinutes,
                    onSelectBgSyncInterval = { repo.setBackgroundSyncIntervalMinutes(it) },
                    virtualNotifyNewAssignment = state.virtualNotifyNewAssignment,
                    onToggleVirtualNotifyNewAssignment = { scope.launch { repo.setVirtualNotifyNewAssignment(it) } },
                    virtualDeadlineLeadHours = state.virtualDeadlineLeadHours,
                    onToggleVirtualDeadlineLeadHour = { repo.toggleVirtualDeadlineLeadHour(it) },
                    onAddCustomVirtualDeadlineHour = { repo.addVirtualDeadlineLeadHour(it) },
                    downloadFolderLabel = state.downloadFolderLabel,
                    onPickDownloadFolder = { scope.launch { repo.pickDownloadFolder() } },
                    onResetDownloadFolder = { repo.clearDownloadFolder() },
                    availableUpdate = availableUpdate,
                    updateChecking = updateChecking,
                    updateDownloading = updateDownloading,
                    updateProgress = updateProgress,
                    updateStatusMessage = updateStatusMessage,
                    onCheckUpdate = { checkAppUpdate(manual = true) },
                    onInstallUpdate = { installAppUpdate() },
                    ignoringBatteryOptimizations = ignoringBatteryOptimizations,
                    onRequestBackgroundBattery = { PlatformBridge.openAppBackgroundSettings() },
                    onLogout = { scope.launch { repo.logout() } },
                    loading = state.loading,
                    listState = centralListState,
                    showAllNotifications = showAllNotificationsModal,
                    onOpenAllNotifications = { showAllNotificationsModal = true },
                    onCloseAllNotifications = { showAllNotificationsModal = false },
                    renderSheetsInPlace = false,
                    onRequestLogout = { confirmLogoutDialog = true }
                )
            }
        }
    }

    val renderOverlays: @Composable () -> Unit = {
        AppSheetsAndDialogsHost(
            state = state,
            mergedSubjects = mergedSubjects,
            activeSubject = activeSubject,
            activeSubjectVirtualLink = activeSubject?.let { subjectVirtualLinks[it.code] },
            riskySubjects = riskySubjects,
            selectedVirtualAssignment = selectedVirtualAssignment,
            selectedVirtualAnnouncement = selectedVirtualAnnouncement,
            showRiskyAttendanceModal = showRiskyAttendanceModal,
            showAllNotificationsModal = showAllNotificationsModal,
            showCalendarEditor = showCalendarEditor,
            editingCalendarEvent = editingCalendarEvent,
            calendarDraftDate = calendarDraftDate,
            selectedCalendarDate = selectedCalendarDate,
            calendarSubjectSuggestions = calendarSubjectSuggestions,
            showGlobalSearchModal = showGlobalSearchModal,
            confirmDeleteCalendarEvent = confirmDeleteCalendarEvent,
            confirmLogoutDialog = confirmLogoutDialog,
            showUpdateDialog = showUpdateDialog,
            availableUpdate = availableUpdate,
            updateDownloading = updateDownloading,
            updateProgress = updateProgress,
            updateStatusMessage = updateStatusMessage,
            onViewD2LFile = { path, title, courseName, fileName ->
                scope.launch { repo.openD2LFile(path, title, courseName, fileName) }
            },
            onDownloadD2LFile = { path, fileName, courseName ->
                scope.launch { repo.downloadD2LFile(path, fileName, courseName) }
            },
            onDownloadBatch = { items ->
                scope.launch { repo.downloadD2LFilesBatch(items) }
            },
            onOpenSalaVirtualWeb = { url -> repo.openSalaVirtualWeb(url) },
            onDismissVirtualAssignment = { selectedVirtualAssignment = null },
            onDismissVirtualAnnouncement = { selectedVirtualAnnouncement = null },
            onDismissSubjectDetail = { selectedSubjectCode = null },
            onOpenVirtualCourseFromSubject = { ou -> openVirtualCourse(ou, subTab = 0) },
            onOpenVirtualAssignmentFromSubject = ::openVirtualAssignment,
            onOpenVirtualTabFromSubject = { navigateToTab(MainTab.VIRTUAL) },
            onSelectRiskySubject = { code ->
                showRiskyAttendanceModal = false
                selectedSubjectCode = code
            },
            onDismissRiskyAttendance = { showRiskyAttendanceModal = false },
            onDismissAllNotifications = { showAllNotificationsModal = false },
            onSaveCalendarEvent = { input ->
                scope.launch {
                    val editing = editingCalendarEvent
                    val ok = if (editing == null) {
                        repo.createCalendarEvent(input)
                    } else {
                        repo.updateCalendarEvent(editing.id, input)
                    }
                    if (ok) {
                        parseIsoDate(input.date)?.let { selectedCalendarDate = it }
                        showCalendarEditor = false
                        editingCalendarEvent = null
                    }
                }
            },
            onRequestDeleteCalendarEvent = { ev -> confirmDeleteCalendarEvent = ev },
            onDismissCalendarEditor = {
                showCalendarEditor = false
                editingCalendarEvent = null
            },
            onSelectGlobalSearchResult = ::handleGlobalSearchResult,
            onDismissGlobalSearch = { showGlobalSearchModal = false },
            onConfirmDeleteCalendarEvent = { targetEvent ->
                confirmDeleteCalendarEvent = null
                scope.launch {
                    if (repo.deleteCalendarEvent(targetEvent.id)) {
                        if (editingCalendarEvent?.id == targetEvent.id) {
                            showCalendarEditor = false
                            editingCalendarEvent = null
                        }
                    }
                }
            },
            onDismissDeleteCalendarEvent = { confirmDeleteCalendarEvent = null },
            onConfirmLogout = {
                confirmLogoutDialog = false
                scope.launch { repo.logout() }
            },
            onDismissLogout = { confirmLogoutDialog = false },
            onInstallAppUpdate = { installAppUpdate() },
            onDismissUpdateDialog = { showUpdateDialog = false }
        )
    }

    CompositionLocalProvider(
        LocalPredictiveBackProgress provides { predictiveBackState },
        LocalBackOverlayController provides backOverlayController
    ) {
        PortalTheme(stylePreference = state.stylePreference) {
            val colors = LocalPortalColors.current
            val style = LocalDesignStyle.current

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.initializing -> SplashLoadingScreen()
                    !state.loggedIn -> LoginScreen(
                        initialServerUrl = state.serverUrl,
                        loading = state.loading,
                        error = state.error,
                        onLogin = { url, ra, pass, perfil ->
                            scope.launch { repo.login(url, ra, pass, perfil) }
                        }
                    )

                    style != DesignStyle.APPLE -> {
                        // Material Design 3 (Material You)
                        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
                            val isDesktop = maxWidth >= 720.dp
                            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                            val topBar: @Composable () -> Unit = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.background)
                                        .then(if (!isDesktop) Modifier.padding(top = statusTop) else Modifier)
                                ) {
                                    if (state.loading) {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().height(2.5.dp),
                                            color = colors.accent,
                                            trackColor = colors.accentSoft.copy(alpha = 0.5f)
                                        )
                                    }
                                    val banner = state.statusBanner ?: state.error
                                    if (!banner.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (state.error != null) colors.red.copy(alpha = 0.14f) else colors.accentSoft)
                                                .clickable { repo.clearBanner() }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = banner,
                                                color = if (state.error != null) colors.red else colors.accent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("OK", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (state.isOffline) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(colors.orange.copy(alpha = 0.14f))
                                                .clickable { scope.launch { repo.refreshDashboard() } }
                                                .padding(horizontal = 16.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Modo offline · ${formatOfflineAge(state.lastSyncAtMillis)}",
                                                color = colors.orange,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Atualizar", color = colors.orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            val refreshing = state.loading || state.virtualLoading || state.calendarLoading
                            val bodyContent: @Composable () -> Unit = {
                                val pullState = rememberPullToRefreshState()
                                PullToRefreshBox(
                                    isRefreshing = refreshing,
                                    onRefresh = { refreshCurrentTab() },
                                    state = pullState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    renderTabPager()
                                }
                            }

                            val navItemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.accent,
                                indicatorColor = colors.accentSoft,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary
                            )
                            val railItemColors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.accent,
                                indicatorColor = colors.accentSoft,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary
                            )

                            if (isDesktop) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    NavigationRail(containerColor = colors.surface, contentColor = colors.textPrimary) {
                                        Spacer(Modifier.height(14.dp))
                                        IconButton(
                                            onClick = {
                                                navHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showGlobalSearchModal = true
                                            },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            Icon(
                                                imageVector = PortalIcons.Search,
                                                contentDescription = "Busca global (Ctrl+K)",
                                                tint = colors.accent
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                navHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                refreshCurrentTab()
                                            },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            if (refreshing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = colors.accent
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = PortalIcons.Refresh,
                                                    contentDescription = "Atualizar",
                                                    tint = colors.textPrimary
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        PortalDivider(Modifier.width(40.dp))
                                        Spacer(Modifier.height(8.dp))
                                        MainTab.entries.forEach { tab ->
                                            val selected = tab == currentTab
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { navigateToTab(tab) },
                                                colors = railItemColors,
                                                icon = {
                                                    BadgedBox(
                                                        badge = {
                                                            if (tab == MainTab.CENTRAL && unreadCount > 0) {
                                                                Badge(containerColor = colors.red, contentColor = Color.White) {
                                                                    Text(if (unreadCount > 9) "9+" else unreadCount.toString())
                                                                }
                                                            }
                                                        }
                                                    ) {
                                                        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label)
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        tab.label,
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        topBar()
                                        Box(modifier = Modifier.weight(1f)) { bodyContent() }
                                    }
                                }
                            } else {
                                Scaffold(
                                    containerColor = colors.background,
                                    topBar = topBar,
                                    bottomBar = {
                                        NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                                            MainTab.entries.forEach { tab ->
                                                val selected = tab == currentTab
                                                NavigationBarItem(
                                                    selected = selected,
                                                    onClick = { navigateToTab(tab) },
                                                    colors = navItemColors,
                                                    icon = {
                                                        BadgedBox(
                                                            badge = {
                                                                if (tab == MainTab.CENTRAL && unreadCount > 0) {
                                                                    Badge(containerColor = colors.red, contentColor = Color.White) {
                                                                        Text(if (unreadCount > 9) "9+" else unreadCount.toString())
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label)
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            tab.label,
                                                            fontSize = 10.5.sp,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                            .predictiveBackSurfaceTransform(
                                                readBack = { if (!hasAnyOverlay) predictiveBackState else PredictiveBackState() },
                                                slideDp = 42f,
                                                verticalSlideDp = 0f,
                                                scaleDrop = 0.055f,
                                                pivotY = 0.5f
                                            )
                                    ) {
                                        bodyContent()
                                    }
                                }
                            }

                            renderOverlays()
                        }
                    }

                    else -> {
                        // Modo Apple Liquid Glass
                        val hazeState = rememberHazeState()
                        val glassEnabled = remember(state.stylePreference) {
                            PlatformBridge.capabilities().supportsAppleEffects
                        }
                        val activeListState = activeListStateFor(currentTab)

                        val collapseProgress by remember(activeListState) {
                            derivedStateOf {
                                if (activeListState.firstVisibleItemIndex > 0) 1f
                                else (activeListState.firstVisibleItemScrollOffset / 110f).coerceIn(0f, 1f)
                            }
                        }
                        val smoothCollapseState = animateFloatAsState(collapseProgress, tween(120))

                        val atTop by remember(activeListState) {
                            derivedStateOf {
                                activeListState.firstVisibleItemIndex == 0 && activeListState.firstVisibleItemScrollOffset < 24
                            }
                        }
                        LaunchedEffect(atTop) {
                            if (atTop) tabBarMinimized = false
                        }

                        var scrollAccumulator by remember { mutableFloatStateOf(0f) }
                        val tabScrollConnection = remember(activeListState) {
                            object : NestedScrollConnection {
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    val dy = available.y
                                    if (dy < -4f) {
                                        scrollAccumulator = (scrollAccumulator + dy).coerceAtMost(0f)
                                        if (scrollAccumulator < -40f && !atTop) {
                                            tabBarMinimized = true
                                        }
                                    } else if (dy > 4f) {
                                        scrollAccumulator = (scrollAccumulator + dy).coerceAtLeast(0f)
                                        if (scrollAccumulator > 24f) {
                                            tabBarMinimized = false
                                        }
                                    }
                                    return Offset.Zero
                                }
                            }
                        }

                        val hasActiveSheet = activeSubject != null ||
                            (showRiskyAttendanceModal && riskySubjects.isNotEmpty()) ||
                            showAllNotificationsModal ||
                            showCalendarEditor

                        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
                            val isDesktop = maxWidth >= 840.dp
                            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            val appleInsets = PaddingValues(
                                top = statusTop + 60.dp,
                                bottom = if (isDesktop) 36.dp else (navBottom + 94.dp)
                            )

                            val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
                            val bgScale by animateFloatAsState(
                                targetValue = if (!isDesktop && hasActiveSheet && !reduceMotion) 0.94f else 1f,
                                animationSpec = AppleMotion.sheet
                            )
                            val bgCorner by animateDpAsState(
                                targetValue = if (!isDesktop && hasActiveSheet) 28.dp else 0.dp,
                                animationSpec = AppleMotion.dpSheet
                            )
                            val pagerProgress by remember(mainPagerState) {
                                derivedStateOf {
                                    mainPagerState.currentPage + mainPagerState.currentPageOffsetFraction
                                }
                            }

                            CompositionLocalProvider(
                                LocalHazeState provides hazeState,
                                LocalGlassEnabled provides glassEnabled,
                                LocalAppleContentInsets provides appleInsets
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.takeIf { colors.isDark } ?: colors.background)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val s = bgScale.coerceIn(0.85f, 1f)
                                                scaleX = s
                                                scaleY = s
                                            }
                                            .predictiveBackSurfaceTransform(
                                                readBack = { if (!hasAnyOverlay) predictiveBackState else PredictiveBackState() },
                                                slideDp = 42f,
                                                verticalSlideDp = 0f,
                                                scaleDrop = 0.055f,
                                                pivotY = 0.5f
                                            )
                                            .clip(RoundedCornerShape(bgCorner.coerceAtLeast(0.dp)))
                                    ) {
                                        // CAMADA 1: Conteúdo edge-to-edge (fonte do Liquid Glass)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .hazeSource(hazeState)
                                                .background(colors.background)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.radialGradient(
                                                            colors = listOf(
                                                                colors.accent.copy(alpha = if (colors.isDark) 0.06f else 0.045f),
                                                                Color.Transparent
                                                            ),
                                                            center = Offset(220f, 120f),
                                                            radius = 900f
                                                        )
                                                    )
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(start = if (isDesktop) 248.dp else 0.dp)
                                                    .nestedScroll(tabScrollConnection)
                                            ) {
                                                if (isDesktop) {
                                                    renderTabPager()
                                                } else {
                                                    val pullState = rememberPullToRefreshState()
                                                    val refreshing = when (currentTab) {
                                                        MainTab.VIRTUAL -> state.virtualLoading && state.virtualOverview != null
                                                        MainTab.CALENDAR -> state.calendarLoading
                                                        else -> state.loading && state.dashboard != null
                                                    }
                                                    PullToRefreshBox(
                                                        isRefreshing = refreshing,
                                                        onRefresh = { refreshCurrentTab() },
                                                        state = pullState,
                                                        indicator = {
                                                            val fraction = pullState.distanceFraction.coerceIn(0f, 1f)
                                                            if (refreshing || fraction > 0.08f) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .align(Alignment.TopCenter)
                                                                        .padding(top = statusTop + 56.dp + (28.dp * fraction))
                                                                ) {
                                                                    GlassSurface(
                                                                        modifier = Modifier.size(38.dp),
                                                                        shape = CircleShape,
                                                                        shadowElevation = 10.dp
                                                                    ) {
                                                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                            AppleActivityIndicator(size = 18.dp, color = colors.textPrimary)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    ) {
                                                        renderTabPager()
                                                    }
                                                }
                                            }
                                        }

                                        // CAMADA 2: Controles de vidro flutuantes
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(start = if (isDesktop) 248.dp else 0.dp)
                                                .fillMaxWidth()
                                        ) {
                                            GlassEdgeBarBackdrop(
                                                collapseProgress = { smoothCollapseState.value },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(statusTop + 62.dp)
                                            )
                                            AppleNavBar(
                                                title = currentTab.label,
                                                collapseProgress = { smoothCollapseState.value },
                                                statusTop = statusTop,
                                                unreadCount = unreadCount,
                                                isDesktop = isDesktop,
                                                loading = state.loading ||
                                                    (currentTab == MainTab.VIRTUAL && state.virtualLoading) ||
                                                    (currentTab == MainTab.CALENDAR && state.calendarLoading),
                                                onOpenNotifications = { navigateToTab(MainTab.CENTRAL) },
                                                onRefresh = { refreshCurrentTab() }
                                            )
                                        }

                                        val banner = state.statusBanner ?: state.error
                                        AppleToastHost(
                                            message = banner,
                                            isError = state.error != null,
                                            topOffset = statusTop + 62.dp,
                                            startOffset = if (isDesktop) 248.dp else 0.dp,
                                            onDismiss = { repo.clearBanner() },
                                            modifier = Modifier.align(Alignment.TopCenter)
                                        )
                                        if (banner.isNullOrBlank() && state.isOffline) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .padding(
                                                        top = statusTop + 62.dp,
                                                        start = (if (isDesktop) 248.dp else 0.dp) + 16.dp,
                                                        end = 16.dp
                                                    )
                                            ) {
                                                GlassSurface(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .clickable { scope.launch { repo.refreshDashboard() } },
                                                    shape = CircleShape,
                                                    shadowElevation = 6.dp
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(7.dp)
                                                                .clip(CircleShape)
                                                                .background(colors.orange)
                                                        )
                                                        Text(
                                                            text = "Modo offline · ${formatOfflineAge(state.lastSyncAtMillis)}",
                                                            color = colors.textPrimary,
                                                            style = LocalAppleTypography.current.caption1,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (isDesktop) {
                                            AppleDesktopSidebar(
                                                currentTab = currentTab,
                                                studentName = state.dashboard?.student?.name,
                                                studentRa = state.dashboard?.student?.ra,
                                                unreadCount = unreadCount,
                                                onSelect = { navigateToTab(it) },
                                                onOpenSearch = { showGlobalSearchModal = true },
                                                modifier = Modifier.align(Alignment.CenterStart)
                                            )
                                        } else {
                                            AppleLiquidGlassTabBar(
                                                currentTab = currentTab,
                                                pagerProgress = pagerProgress,
                                                minimized = tabBarMinimized,
                                                navBottom = navBottom,
                                                onSelect = { navigateToTab(it) },
                                                modifier = Modifier.align(Alignment.BottomCenter)
                                            )
                                        }
                                    }

                                    // CAMADA 3: Sheets e Alertas (acima de tudo, com acesso ao hazeSource)
                                    renderOverlays()
                                }
                            }
                        }
                    }
                }

                state.documentLoadingLabel?.let { label ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surface,
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = colors.accent,
                                    strokeWidth = 2.5.dp
                                )
                                Column {
                                    Text(
                                        text = "Abrindo arquivo...",
                                        color = colors.textPrimary,
                                        style = LocalAppleTypography.current.headline
                                    )
                                    Text(
                                        text = label,
                                        color = colors.textSecondary,
                                        style = LocalAppleTypography.current.caption1
                                    )
                                }
                            }
                        }
                    }
                }

                state.activeDocumentViewer?.let { viewer ->
                    PlatformDocumentViewer(
                        title = viewer.title,
                        courseName = viewer.courseName,
                        fileName = viewer.fileName,
                        mimeType = viewer.mimeType,
                        bytes = viewer.bytes,
                        fallbackUrl = viewer.fallbackUrl,
                        downloading = state.downloadingFileKey == viewer.path,
                        onDownload = { scope.launch { repo.downloadActiveDocument() } },
                        onClose = { repo.closeDocumentViewer() }
                    )
                }
            }
        }
    }
}
