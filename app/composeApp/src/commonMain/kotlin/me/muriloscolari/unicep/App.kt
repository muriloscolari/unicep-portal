package me.muriloscolari.unicep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.muriloscolari.unicep.data.api.ReportCardResponse
import me.muriloscolari.unicep.data.repo.PortalRepository
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalTheme
import me.muriloscolari.unicep.ui.screens.AcademicScreen
import me.muriloscolari.unicep.ui.screens.CentralScreen
import me.muriloscolari.unicep.ui.screens.LoginScreen
import me.muriloscolari.unicep.ui.screens.SemesterScreen
import me.muriloscolari.unicep.ui.screens.SplashLoadingScreen

private enum class MainTab(val route: String, val label: String, val icon: ImageVector) {
    SEMESTER("semestre", "Semestre", PortalIcons.Grades),
    COURSE("curso", "Curso", PortalIcons.Academic),
    CENTRAL("central", "Central", PortalIcons.More)
}

@Composable
fun App(initialTab: Int = 0) {
    val repo = remember { PortalRepository() }
    val state by repo.state.collectAsState()
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(MainTab.entries.getOrElse(initialTab) { MainTab.SEMESTER }) }
    var courseSubTab by remember { mutableStateOf(0) }
    var selectedSubjectCode by remember { mutableStateOf<String?>(null) }
    var suppressHistoryPush by remember { mutableStateOf(false) }

    fun encodeRoute(): String = when (currentTab) {
        MainTab.SEMESTER -> if (!selectedSubjectCode.isNullOrBlank()) "semestre/subject/$selectedSubjectCode" else "semestre"
        MainTab.COURSE -> "curso/$courseSubTab"
        MainTab.CENTRAL -> "central"
    }

    fun applyPopRoute(route: String) {
        suppressHistoryPush = true
        when {
            route.startsWith("semestre/subject/") -> {
                currentTab = MainTab.SEMESTER
                selectedSubjectCode = route.removePrefix("semestre/subject/").ifBlank { null }
            }
            route == "semestre" -> {
                currentTab = MainTab.SEMESTER
                selectedSubjectCode = null
            }
            route.startsWith("curso") -> {
                currentTab = MainTab.COURSE
                selectedSubjectCode = null
                courseSubTab = route.substringAfter('/', "0").toIntOrNull()?.coerceIn(0, 2) ?: 0
            }
            route == "central" -> {
                currentTab = MainTab.CENTRAL
                selectedSubjectCode = null
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
                selectedSubjectCode != null -> {
                    selectedSubjectCode = null
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

    LaunchedEffect(currentTab, courseSubTab, selectedSubjectCode, state.loggedIn) {
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

            else -> {
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
                                        text = if (style == DesignStyle.APPLE) "iOS" else "M3",
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
                                        IconButton(onClick = {
                                            selectedSubjectCode = null
                                            currentTab = MainTab.CENTRAL
                                        }) {
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
                                onSelectPeriod = { id -> scope.launch { repo.selectSemesterPeriod(id) } },
                                onOpenSubject = { code -> selectedSubjectCode = code },
                                onCloseSubject = { selectedSubjectCode = null },
                                onOpenNotifications = {
                                    selectedSubjectCode = null
                                    currentTab = MainTab.CENTRAL
                                }
                            )

                            MainTab.COURSE -> AcademicScreen(
                                curriculum = state.curriculum,
                                schedule = state.schedule,
                                selectedSubTab = courseSubTab,
                                onSelectSubTab = { courseSubTab = it },
                                onOpenProxy = { path -> repo.openPortalLink(path, true) }
                            )

                            MainTab.CENTRAL -> CentralScreen(
                                dashboard = state.dashboard,
                                finance = state.finance,
                                services = state.services,
                                notifications = state.notifications,
                                stylePreference = state.stylePreference,
                                onSelectStyle = { repo.setStylePreference(it) },
                                onOpenAva = { scope.launch { repo.openAvaSso() } },
                                onOpenLink = { path, proxy -> repo.openPortalLink(path, proxy) },
                                onSyncNotifications = { scope.launch { repo.syncNotificationsNow() } },
                                onMarkAllRead = { scope.launch { repo.markAllNotificationsRead() } },
                                onTestNotification = { scope.launch { repo.sendTestNotification() } },
                                onLogout = { repo.logout() }
                            )
                        }
                    }

                    if (isDesktop) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            DesktopSideNav(
                                style = style,
                                currentTab = currentTab,
                                onSelect = {
                                    selectedSubjectCode = null
                                    currentTab = it
                                }
                            )
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                topBar()
                                Box(modifier = Modifier.weight(1f)) { bodyContent() }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Scaffold(
                                containerColor = colors.background,
                                topBar = topBar,
                                bottomBar = {
                                    if (style == DesignStyle.MATERIAL) {
                                        NavigationBar(containerColor = colors.surface) {
                                            MainTab.entries.forEach { tab ->
                                                NavigationBarItem(
                                                    selected = tab == currentTab,
                                                    onClick = {
                                                        selectedSubjectCode = null
                                                        currentTab = tab
                                                    },
                                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                                    label = { Text(tab.label, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                    bodyContent()
                                }
                            }

                            if (style == DesignStyle.APPLE) {
                                AppleFloatingTabDock(
                                    currentTab = currentTab,
                                    onSelect = {
                                        selectedSubjectCode = null
                                        currentTab = it
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
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
private fun AppleFloatingTabDock(
    currentTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceChrome,
            border = BorderStroke(0.5.dp, colors.divider),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) colors.accentSoft else Color.Transparent)
                            .clickable { onSelect(tab) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = tab.label,
                            color = if (selected) colors.accent else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSideNav(
    style: DesignStyle,
    currentTab: MainTab,
    onSelect: (MainTab) -> Unit
) {
    val colors = LocalPortalColors.current
    if (style == DesignStyle.APPLE) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(colors.surfaceChrome)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "ACOMPANHAMENTO",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
                MainTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.accentSoft else Color.Transparent)
                            .clickable { onSelect(tab) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tab.label,
                            color = if (selected) colors.accent else colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
            Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(colors.divider))
        }
    } else {
        NavigationRail(containerColor = colors.surface) {
            Spacer(Modifier.height(12.dp))
            MainTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = tab == currentTab,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) }
                )
            }
        }
    }
}
