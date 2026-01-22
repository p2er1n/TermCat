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
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
    val accentColor = Color(0xFF1C274C)
    val baseColor = Color(0xFFA0A6B6)
    val cardColor = Color(0xFFF6F7FB)
    val successGreen = Color(0xFF16A34A)
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF2F4F8), Color(0xFFDDE2EA))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                border = BorderStroke(1.dp, baseColor.copy(alpha = 0.35f)),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            fontSize = 30.sp
                        )
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFF2F3441),
                            fontFamily = FontFamily.Serif
                        )
                    )
                    SettingToggleRow(
                        label = stringResource(R.string.home_status_permission),
                        description = stringResource(R.string.home_manage_permission),
                        checked = overlayEnabled,
                        enabled = true,
                        onToggle = { onOpenSettings() },
                        successGreen = successGreen
                    )
                    SettingToggleRow(
                        label = stringResource(R.string.accessibility_title),
                        description = stringResource(R.string.accessibility_desc),
                        checked = accessibilityEnabled,
                        enabled = true,
                        onToggle = { onOpenAccessibility() },
                        successGreen = successGreen
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_status_window),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                    color = accentColor
                                )
                            )
                            Text(
                                text = stringResource(R.string.home_primary_action_start),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Serif,
                                    color = Color(0xFF4B5563)
                                )
                            )
                        }
                        val enabled = overlayRunning || accessibilityEnabled
                        val iconAlpha = if (overlayRunning) 1f else 0.35f
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (pressed) 0.92f else 1f,
                            animationSpec = tween(durationMillis = 120),
                            label = "overlayIconScale"
                        )
                        Image(
                            painter = painterResource(R.drawable.overlay_icon),
                            contentDescription = stringResource(R.string.home_primary_action_start),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .alpha(iconAlpha)
                                .scale(scale)
                                .clickable(
                                    enabled = enabled,
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    if (overlayRunning) {
                                        onStopOverlay()
                                    } else {
                                        onStartOverlay()
                                    }
                                }
                        )
                    }
                }
            }

            SectionTitle(text = stringResource(R.string.section_ocr), color = accentColor)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = BorderStroke(1.dp, baseColor.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OcrEngineCard(
                        selected = ocrEngine,
                        onSelect = { ocrEngine = it },
                        cardColor = cardColor,
                        cardShape = cardShape,
                        accentColor = accentColor
                    )
                    CaptureSettingsCard(
                        maxCapturePagesText = maxCapturePagesText,
                        onMaxCapturePagesChange = { maxCapturePagesText = it },
                        cardColor = cardColor,
                        cardShape = cardShape,
                        accentColor = accentColor
                    )
                }
            }

            SectionTitle(text = stringResource(R.string.section_analysis), color = accentColor)
            LlmConfigCard(
                endpoint = llmEndpoint,
                apiKey = llmApiKey,
                model = llmModel,
                onEndpointChange = { llmEndpoint = it },
                onApiKeyChange = { llmApiKey = it },
                onModelChange = { llmModel = it },
                cardColor = cardColor,
                cardShape = cardShape,
                accentColor = accentColor
            )

            Text(
                text = stringResource(R.string.home_footer),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF3B4251),
                    fontFamily = FontFamily.Serif
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    successGreen: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFF2F3441)
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFF4B5563)
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            thumbContent = {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = successGreen
                    )
                }
            }
        )
    }
}


@Composable
private fun LlmConfigCard(
    endpoint: String,
    apiKey: String,
    model: String,
    onEndpointChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    cardColor: Color,
    cardShape: androidx.compose.foundation.shape.RoundedCornerShape,
    accentColor: Color
) {
    val endpointError = remember(endpoint) { !isValidEndpoint(endpoint) }
    val modelError = remember(model) { model.isBlank() }
    val apiKeyError = remember(apiKey) { apiKey.isBlank() || apiKey.length < 8 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.llm_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accentColor,
                    fontFamily = FontFamily.Serif
                )
            )
            Text(
                text = stringResource(R.string.llm_required),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                )
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                label = { Text(text = stringResource(R.string.llm_endpoint_label)) },
                placeholder = { Text(text = stringResource(R.string.llm_endpoint_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = endpointError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    focusedLabelColor = accentColor,
                    cursorColor = accentColor
                ),
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    focusedLabelColor = accentColor,
                    cursorColor = accentColor
                ),
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    focusedLabelColor = accentColor,
                    cursorColor = accentColor
                ),
                supportingText = {
                    if (apiKeyError) {
                        Text(text = stringResource(R.string.llm_api_key_error))
                    }
                }
            )
            Text(
                text = stringResource(R.string.llm_helper_text),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                )
            )
        }
    }
}

@Composable
private fun AccessibilityToggle(
    enabled: Boolean,
    onOpenAccessibility: () -> Unit,
    cardColor: Color,
    cardShape: androidx.compose.foundation.shape.RoundedCornerShape,
    accentColor: Color,
    successGreen: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.accessibility_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accentColor,
                    fontFamily = FontFamily.Serif
                )
            )
            Text(
                text = stringResource(R.string.accessibility_desc),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                )
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif
                    )
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { onOpenAccessibility() },
                    thumbContent = {
                        if (enabled) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = successGreen
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OcrEngineCard(
    selected: String,
    onSelect: (String) -> Unit,
    cardColor: Color,
    cardShape: androidx.compose.foundation.shape.RoundedCornerShape,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.ocr_engine_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accentColor,
                    fontFamily = FontFamily.Serif
                )
            )
            Text(
                text = stringResource(R.string.ocr_engine_desc),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                )
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Serif
            )
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun CaptureSettingsCard(
    maxCapturePagesText: String,
    onMaxCapturePagesChange: (String) -> Unit,
    cardColor: Color,
    cardShape: androidx.compose.foundation.shape.RoundedCornerShape,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.capture_settings_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accentColor,
                    fontFamily = FontFamily.Serif
                )
            )
            Text(
                text = stringResource(R.string.capture_settings_desc),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                )
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
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                )
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
private fun SectionTitle(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )
    )
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
