package com.aditya.powermonitor.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aditya.powermonitor.ui.theme.PowerMonitorTheme
import com.aditya.powermonitor.util.PermissionUtils

/** Explains why usage access is needed and sends the user to Settings to grant it. */
@Composable
fun PermissionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Usage access needed",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "To see which apps are active and for how long, Power Monitor needs " +
                "Usage access. Android only lets you turn this on in Settings.\n\n" +
                "Find Power Monitor in the list, switch it on, then press Back.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { PermissionUtils.openUsageAccessSettings(context) }) {
            Text("Open Settings")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionScreenPreview() {
    PowerMonitorTheme { PermissionScreen() }
}
