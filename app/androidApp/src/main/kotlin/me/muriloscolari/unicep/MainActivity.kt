package me.muriloscolari.unicep

import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.muriloscolari.unicep.platform.PlatformBridge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()
        PlatformBridge.bindActivity(this)
        val initialTab = intent?.getIntExtra("open_tab", 0) ?: 0
        setContent {
            App(initialTab = initialTab)
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRate()
    }

    private fun requestHighRefreshRate() {
        runCatching {
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            val currentDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display ?: @Suppress("DEPRECATION") windowManager.defaultDisplay
            } else {
                @Suppress("DEPRECATION") windowManager.defaultDisplay
            }
            val active = currentDisplay?.mode ?: return
            val bestMode = currentDisplay.supportedModes
                .filter { it.physicalWidth == active.physicalWidth && it.physicalHeight == active.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: active

            val params = window.attributes
            var changed = false
            if (params.preferredDisplayModeId != bestMode.modeId) {
                params.preferredDisplayModeId = bestMode.modeId
                changed = true
            }
            if (params.preferredRefreshRate < bestMode.refreshRate) {
                params.preferredRefreshRate = bestMode.refreshRate
                changed = true
            }
            if (changed) {
                window.attributes = params
            }
        }
    }
}
