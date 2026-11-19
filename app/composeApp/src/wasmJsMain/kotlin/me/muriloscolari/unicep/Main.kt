package me.muriloscolari.unicep

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import me.muriloscolari.unicep.platform.PlatformBridge

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("ComposeTarget") {
        LaunchedEffect(Unit) {
            PlatformBridge.notifyFirstFrameRendered()
        }
        App()
    }
}
