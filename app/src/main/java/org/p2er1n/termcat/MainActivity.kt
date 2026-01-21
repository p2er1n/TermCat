package org.p2er1n.termcat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import org.p2er1n.termcat.ui.theme.TermCatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TermCatTheme {
                HomeScreen(
                    onStartOverlay = { requestOrStartOverlay() },
                    onOpenSettings = { openOverlaySettings() }
                )
            }
        }
    }

    private fun requestOrStartOverlay() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingWindowService::class.java))
            return
        }

        openOverlaySettings()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}

@Composable
fun HomeScreen(
    onStartOverlay: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var overlayRunning by remember { mutableStateOf(AppPrefs.isOverlayRunning(context)) }

    LaunchedEffect(Unit) {
        overlayEnabled = Settings.canDrawOverlays(context)
        overlayRunning = AppPrefs.isOverlayRunning(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayEnabled = Settings.canDrawOverlays(context)
                overlayRunning = AppPrefs.isOverlayRunning(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = onStartOverlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(text = stringResource(R.string.home_primary_action), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onOpenSettings) {
            Text(text = stringResource(R.string.home_manage_permission))
        }
        Spacer(modifier = Modifier.height(6.dp))
        StatusCard(
            overlayEnabled = overlayEnabled,
            overlayRunning = overlayRunning
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.home_footer),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatusCard(
    overlayEnabled: Boolean,
    overlayRunning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.home_status_title), style = MaterialTheme.typography.titleMedium)
            StatusRow(
                label = stringResource(R.string.home_status_permission),
                value = if (overlayEnabled) {
                    stringResource(R.string.home_status_permission_granted)
                } else {
                    stringResource(R.string.home_status_permission_denied)
                }
            )
            StatusRow(
                label = stringResource(R.string.home_status_window),
                value = if (overlayRunning) {
                    stringResource(R.string.home_status_window_running)
                } else {
                    stringResource(R.string.home_status_window_stopped)
                }
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    TermCatTheme {
        HomeScreen(
            onStartOverlay = {},
            onOpenSettings = {}
        )
    }
}
