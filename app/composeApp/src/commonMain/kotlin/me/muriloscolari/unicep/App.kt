package me.muriloscolari.unicep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.muriloscolari.unicep.data.api.ReportCardResponse
import me.muriloscolari.unicep.data.repo.PortalRepository
import me.muriloscolari.unicep.platform.AppUpdateInfo
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.designsystem.AppleActivityIndicator
import me.muriloscolari.unicep.ui.designsystem.AppleAlert
import me.muriloscolari.unicep.ui.designsystem.AppleMotion
import me.muriloscolari.unicep.ui.designsystem.AppleProgressBar
import me.muriloscolari.unicep.ui.designsystem.GlassButton
import me.muriloscolari.unicep.ui.designsystem.GlassEdgeBarBackdrop
import me.muriloscolari.unicep.ui.designsystem.GlassSurface
import me.muriloscolari.unicep.ui.designsystem.LocalAppleContentInsets
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalGlassEnabled
import me.muriloscolari.unicep.ui.designsystem.LocalHazeState
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalTheme
import me.muriloscolari.unicep.ui.screens.AcademicScreen
import me.muriloscolari.unicep.ui.screens.AppleAllNotificationsSheet
import me.muriloscolari.unicep.ui.screens.AppleAttendanceLimitSheet
import me.muriloscolari.unicep.ui.screens.AppleSubjectDetailSheet
import me.muriloscolari.unicep.ui.screens.CentralScreen
import me.muriloscolari.unicep.ui.screens.LoginScreen
import me.muriloscolari.unicep.ui.screens.SemesterScreen
import me.muriloscolari.unicep.ui.screens.SplashLoadingScreen
import me.muriloscolari.unicep.ui.screens.isFrequencyAtLimit
import me.muriloscolari.unicep.ui.screens.mergeUnifiedSubjects
import me.muriloscolari.unicep.ui.util.initialsFromName
import me.muriloscolari.unicep.ui.util.toDisplayTitle

private enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    SEMESTER("semestre", "Semestre", PortalIcons.Grades, PortalIcons.GradesFill),
    COURSE("curso", "Curso", PortalIcons.Academic, PortalIcons.AcademicFill),
    CENTRAL("central", "Central", PortalIcons.More, PortalIcons.MoreFill)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(initialTab: Int = 0) {
    val repo = remember { PortalRepository() }
    val state by repo.state.collectAsState()
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(MainTab.entries.getOrElse(initialTab) { MainTab.SEMESTER }) }
    var courseSubTab by remember { mutableStateOf(0) }
    var selectedSubjectCode by remember { mutableStateOf<String?>(null) }
    var showRiskyAttendanceModal by remember { mutableStateOf(false) }
    var showAllNotificationsModal by remember { mutableStateOf(false) }
    var confirmLogoutDialog by remember { mutableStateOf(false) }
    var suppressHistoryPush by remember { mutableStateOf(false) }

    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    val semesterListState = rememberLazyListState()
    val courseListState = rememberLazyListState()
    val centralListState = rememberLazyListState()

    fun activeListStateFor(tab: MainTab): LazyListState = when (tab) {
        MainTab.SEMESTER -> semesterListState
        MainTab.COURSE -> courseListState
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

    fun navigateToTab(tab: MainTab) {
        val sameTabRoot = tab == currentTab &&
            selectedSubjectCode == null &&
            !showRiskyAttendanceModal &&
            !showAllNotificationsModal
        if (sameTabRoot) {
            tabBarMinimized = false
            scope.launch { activeListStateFor(tab).animateScrollToItem(0) }
        } else {
            selectedSubjectCode = null
            showRiskyAttendanceModal = false
            showAllNotificationsModal = false
            tabBarMinimized = false
            currentTab = tab
        }
    }

    fun encodeRoute(): String = when (currentTab) {
        MainTab.SEMESTER -> when {
            !selectedSubjectCode.isNullOrBlank() -> "semestre/subject/$selectedSubjectCode"
            showRiskyAttendanceModal -> "semestre/frequencia-limite"
            else -> "semestre"
        }
        MainTab.COURSE -> "curso/$courseSubTab"
        MainTab.CENTRAL -> if (showAllNotificationsModal) "central/alertas" else "central"
    }

    fun applyPopRoute(route: String) {
        suppressHistoryPush = true
        when {
            route.startsWith("semestre/subject/") -> {
                currentTab = MainTab.SEMESTER
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
                selectedSubjectCode = route.removePrefix("semestre/subject/").ifBlank { null }
            }
            route == "semestre/frequencia-limite" -> {
                currentTab = MainTab.SEMESTER
                selectedSubjectCode = null
                showAllNotificationsModal = false
                showRiskyAttendanceModal = true
            }
            route == "semestre" -> {
                currentTab = MainTab.SEMESTER
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
            }
            route.startsWith("curso") -> {
                currentTab = MainTab.COURSE
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
                courseSubTab = route.substringAfter('/', "0").toIntOrNull()?.coerceIn(0, 2) ?: 0
            }
            route == "central/alertas" -> {
                currentTab = MainTab.CENTRAL
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = true
            }
            route == "central" -> {
                currentTab = MainTab.CENTRAL
                selectedSubjectCode = null
                showRiskyAttendanceModal = false
                showAllNotificationsModal = false
            }
        }
    }

    LaunchedEffect(Unit) {
        repo.tryAutoLogin()
    }

    DisposableEffect(Unit) {
        PlatformBridge.initNavigationHistory(MainTab.entries.getOrElse(initialTab) { MainTab.SEMESTER }.route)
        PlatformBridge.setPopRouteListener { route -> applyPopRoute(route) }
        PlatformBridge.setAndroidBackHandler {
            when {
                confirmLogoutDialog -> {
                    confirmLogoutDialog = false
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
                currentTab != MainTab.SEMESTER -> {
                    currentTab = MainTab.SEMESTER
                    true
                }
                else -> false
            }
        }
        onDispose {
            PlatformBridge.setAndroidBackHandler(null)
        }
    }

    LaunchedEffect(currentTab, courseSubTab, selectedSubjectCode, showRiskyAttendanceModal, showAllNotificationsModal, state.loggedIn) {
        if (!state.loggedIn) return@LaunchedEffect
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
            MainTab.CENTRAL -> repo.loadFinanceAndServicesIfNeeded()
            MainTab.SEMESTER -> Unit
        }
    }

    PortalTheme(stylePreference = state.stylePreference) {
        val colors = LocalPortalColors.current
        val style = LocalDesignStyle.current

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
                // Material Design 3 clássico preservado
                val unreadCount = state.notifications.count { it.readAt == null }
                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
                    val isDesktop = maxWidth >= 640.dp

                    val topBar: @Composable () -> Unit = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceChrome)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("UNICEP Portal", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    Text(
                                        text = "M3",
                                        color = colors.accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.accentSoft)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        IconButton(onClick = { navigateToTab(MainTab.CENTRAL) }) {
                                            Icon(PortalIcons.Bell, contentDescription = "Central e Notificações", tint = colors.accent)
                                        }
                                        if (unreadCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 8.dp, end = 8.dp)
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.red)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { scope.launch { repo.refreshDashboard() } }) {
                                        Icon(PortalIcons.Refresh, contentDescription = "Atualizar", tint = colors.accent)
                                    }
                                }
                            }
                            if (state.loading) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = colors.accent,
                                    trackColor = colors.surfaceSecondary
                                )
                            } else {
                                PortalDivider()
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
                            }
                        }
                    }

                    val bodyContent: @Composable () -> Unit = {
                        when (currentTab) {
                            MainTab.SEMESTER -> SemesterScreen(
                                dashboard = state.dashboard,
                                periods = state.dashboard?.gradePeriods.orEmpty(),
                                selectedPeriodId = state.selectedGradePeriodId,
                                grades = state.grades?.items.orEmpty(),
                                report = state.report ?: state.dashboard?.currentReport ?: ReportCardResponse(),
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
                                loading = state.loading,
                                listState = semesterListState
                            )

                            MainTab.COURSE -> AcademicScreen(
                                curriculum = state.curriculum,
                                schedule = state.schedule,
                                selectedSubTab = courseSubTab,
                                onSelectSubTab = { courseSubTab = it },
                                onOpenProxy = { path -> scope.launch { repo.openPortalLink(path, true) } },
                                loading = state.loading,
                                listState = courseListState
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
                                availableUpdate = availableUpdate,
                                updateChecking = updateChecking,
                                updateDownloading = updateDownloading,
                                updateProgress = updateProgress,
                                updateStatusMessage = updateStatusMessage,
                                onCheckUpdate = { checkAppUpdate(manual = true) },
                                onInstallUpdate = { installAppUpdate() },
                                onLogout = { scope.launch { repo.logout() } },
                                loading = state.loading,
                                listState = centralListState
                            )
                        }
                    }

                    if (isDesktop) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigationRail(containerColor = colors.surface) {
                                Spacer(Modifier.height(12.dp))
                                MainTab.entries.forEach { tab ->
                                    NavigationRailItem(
                                        selected = tab == currentTab,
                                        onClick = { navigateToTab(tab) },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) }
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
                                NavigationBar(containerColor = colors.surface) {
                                    MainTab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = tab == currentTab,
                                            onClick = { navigateToTab(tab) },
                                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                                            label = { Text(tab.label, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                bodyContent()
                            }
                        }
                    }

                    if (showUpdateDialog && availableUpdate != null) {
                        val upd = availableUpdate!!
                        AlertDialog(
                            onDismissRequest = { if (!updateDownloading) showUpdateDialog = false },
                            containerColor = colors.surface,
                            titleContentColor = colors.textPrimary,
                            textContentColor = colors.textSecondary,
                            title = { Text("Atualização disponível (v${upd.latestVersion})", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Versão instalada: v${upd.currentVersion} → Nova: v${upd.latestVersion}")
                                    if (upd.releaseName.isNotBlank()) {
                                        Text(upd.releaseName, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                                    }
                                    if (upd.releaseNotes.isNotBlank()) {
                                        Text(upd.releaseNotes, fontSize = 12.5.sp)
                                    }
                                    if (updateDownloading) {
                                        LinearProgressIndicator(
                                            progress = { updateProgress.coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = colors.accent,
                                            trackColor = colors.accentSoft
                                        )
                                        Text("Baixando APK (${(updateProgress * 100).toInt()}%)...", fontSize = 12.sp)
                                    } else if (!updateStatusMessage.isNullOrBlank() && updateStatusMessage!!.contains("Permita", ignoreCase = true)) {
                                        Text(updateStatusMessage!!, color = colors.orange, fontSize = 12.5.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { installAppUpdate() },
                                    enabled = !updateDownloading
                                ) {
                                    Text(
                                        if (updateDownloading) "Baixando..." else "Atualizar agora",
                                        color = colors.accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showUpdateDialog = false },
                                    enabled = !updateDownloading
                                ) {
                                    Text("Depois", color = colors.textSecondary)
                                }
                            }
                        )
                    }
                }
            }

            else -> {
                // Modo Apple Liquid Glass
                val unreadCount = state.notifications.count { it.readAt == null }
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
                val smoothCollapse by animateFloatAsState(collapseProgress, tween(120))

                // Auto-expande a tab bar quando estiver no topo da lista ou ao trocar de aba
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

                val activeReport = state.report ?: state.dashboard?.currentReport ?: ReportCardResponse()
                val activeGrades = state.grades?.items.orEmpty()
                val mergedSubjects = remember(activeGrades, activeReport.items) {
                    mergeUnifiedSubjects(activeGrades, activeReport)
                }
                val riskySubjects = remember(mergedSubjects) {
                    mergedSubjects
                        .filter { it.report?.isFrequencyAtLimit() == true }
                        .sortedBy { it.report?.remainingAbsences ?: Int.MAX_VALUE }
                }
                val activeSubject = mergedSubjects.firstOrNull { it.code == selectedSubjectCode }
                val hasActiveSheet = activeSubject != null ||
                    (showRiskyAttendanceModal && riskySubjects.isNotEmpty()) ||
                    showAllNotificationsModal

                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
                    val isDesktop = maxWidth >= 640.dp
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

                    CompositionLocalProvider(
                        LocalHazeState provides hazeState,
                        LocalGlassEnabled provides glassEnabled,
                        LocalAppleContentInsets provides appleInsets
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.takeIf { colors.isDark } ?: colors.background)) {
                            // CAMADA 1 + 2 dentro do card stack scale no mobile
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val s = bgScale.coerceIn(0.85f, 1f)
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .clip(RoundedCornerShape(bgCorner.coerceAtLeast(0.dp)))
                            ) {
                                // CAMADA 1: Conteúdo edge-to-edge (fonte do Liquid Glass)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .hazeSource(hazeState)
                                        .background(colors.background)
                                ) {
                                    // Brilho ambiente muito sutil no fundo para dar profundidade ao vidro
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

                                    val contentBody: @Composable () -> Unit = {
                                        AnimatedContent(
                                            targetState = currentTab,
                                            transitionSpec = {
                                                (fadeIn(tween(180)) + scaleIn(
                                                    initialScale = 0.985f,
                                                    animationSpec = tween(180)
                                                )) togetherWith fadeOut(tween(130))
                                            }
                                        ) { tab ->
                                            when (tab) {
                                                MainTab.SEMESTER -> SemesterScreen(
                                                    dashboard = state.dashboard,
                                                    periods = state.dashboard?.gradePeriods.orEmpty(),
                                                    selectedPeriodId = state.selectedGradePeriodId,
                                                    grades = activeGrades,
                                                    report = activeReport,
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
                                                    loading = state.loading,
                                                    listState = courseListState
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
                                                    availableUpdate = availableUpdate,
                                                    updateChecking = updateChecking,
                                                    updateDownloading = updateDownloading,
                                                    updateProgress = updateProgress,
                                                    updateStatusMessage = updateStatusMessage,
                                                    onCheckUpdate = { checkAppUpdate(manual = true) },
                                                    onInstallUpdate = { installAppUpdate() },
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

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = if (isDesktop) 248.dp else 0.dp)
                                            .nestedScroll(tabScrollConnection)
                                    ) {
                                        if (isDesktop) {
                                            contentBody()
                                        } else {
                                            val pullState = rememberPullToRefreshState()
                                            val refreshing = state.loading && state.dashboard != null
                                            PullToRefreshBox(
                                                isRefreshing = refreshing,
                                                onRefresh = { scope.launch { repo.refreshDashboard() } },
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
                                                contentBody()
                                            }
                                        }
                                    }
                                }

                                // CAMADA 2: Controles de vidro flutuantes

                                // Nav bar no topo com Scroll Edge Effect + botões em vidro
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(start = if (isDesktop) 248.dp else 0.dp)
                                        .fillMaxWidth()
                                ) {
                                    GlassEdgeBarBackdrop(
                                        collapseProgress = smoothCollapse,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(statusTop + 62.dp)
                                    )
                                    AppleNavBar(
                                        title = currentTab.label,
                                        collapseProgress = smoothCollapse,
                                        statusTop = statusTop,
                                        unreadCount = unreadCount,
                                        isDesktop = isDesktop,
                                        loading = state.loading,
                                        onOpenNotifications = { navigateToTab(MainTab.CENTRAL) },
                                        onRefresh = { scope.launch { repo.refreshDashboard() } }
                                    )
                                }

                                // Toast flutuante para status/erro
                                val banner = state.statusBanner ?: state.error
                                AppleToastHost(
                                    message = banner,
                                    isError = state.error != null,
                                    topOffset = statusTop + 62.dp,
                                    startOffset = if (isDesktop) 248.dp else 0.dp,
                                    onDismiss = { repo.clearBanner() },
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )

                                // Sidebar flutuante desktop (macOS Tahoe)
                                if (isDesktop) {
                                    AppleDesktopSidebar(
                                        currentTab = currentTab,
                                        studentName = state.dashboard?.student?.name,
                                        studentRa = state.dashboard?.student?.ra,
                                        unreadCount = unreadCount,
                                        onSelect = { navigateToTab(it) },
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                } else {
                                    // Tab bar Liquid Glass mobile
                                    AppleLiquidGlassTabBar(
                                        currentTab = currentTab,
                                        minimized = tabBarMinimized,
                                        navBottom = navBottom,
                                        onSelect = { navigateToTab(it) },
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }

                            // CAMADA 3: Sheets e Alertas (acima de tudo, com acesso ao hazeSource)
                            when {
                                activeSubject != null -> AppleSubjectDetailSheet(
                                    subject = activeSubject,
                                    onDismiss = { selectedSubjectCode = null }
                                )
                                showRiskyAttendanceModal && riskySubjects.isNotEmpty() -> AppleAttendanceLimitSheet(
                                    subjects = riskySubjects,
                                    onSelectSubject = { code ->
                                        showRiskyAttendanceModal = false
                                        selectedSubjectCode = code
                                    },
                                    onDismiss = { showRiskyAttendanceModal = false }
                                )
                                showAllNotificationsModal -> AppleAllNotificationsSheet(
                                    notifications = state.notifications,
                                    onDismiss = { showAllNotificationsModal = false }
                                )
                            }

                            if (confirmLogoutDialog) {
                                AppleAlert(
                                    title = "Sair da conta?",
                                    message = "Sua sessão salva será removida deste dispositivo.",
                                    confirmText = "Sair",
                                    confirmDestructive = true,
                                    onConfirm = {
                                        confirmLogoutDialog = false
                                        scope.launch { repo.logout() }
                                    },
                                    onDismiss = { confirmLogoutDialog = false }
                                )
                            }

                            if (showUpdateDialog && availableUpdate != null) {
                                val upd = availableUpdate!!
                                AppleAlert(
                                    title = "Atualização disponível (v${upd.latestVersion})",
                                    message = buildString {
                                        append("Versão instalada: v${upd.currentVersion} → v${upd.latestVersion}")
                                        if (upd.releaseName.isNotBlank()) append("\n${upd.releaseName}")
                                        if (upd.releaseNotes.isNotBlank()) append("\n\n${upd.releaseNotes}")
                                    },
                                    confirmText = if (updateDownloading) "Baixando..." else "Atualizar",
                                    dismissText = "Depois",
                                    confirmEnabled = !updateDownloading,
                                    dismissEnabled = !updateDownloading,
                                    onConfirm = { installAppUpdate() },
                                    onDismiss = { showUpdateDialog = false },
                                    extraContent = if (updateDownloading) {
                                        {
                                            AppleProgressBar(progress = updateProgress, color = colors.accent)
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "Baixando APK (${(updateProgress * 100).toInt()}%)…",
                                                color = colors.textSecondary,
                                                style = LocalAppleTypography.current.caption1
                                            )
                                        }
                                    } else if (!updateStatusMessage.isNullOrBlank() && updateStatusMessage!!.contains("Permita", ignoreCase = true)) {
                                        {
                                            Text(
                                                text = updateStatusMessage!!,
                                                color = colors.orange,
                                                style = LocalAppleTypography.current.caption1
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleNavBar(
    title: String,
    collapseProgress: Float,
    statusTop: Dp,
    unreadCount: Int,
    isDesktop: Boolean,
    loading: Boolean,
    onOpenNotifications: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = statusTop, start = 16.dp, end = 16.dp)
            .height(52.dp)
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = appleType.headline,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(collapseProgress.coerceIn(0f, 1f))
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isDesktop) {
                GlassButton(
                    onClick = onRefresh,
                    size = 40.dp,
                    contentDescription = "Atualizar"
                ) {
                    if (loading) {
                        AppleActivityIndicator(size = 18.dp, color = colors.accent)
                    } else {
                        Icon(
                            imageVector = PortalIcons.Refresh,
                            contentDescription = "Atualizar",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box {
                GlassButton(
                    onClick = onOpenNotifications,
                    size = 40.dp,
                    contentDescription = "Notificações"
                ) {
                    Icon(
                        imageVector = PortalIcons.Bell,
                        contentDescription = "Central e Notificações",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(colors.red)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleToastHost(
    message: String?,
    isError: Boolean,
    topOffset: Dp,
    startOffset: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val scope = rememberCoroutineScope()
    var dragY by remember(message) { mutableFloatStateOf(0f) }

    LaunchedEffect(message, isError) {
        if (!message.isNullOrBlank() && !isError) {
            delay(4000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        modifier = modifier.padding(top = topOffset, start = startOffset + 16.dp, end = 16.dp),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(AppleMotion.standard),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(AppleMotion.standard)
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .offset(y = (dragY / 3f).dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragY = (dragY + delta).coerceAtMost(20f)
                    },
                    onDragStopped = {
                        if (dragY < -28f) onDismiss() else scope.launch { dragY = 0f }
                    }
                )
                .clickable { onDismiss() },
            shape = CircleShape,
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isError) PortalIcons.Warning else PortalIcons.CheckCircle,
                    contentDescription = null,
                    tint = if (isError) colors.red else colors.green,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = message.orEmpty(),
                    color = colors.textPrimary,
                    style = appleType.subheadline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AppleLiquidGlassTabBar(
    currentTab: MainTab,
    minimized: Boolean,
    navBottom: Dp,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val haptic = LocalHapticFeedback.current
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    val barHeight by animateDpAsState(
        targetValue = if (minimized) 48.dp else 62.dp,
        animationSpec = AppleMotion.dpStandard
    )
    val horizontalPad by animateDpAsState(
        targetValue = if (minimized) 56.dp else 20.dp,
        animationSpec = AppleMotion.dpStandard
    )

    val safePad = horizontalPad.coerceAtLeast(0.dp)
    val safeHeight = barHeight.coerceAtLeast(1.dp)

    Box(
        modifier = modifier
            .padding(bottom = (navBottom + 12.dp).coerceAtLeast(0.dp), start = safePad, end = safePad)
            .widthIn(max = 360.dp)
            .fillMaxWidth()
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(safeHeight),
            shape = CircleShape,
            shadowElevation = 20.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                val itemWidth = (maxWidth / tabs.size).coerceAtLeast(0.dp)
                val lensOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = AppleMotion.dpSnappy
                )

                // Lente deslizante atrás da aba ativa
                Box(
                    modifier = Modifier
                        .offset(x = lensOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.glassLens)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    tabs.forEach { tab ->
                        val isSelected = tab == currentTab
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val itemScale by animateFloatAsState(
                            targetValue = if (pressed) 0.93f else 1f,
                            animationSpec = AppleMotion.press
                        )
                        val safeItemScale = itemScale.coerceIn(0.8f, 1.2f)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer { scaleX = safeItemScale; scaleY = safeItemScale }
                                .clip(CircleShape)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .semantics {
                                    role = Role.Tab
                                    this.selected = isSelected
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(tab)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) colors.accent else colors.textPrimary,
                                modifier = Modifier.size(if (minimized) 21.dp else 22.dp)
                            )
                            AnimatedVisibility(
                                visible = !minimized,
                                enter = fadeIn(tween(150)),
                                exit = fadeOut(tween(110))
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (isSelected) colors.accent else colors.textPrimary,
                                    style = appleType.caption2,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleDesktopSidebar(
    currentTab: MainTab,
    studentName: String?,
    studentRa: String?,
    unreadCount: Int,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val shape = RoundedCornerShape(22.dp)

    GlassSurface(
        modifier = modifier
            .padding(8.dp)
            .width(232.dp)
            .fillMaxHeight(),
        shape = shape,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Brush.linearGradient(listOf(colors.accent, colors.indigo))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = PortalIcons.Academic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "UNICEP Portal",
                        color = colors.textPrimary,
                        style = appleType.headline,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(6.dp))

                MainTab.entries.forEach { tab ->
                    val isSelected = tab == currentTab
                    val interactionSource = remember { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()
                    val pressed by interactionSource.collectIsPressedAsState()
                    val rowBg = when {
                        isSelected -> colors.glassLens
                        pressed -> colors.systemFill
                        hovered -> colors.systemFill.copy(alpha = 0.45f)
                        else -> Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(rowBg)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .hoverable(interactionSource)
                            .semantics {
                                role = Role.Tab
                                this.selected = isSelected
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelect(tab) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                contentDescription = null,
                                tint = if (isSelected) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = tab.label,
                                color = colors.textPrimary,
                                style = appleType.subheadline,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                        if (tab == MainTab.CENTRAL && unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.red)
                            )
                        }
                    }
                }
            }

            if (!studentName.isNullOrBlank() || !studentRa.isNullOrBlank()) {
                val shortName = studentName.orEmpty().toDisplayTitle().ifBlank { "Aluno" }
                val initials = remember(studentName) { initialsFromName(studentName) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelect(MainTab.CENTRAL) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(colors.accent, colors.indigo))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = Color.White,
                            style = appleType.caption1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shortName,
                            color = colors.textPrimary,
                            style = appleType.footnote,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!studentRa.isNullOrBlank()) {
                            Text(
                                text = "RA $studentRa",
                                color = colors.textSecondary,
                                style = appleType.caption1,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
