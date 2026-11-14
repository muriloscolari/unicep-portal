package me.muriloscolari.unicep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.muriloscolari.unicep.platform.PlatformBridge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlatformBridge.bindActivity(this)
        val initialTab = intent?.getIntExtra("open_tab", 0) ?: 0
        setContent {
            App(initialTab = initialTab)
        }
    }
}
