package me.muriloscolari.unicep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.PortalTextField

@Composable
fun SplashLoadingScreen() {
    val colors = LocalPortalColors.current
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
}

@Composable
fun LoginScreen(
    initialServerUrl: String,
    loading: Boolean,
    error: String?,
    onLogin: (serverUrl: String, ra: String, password: String, perfil: String) -> Unit
) {
    val colors = LocalPortalColors.current
    var ra by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var showServer by remember { mutableStateOf(!PlatformBridge.capabilities().isWeb) }
    var perfilIndex by remember { mutableStateOf(0) }
    val perfilLabels = listOf("Graduação", "Pós-Grad.")
    val perfilValues = listOf("alunograd", "alunopos")
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(showServer) {
        if (showServer) {
            delay(150)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(PortalIcons.Academic, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("UNICEP Portal", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Acesso rápido às notas, boletim e avisos",
                color = colors.textSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))

            PortalCard(modifier = Modifier.fillMaxWidth()) {
                PortalTextField(
                    value = ra,
                    onValueChange = { if (it.length <= 11) ra = it },
                    label = "RA (Usuário)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(12.dp))
                PortalTextField(
                    value = password,
                    onValueChange = { if (it.length <= 8) password = it },
                    label = "Senha do portal",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (ra.isNotBlank() && password.isNotBlank() && !loading) {
                            onLogin(serverUrl, ra, password, perfilValues[perfilIndex])
                        }
                    })
                )
                Spacer(Modifier.height(14.dp))
                PortalSegmentedControl(
                    options = perfilLabels,
                    selectedIndex = perfilIndex,
                    onSelect = { perfilIndex = it }
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showServer = !showServer }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Servidor do homelab", color = colors.textSecondary, fontSize = 12.sp)
                    Text(if (showServer) "Ocultar" else "Editar", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                AnimatedVisibility(visible = showServer) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        PortalTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = "URL do Backend",
                            modifier = Modifier.onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch {
                                        delay(220)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (ra.isNotBlank() && password.isNotBlank() && !loading) {
                                    onLogin(serverUrl, ra, password, perfilValues[perfilIndex])
                                }
                            })
                        )
                    }
                }

                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = colors.red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(18.dp))
                PortalPrimaryButton(
                    text = "Entrar e Salvar Sessão",
                    onClick = { onLogin(serverUrl, ra, password, perfilValues[perfilIndex]) },
                    enabled = ra.isNotBlank() && password.isNotBlank(),
                    loading = loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
