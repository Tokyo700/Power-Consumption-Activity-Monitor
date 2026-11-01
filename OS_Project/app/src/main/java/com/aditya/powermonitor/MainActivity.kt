package com.aditya.powermonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aditya.powermonitor.ui.navigation.NavGraph
import com.aditya.powermonitor.ui.permission.PermissionScreen
import com.aditya.powermonitor.ui.theme.PowerMonitorTheme
import com.aditya.powermonitor.util.PermissionUtils

/** The only Activity. Every screen is a composable drawn inside it. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PowerMonitorTheme {
                // Each screen draws its own Scaffold and top bar now.
                Surface { AppRoot() }
            }
        }
    }
}

/** Shows the permission screen until usage access is granted, then the app's screens. */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(PermissionUtils.hasUsageAccess(context)) }

    // ON_RESUME fires every time the Activity comes back to the front, including when the
    // user returns from Settings, so the screen updates without a restart.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasAccess = PermissionUtils.hasUsageAccess(context)
    }

    if (hasAccess) {
        NavGraph()
    } else {
        // No Scaffold here, so keep clear of the status and navigation bars manually.
        PermissionScreen(Modifier.safeDrawingPadding())
    }
}
