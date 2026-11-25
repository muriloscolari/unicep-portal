package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.platform.ExportCardData
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.screens.PortalOverlayModal

@Composable
fun ExportActionModal(
    fileName: String,
    data: ExportCardData,
    onDismiss: () -> Unit
) {
    val colors = LocalPortalColors.current
    val haptics = LocalHapticFeedback.current
    val isWeb = remember { PlatformBridge.capabilities().isWeb }
    var feedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    RegisterBackOverlay(onBack = onDismiss)

    PortalOverlayModal(
        title = "Exportar / Compartilhar",
        subtitle = data.title,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExportOptionCard(
                icon = PortalIcons.Document,
                tint = colors.accent,
                title = if (isWeb) "Copiar texto" else "Compartilhar texto",
                subtitle = if (isWeb) "Copia um resumo pronto para enviar" else "Enviar resumo por mensagem ou outro app",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val ok = PlatformBridge.shareText(data.title, data.plainText)
                    if (isWeb) {
                        feedback = if (ok) "Texto copiado para a área de transferência." to true else "Não foi possível copiar o texto." to false
                    } else if (ok) {
                        onDismiss()
                    } else {
                        feedback = "Não foi possível abrir o compartilhamento." to false
                    }
                }
            )

            ExportOptionCard(
                icon = PortalIcons.Download,
                tint = colors.green,
                title = if (isWeb) "Baixar imagem" else "Compartilhar imagem",
                subtitle = "Salva ou envia como imagem",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val ok = PlatformBridge.exportPng(fileName, data)
                    if (isWeb) {
                        feedback = if (ok) "Download da imagem PNG iniciado." to true else "Falha ao gerar imagem PNG." to false
                    } else if (ok) {
                        onDismiss()
                    } else {
                        feedback = "Falha ao gerar imagem PNG." to false
                    }
                }
            )

            ExportOptionCard(
                icon = PortalIcons.Share,
                tint = colors.orange,
                title = "Imprimir / Salvar PDF",
                subtitle = "Imprimir ou salvar como PDF",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val ok = PlatformBridge.printDocument(data.title, data)
                    if (ok) {
                        onDismiss()
                    } else {
                        feedback = "Impressão indisponível neste dispositivo." to false
                    }
                }
            )

            feedback?.let { (message, isSuccess) ->
                Spacer(Modifier.height(2.dp))
                val bg = (if (isSuccess) colors.green else colors.red).copy(alpha = 0.14f)
                val fg = if (isSuccess) colors.green else colors.red
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message,
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
            }
            Icon(PortalIcons.ChevronRight, contentDescription = null, tint = colors.tertiaryLabel, modifier = Modifier.size(18.dp))
        }
    }
}
