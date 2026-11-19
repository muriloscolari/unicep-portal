package me.muriloscolari.unicep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.designsystem.AppleActivityIndicator
import me.muriloscolari.unicep.ui.designsystem.GlassProminentButton
import me.muriloscolari.unicep.ui.designsystem.GlassSurface
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalGlassEnabled
import me.muriloscolari.unicep.ui.designsystem.LocalHazeState
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.PortalTextButton
import me.muriloscolari.unicep.ui.designsystem.PortalTextField
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
private fun AppleMeshBackdrop(modifier: Modifier = Modifier) {
    val colors = LocalPortalColors.current
    val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
    val phase = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition()
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
        animated
    }

    Canvas(modifier = modifier.fillMaxSize().background(colors.background)) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val radiusBase = (maxOf(w, h) * 0.58f).coerceAtLeast(1f)
        val alphaBoost = if (colors.isDark) 0.28f else 0.19f

        // Blob 1: Blue
        val c1 = Offset(
            x = w * (0.25f + 0.12f * cos(phase)),
            y = h * (0.22f + 0.08f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.accent.copy(alpha = alphaBoost), Color.Transparent),
                center = c1,
                radius = radiusBase
            ),
            radius = radiusBase,
            center = c1
        )

        // Blob 2: Indigo
        val c2 = Offset(
            x = w * (0.78f + 0.10f * sin(phase + 1.2f)),
            y = h * (0.38f + 0.10f * cos(phase + 0.8f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.indigo.copy(alpha = alphaBoost * 0.9f), Color.Transparent),
                center = c2,
                radius = radiusBase * 0.95f
            ),
            radius = radiusBase * 0.95f,
            center = c2
        )

        // Blob 3: Teal
        val c3 = Offset(
            x = w * (0.48f + 0.14f * cos(phase + 2.4f)),
            y = h * (0.80f + 0.08f * sin(phase + 1.7f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.teal.copy(alpha = alphaBoost * 0.8f), Color.Transparent),
                center = c3,
                radius = radiusBase * 0.9f
            ),
            radius = radiusBase * 0.9f,
            center = c3
        )
    }
}

@Composable
private fun AppIconSquircle(sizeDp: Int, cornerDp: Int) {
    val colors = LocalPortalColors.current
    val shape = RoundedCornerShape(cornerDp.dp)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.accent.copy(alpha = 0.35f),
                spotColor = colors.accent.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(colors.accent, colors.indigo)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = PortalIcons.Academic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((sizeDp * 0.48f).dp)
        )
    }
}

@Composable
fun SplashLoadingScreen() {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    if (style != DesignStyle.APPLE) {
        Box(
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(PortalIcons.Academic, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = colors.accent)
                Text("Conectando automaticamente...", color = colors.textSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    var showText by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        showText = true
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AppleMeshBackdrop()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AppIconSquircle(sizeDp = 88, cornerDp = 20)
            AppleActivityIndicator(size = 24.dp, color = colors.textSecondary)
            AnimatedVisibility(visible = showText, enter = fadeIn(tween(300))) {
                Text(
                    text = "Entrando…",
                    color = colors.textSecondary,
                    style = appleType.footnote
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    initialServerUrl: String,
    loading: Boolean,
    error: String?,
    onLogin: (serverUrl: String, ra: String, password: String, perfil: String) -> Unit
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    var ra by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var showServer by remember { mutableStateOf(false) }
    var perfilIndex by remember { mutableStateOf(0) }
    val perfilLabels = listOf("Graduação", "Pós-graduação")
    val perfilValues = listOf("alunograd", "alunopos")
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val passwordFocusRequester = remember { FocusRequester() }
    val submitLogin: () -> Unit = {
        if (ra.isNotBlank() && password.isNotBlank() && !loading) {
            onLogin(serverUrl, ra, password, perfilValues[perfilIndex])
        }
    }

    if (style != DesignStyle.APPLE) {
        val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
        val shakeOffset = remember { Animatable(0f) }

        LaunchedEffect(error) {
            if (!error.isNullOrBlank() && !reduceMotion) {
                listOf(-12f, 12f, -8f, 8f, -4f, 0f).forEach { target ->
                    shakeOffset.animateTo(target, tween(45))
                }
            }
        }
        LaunchedEffect(showServer) {
            if (showServer) {
                delay(160)
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.background,
                            colors.accentSoft.copy(alpha = if (colors.isDark) 0.18f else 0.35f),
                            colors.background
                        )
                    )
                )
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIconSquircle(sizeDp = 78, cornerDp = 22)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "UNICEP Portal",
                    color = colors.textPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Notas, faltas e avisos num só lugar",
                    color = colors.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                PortalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(shakeOffset.value.toInt(), 0) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
                ) {
                    PortalSegmentedControl(
                        options = perfilLabels,
                        selectedIndex = perfilIndex,
                        onSelect = { perfilIndex = it }
                    )
                    Spacer(Modifier.height(16.dp))
                    PortalTextField(
                        value = ra,
                        onValueChange = { if (it.length <= 11) ra = it },
                        label = "RA (usuário)",
                        leadingIcon = PortalIcons.Person,
                        modifier = Modifier.onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                                passwordFocusRequester.requestFocus()
                                true
                            } else false
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() })
                    )
                    Spacer(Modifier.height(12.dp))
                    PortalTextField(
                        value = password,
                        onValueChange = { if (it.length <= 8) password = it },
                        label = "Senha do portal",
                        leadingIcon = PortalIcons.Lock,
                        modifier = Modifier
                            .focusRequester(passwordFocusRequester)
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                                    submitLogin()
                                    true
                                } else false
                            },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                        trailing = {
                            Icon(
                                imageVector = if (passwordVisible) PortalIcons.EyeOff else PortalIcons.Eye,
                                contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { passwordVisible = !passwordVisible }
                            )
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.red.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = PortalIcons.Warning,
                                contentDescription = null,
                                tint = colors.red,
                                modifier = Modifier.size(16.dp)
                            )
                            SelectionContainer {
                                Text(
                                    text = error,
                                    color = colors.red,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    PortalPrimaryButton(
                        text = "Entrar",
                        onClick = submitLogin,
                        enabled = ra.isNotBlank() && password.isNotBlank(),
                        loading = loading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Sua sessão fica salva com segurança neste dispositivo.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!PlatformBridge.capabilities().isWeb) {
                    Spacer(Modifier.height(14.dp))
                    PortalTextButton(
                        text = if (showServer) "Ocultar configurações avançadas" else "Configurações avançadas",
                        color = colors.textSecondary,
                        onClick = { showServer = !showServer }
                    )
                    AnimatedVisibility(visible = showServer) {
                        PortalCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
                        ) {
                            PortalTextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = "URL do servidor",
                                leadingIcon = PortalIcons.Globe,
                                modifier = Modifier.onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            delay(220)
                                            scrollState.animateScrollTo(scrollState.maxValue)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitLogin() })
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // Apple Liquid Glass Login
    val loginHazeState = rememberHazeState()
    val glassEnabled = remember { PlatformBridge.capabilities().supportsAppleEffects }
    val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank() && !reduceMotion) {
            listOf(-12f, 12f, -8f, 8f, -4f, 0f).forEach { target ->
                shakeOffset.animateTo(target, tween(45))
            }
        }
    }

    LaunchedEffect(showServer) {
        if (showServer) {
            delay(160)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    CompositionLocalProvider(
        LocalHazeState provides loginHazeState,
        LocalGlassEnabled provides glassEnabled
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppleMeshBackdrop(modifier = Modifier.hazeSource(loginHazeState))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIconSquircle(sizeDp = 80, cornerDp = 18)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "UNICEP Portal",
                        color = colors.textPrimary,
                        style = appleType.title1,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Notas, faltas e avisos num só lugar",
                        color = colors.textSecondary,
                        style = appleType.callout,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                            .animateContentSize(),
                        shape = RoundedCornerShape(28.dp),
                        tint = colors.surface.copy(alpha = if (colors.isDark) 0.62f else 0.72f),
                        shadowElevation = 20.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            PortalSegmentedControl(
                                options = perfilLabels,
                                selectedIndex = perfilIndex,
                                onSelect = { perfilIndex = it }
                            )
                            Spacer(Modifier.height(16.dp))

                            // Campos agrupados em célula única (RA | Senha)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surface.copy(alpha = if (colors.isDark) 0.72f else 0.88f))
                            ) {
                                GroupedLoginFieldRow(
                                    value = ra,
                                    onValueChange = { if (it.length <= 11) ra = it },
                                    placeholder = "RA (usuário)",
                                    icon = PortalIcons.Person,
                                    inputModifier = Modifier.onPreviewKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                                            passwordFocusRequester.requestFocus()
                                            true
                                        } else false
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(onNext = {
                                        passwordFocusRequester.requestFocus()
                                    })
                                )
                                PortalDivider(Modifier.padding(start = 46.dp))
                                GroupedLoginFieldRow(
                                    value = password,
                                    onValueChange = { if (it.length <= 8) password = it },
                                    placeholder = "Senha do portal",
                                    icon = PortalIcons.Lock,
                                    inputModifier = Modifier
                                        .focusRequester(passwordFocusRequester)
                                        .onPreviewKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                                                submitLogin()
                                                true
                                            } else false
                                        },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                                    trailing = {
                                        Icon(
                                            imageVector = if (passwordVisible) PortalIcons.EyeOff else PortalIcons.Eye,
                                            contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                                            tint = colors.textSecondary,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .clickable { passwordVisible = !passwordVisible }
                                        )
                                    }
                                )
                            }

                            if (!error.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = PortalIcons.Warning,
                                        contentDescription = null,
                                        tint = colors.red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = error,
                                            color = colors.red,
                                            style = appleType.footnote,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(18.dp))
                            GlassProminentButton(
                                text = "Entrar",
                                onClick = submitLogin,
                                enabled = ra.isNotBlank() && password.isNotBlank(),
                                loading = loading,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Sua sessão fica salva com segurança neste dispositivo.",
                                color = colors.textSecondary,
                                style = appleType.caption1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (!PlatformBridge.capabilities().isWeb) {
                        Spacer(Modifier.height(14.dp))
                        PortalTextButton(
                            text = if (showServer) "Ocultar configurações avançadas" else "Configurações avançadas",
                            color = colors.textSecondary,
                            onClick = { showServer = !showServer }
                        )
                        AnimatedVisibility(visible = showServer) {
                            Spacer(Modifier.height(8.dp))
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                shape = RoundedCornerShape(18.dp),
                                tint = colors.surface.copy(alpha = if (colors.isDark) 0.65f else 0.78f),
                                shadowElevation = 10.dp
                            ) {
                                Box(modifier = Modifier.padding(12.dp)) {
                                    PortalTextField(
                                        value = serverUrl,
                                        onValueChange = { serverUrl = it },
                                        label = "URL do servidor",
                                        leadingIcon = PortalIcons.Globe,
                                        modifier = Modifier.onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                scope.launch {
                                                    delay(220)
                                                    scrollState.animateScrollTo(scrollState.maxValue)
                                                }
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { submitLogin() })
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedLoginFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    inputModifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(14.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.tertiaryLabel,
                    style = appleType.body
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = inputModifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Text),
                singleLine = true,
                textStyle = appleType.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}
