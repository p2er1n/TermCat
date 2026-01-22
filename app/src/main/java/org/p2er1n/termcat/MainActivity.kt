package org.p2er1n.termcat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import org.p2er1n.termcat.ui.theme.TermCatTheme
import android.app.ActivityManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ComponentName
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.widget.Toast

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TermCatTheme {
                HomeScreen(
                    onStartOverlay = { requestOrStartOverlay() },
                    onStopOverlay = { stopOverlay() },
                    onOpenSettings = { openOverlaySettings() },
                    onOpenAccessibility = { openAccessibilitySettings() }
                )
            }
        }
    }

    private fun requestOrStartOverlay() {
        if (!isScrollServiceEnabled(this)) {
            Toast.makeText(
                this,
                getString(R.string.accessibility_scroll_required),
                Toast.LENGTH_LONG
            ).show()
            return
        }
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

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun stopOverlay() {
        stopService(Intent(this, FloatingWindowService::class.java))
    }
}

@Composable
fun HomeScreen(
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var overlayRunning by remember { mutableStateOf(AppPrefs.isOverlayRunning(context)) }
    var accessibilityEnabled by remember { mutableStateOf(isScrollServiceEnabled(context)) }
    var llmEndpoint by remember { mutableStateOf(AppPrefs.getLlmEndpoint(context)) }
    var llmApiKey by remember { mutableStateOf(AppPrefs.getLlmApiKey(context)) }
    var llmModel by remember { mutableStateOf(AppPrefs.getLlmModel(context)) }
    var ocrEngine by remember { mutableStateOf(AppPrefs.getOcrEngine(context)) }
    var maxCapturePagesText by remember {
        mutableStateOf(AppPrefs.getMaxCapturePages(context).toString())
    }

    LaunchedEffect(Unit) {
        overlayEnabled = Settings.canDrawOverlays(context)
        overlayRunning = isOverlayServiceRunning(context)
        accessibilityEnabled = isScrollServiceEnabled(context)
        llmEndpoint = AppPrefs.getLlmEndpoint(context)
        llmApiKey = AppPrefs.getLlmApiKey(context)
        llmModel = AppPrefs.getLlmModel(context)
        ocrEngine = AppPrefs.getOcrEngine(context)
        maxCapturePagesText = AppPrefs.getMaxCapturePages(context).toString()
    }

    LaunchedEffect(llmEndpoint) {
        AppPrefs.setLlmEndpoint(context, llmEndpoint)
    }

    LaunchedEffect(llmApiKey) {
        AppPrefs.setLlmApiKey(context, llmApiKey)
    }

    LaunchedEffect(llmModel) {
        AppPrefs.setLlmModel(context, llmModel)
    }

    LaunchedEffect(ocrEngine) {
        AppPrefs.setOcrEngine(context, ocrEngine)
    }

    LaunchedEffect(maxCapturePagesText) {
        val value = maxCapturePagesText.toIntOrNull()
        if (value != null) {
            AppPrefs.setMaxCapturePages(context, value)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val filter = IntentFilter(FloatingWindowService.ACTION_OVERLAY_STATUS)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                overlayRunning = isOverlayServiceRunning(context)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayEnabled = Settings.canDrawOverlays(context)
                overlayRunning = isOverlayServiceRunning(context)
                accessibilityEnabled = isScrollServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            onClick = if (overlayRunning) onStopOverlay else onStartOverlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = overlayRunning || accessibilityEnabled
        ) {
            val label = if (overlayRunning) {
                stringResource(R.string.home_primary_action_stop)
            } else {
                stringResource(R.string.home_primary_action_start)
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onOpenSettings) {
            Text(text = stringResource(R.string.home_manage_permission))
        }
        AccessibilityToggle(
            enabled = accessibilityEnabled,
            onOpenAccessibility = onOpenAccessibility
        )
        OcrEngineCard(
            selected = ocrEngine,
            onSelect = { ocrEngine = it }
        )
        CaptureSettingsCard(
            maxCapturePagesText = maxCapturePagesText,
            onMaxCapturePagesChange = { maxCapturePagesText = it }
        )
        Spacer(modifier = Modifier.height(6.dp))
        StatusCard(
            overlayEnabled = overlayEnabled,
            overlayRunning = overlayRunning
        )
        LlmConfigCard(
            endpoint = llmEndpoint,
            apiKey = llmApiKey,
            model = llmModel,
            onEndpointChange = { llmEndpoint = it },
            onApiKeyChange = { llmApiKey = it },
            onModelChange = { llmModel = it }
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
private fun LlmConfigCard(
    endpoint: String,
    apiKey: String,
    model: String,
    onEndpointChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit
) {
    val endpointError = remember(endpoint) { !isValidEndpoint(endpoint) }
    val modelError = remember(model) { model.isBlank() }
    val apiKeyError = remember(apiKey) { apiKey.isBlank() || apiKey.length < 8 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(R.string.llm_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.llm_required),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                label = { Text(text = stringResource(R.string.llm_endpoint_label)) },
                placeholder = { Text(text = stringResource(R.string.llm_endpoint_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = endpointError,
                supportingText = {
                    if (endpointError) {
                        Text(text = stringResource(R.string.llm_endpoint_error))
                    }
                }
            )
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                label = { Text(text = stringResource(R.string.llm_model_label)) },
                placeholder = { Text(text = stringResource(R.string.llm_model_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = modelError,
                supportingText = {
                    if (modelError) {
                        Text(text = stringResource(R.string.llm_model_error))
                    }
                }
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(text = stringResource(R.string.llm_api_key_label)) },
                placeholder = { Text(text = stringResource(R.string.llm_api_key_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = apiKeyError,
                supportingText = {
                    if (apiKeyError) {
                        Text(text = stringResource(R.string.llm_api_key_error))
                    }
                }
            )
            Text(
                text = stringResource(R.string.llm_helper_text),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AccessibilityToggle(
    enabled: Boolean,
    onOpenAccessibility: () -> Unit
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
            Text(
                text = stringResource(R.string.accessibility_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.accessibility_desc),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (enabled) {
                        stringResource(R.string.accessibility_enabled)
                    } else {
                        stringResource(R.string.accessibility_disabled)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { onOpenAccessibility() }
                )
            }
            TextButton(onClick = onOpenAccessibility) {
                Text(text = stringResource(R.string.accessibility_open_settings))
            }
        }
    }
}

@Composable
private fun OcrEngineCard(
    selected: String,
    onSelect: (String) -> Unit
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
            Text(
                text = stringResource(R.string.ocr_engine_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.ocr_engine_desc),
                style = MaterialTheme.typography.bodySmall
            )
            OcrEngineOption(
                label = stringResource(R.string.ocr_engine_mlkit),
                selected = selected == AppPrefs.OCR_ENGINE_MLKIT,
                onClick = { onSelect(AppPrefs.OCR_ENGINE_MLKIT) }
            )
            OcrEngineOption(
                label = stringResource(R.string.ocr_engine_paddle),
                selected = selected == AppPrefs.OCR_ENGINE_PADDLE,
                onClick = { onSelect(AppPrefs.OCR_ENGINE_PADDLE) }
            )
        }
    }
}

@Composable
private fun OcrEngineOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun CaptureSettingsCard(
    maxCapturePagesText: String,
    onMaxCapturePagesChange: (String) -> Unit
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
            Text(
                text = stringResource(R.string.capture_settings_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.capture_settings_desc),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = maxCapturePagesText,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() }
                    onMaxCapturePagesChange(filtered.take(3))
                },
                label = { Text(text = stringResource(R.string.capture_settings_max_pages_label)) },
                placeholder = { Text(text = stringResource(R.string.capture_settings_max_pages_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = stringResource(R.string.capture_settings_helper_text),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun isValidEndpoint(endpoint: String): Boolean {
    if (endpoint.isBlank()) return false
    val trimmed = endpoint.trim()
    return trimmed.startsWith("https://") || trimmed.startsWith("http://")
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun isOverlayServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    val services = manager.getRunningServices(Int.MAX_VALUE)
    return services.any { it.service.className == FloatingWindowService::class.java.name }
}

private fun isScrollServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabled = manager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    val id = ComponentName(context, AccessibilityScrollService::class.java).flattenToString()
    if (enabled.any { it.id == id }) {
        return true
    }
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    return enabledServices.split(':').any { it.equals(id, ignoreCase = true) }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    TermCatTheme {
        HomeScreen(
            onStartOverlay = {},
            onStopOverlay = {},
            onOpenSettings = {},
            onOpenAccessibility = {}
        )
    }
}
