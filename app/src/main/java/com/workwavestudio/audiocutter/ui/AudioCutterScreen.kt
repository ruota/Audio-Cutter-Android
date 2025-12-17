package com.workwavestudio.audiocutter.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.google.accompanist.flowlayout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.workwavestudio.audiocutter.AudioCutterViewModel
import com.workwavestudio.audiocutter.AudioUiState
import com.workwavestudio.audiocutter.OutputFormat
import com.workwavestudio.audiocutter.QualityPreset
import com.workwavestudio.audiocutter.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.roundToInt
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AudioCutterScreen(
    viewModel: AudioCutterViewModel,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by remember { androidx.compose.runtime.derivedStateOf { viewModel.uiState } }
    var interstitialAd: InterstitialAd? by remember { mutableStateOf<InterstitialAd?>(null) }
    val loadAd: () -> Unit = {
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            com.workwavestudio.audiocutter.BuildConfig.INTERSTITIAL_AD_UNIT_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }
    LaunchedEffect(Unit) {
        loadAd()
    }
    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            viewModel.onAudioPicked(it, context)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permission required to import audio")
            }
        }
    }
    val saveResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/*")
    ) { uri: Uri? ->
        val output = uiState.trimmedOutput
        if (uri != null && output != null) {
            val successMsg = context.getString(R.string.save_success)
            scope.launch {
                val result = runCatching {
                    context.contentResolver.openInputStream(output)?.use { input ->
                        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                            input.copyTo(out)
                        } ?: error("Cannot open destination")
                    } ?: error("Cannot open source")
                }
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar(successMsg)
                } else {
                    val msg = context.getString(
                        R.string.save_failed,
                        result.exceptionOrNull()?.localizedMessage ?: "unknown"
                    )
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }
    val importAudio: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        } else {
            permissionLauncher.launch(permission)
        }
    }
    LaunchedEffect(uiState.showFullScreenBanner) {
        if (uiState.showFullScreenBanner) {
            val activity = context.findActivity()
            val ad = interstitialAd
            if (ad != null && activity != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        loadAd()
                        viewModel.onFullScreenBannerFinished()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        interstitialAd = null
                        loadAd()
                        viewModel.onFullScreenBannerFinished()
                    }
                }
                ad.show(activity)
            } else {
                viewModel.onFullScreenBannerFinished()
                if (ad == null) loadAd()
            }
        }
    }

    val gradientBackground = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
        )
    )

    androidx.compose.material3.Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroCard()

                AudioInfoCard(
                    uiState = uiState,
                    onPickAudio = importAudio
                )

                EncodingControls(
                    uiState = uiState,
                    onFormatChange = viewModel::updateFormat,
                    onQualityChange = viewModel::updateQuality,
                    onSpeedChange = viewModel::updateSpeed
                )

                WaveformTrimCard(
                    uiState = uiState,
                    onRangeChange = viewModel::onRangeChange
                )

                ActionButtons(
                    uiState = uiState,
                    onPickAudio = importAudio,
                    onPlayPreview = { viewModel.togglePlayback(context) },
                    onTrim = { viewModel.trim(context) }
                )

                val output = uiState.trimmedOutput
                if (output != null) {
                    OutputCard(
                        output = output,
                        onSave = { saveResultLauncher.launch("clip_${System.currentTimeMillis()}.${uiState.outputFormat.extension}") }
                    )
                }

                BannerAd(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (uiState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.trimming),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(26.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hero_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    text = stringResource(R.string.hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.9f)
                )
            }
        }
    }
}

@Composable
private fun AudioInfoCard(
    uiState: AudioUiState,
    onPickAudio: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.uri == null) {
                Button(
                    onClick = onPickAudio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.import_audio))
                }
            } else {
                Text(
                    text = stringResource(R.string.clip_current),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                    Text(
                        text = uiState.fileName.ifEmpty { stringResource(R.string.no_file) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBadge(
                        text = stringResource(R.string.duration_label, formatTime(uiState.durationMs)),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    )
                    StatusBadge(
                        text = stringResource(
                            R.string.output_label,
                            uiState.outputFormat.extension.uppercase()
                        ),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WaveformTrimCard(
    uiState: AudioUiState,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit
) {
    val range = uiState.startMs.toFloat()..uiState.endMs.toFloat()
    val maxRange = uiState.durationMs.takeIf { it > 0 }?.toFloat() ?: 1_000f
    fun formatToTimeInput(ms: Long): String {
        val totalMs = ms.coerceAtLeast(0)
        val minutes = totalMs / 60_000
        val seconds = (totalMs % 60_000) / 1000f
        return String.format(Locale.US, "%02d:%05.2f", minutes, seconds)
    }
    fun snapToMsFromInput(input: String): Long? {
        val cleaned = input.trim().replace(',', '.')
        val parts = cleaned.split(":")
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        val seconds = parts[1].toDoubleOrNull() ?: return null
        val totalSeconds = minutes * 60 + seconds
        if (totalSeconds < 0) return null
        return (totalSeconds * 100).roundToLong() * 10
    }
    var startInput by remember(uiState.startMs) { mutableStateOf(formatToTimeInput(uiState.startMs)) }
    var endInput by remember(uiState.endMs) { mutableStateOf(formatToTimeInput(uiState.endMs)) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.waveform_title),
                fontWeight = FontWeight.SemiBold
            )
            if (uiState.waveform.isEmpty()) {
            } else {
                WaveformView(
                    amplitudes = uiState.waveform,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    color = MaterialTheme.colorScheme.primary,
                    selectionColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    playheadColor = MaterialTheme.colorScheme.tertiary,
                    selectionRange = uiState.startMs.toFloat()..uiState.endMs.toFloat(),
                    positionMs = if (uiState.isPlaying) uiState.playbackPositionMs else uiState.startMs,
                    durationMs = uiState.durationMs
                )
            }
            RangeSlider(
                value = range,
                onValueChange = { values ->
                    val clampedStart = values.start.coerceIn(0f, values.endInclusive)
                    val clampedEnd = values.endInclusive.coerceIn(clampedStart, maxRange)
                    onRangeChange(clampedStart..clampedEnd)
                },
                valueRange = 0f..maxRange,
                steps = 0,
                enabled = uiState.durationMs > 0
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = startInput,
                    onValueChange = { text ->
                        startInput = text
                        val snapped = snapToMsFromInput(text)
                        if (snapped != null) {
                            val durationLimit = if (uiState.durationMs > 0) uiState.durationMs else Long.MAX_VALUE
                            val safeStart = snapped.coerceIn(0, min(durationLimit, uiState.endMs))
                            onRangeChange(safeStart.toFloat()..uiState.endMs.toFloat())
                        }
                    },
                    label = { Text("${stringResource(R.string.range_start)} (mm:ss)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endInput,
                    onValueChange = { text ->
                        endInput = text
                        val snapped = snapToMsFromInput(text)
                        if (snapped != null) {
                            val durationLimit = if (uiState.durationMs > 0) uiState.durationMs else Long.MAX_VALUE
                            val safeEnd = snapped.coerceIn(uiState.startMs, durationLimit)
                            onRangeChange(uiState.startMs.toFloat()..safeEnd.toFloat())
                        }
                    },
                    label = { Text("${stringResource(R.string.range_end)} (mm:ss)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EncodingControls(
    uiState: AudioUiState,
    onFormatChange: (OutputFormat) -> Unit,
    onQualityChange: (QualityPreset) -> Unit,
    onSpeedChange: (Float) -> Unit
    ) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.format_quality_title),
                fontWeight = FontWeight.SemiBold
            )
            OptionChipsRow(
                title = stringResource(R.string.format_title),
                options = OutputFormat.values().toList(),
                selected = uiState.outputFormat,
                label = { it.label },
                onSelect = onFormatChange
            )
            OptionChipsRow(
                title = stringResource(R.string.quality_title),
                options = QualityPreset.values().toList(),
                selected = uiState.quality,
                label = { it.label },
                onSelect = onQualityChange
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.speed_title, String.format(Locale.US, "%.1fx", uiState.speed)),
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = uiState.speed,
                    onValueChange = { raw ->
                        val snapped = (raw * 10f).roundToInt() / 10f
                        onSpeedChange(snapped.coerceIn(0.1f, 4f))
                    },
                    valueRange = 0.1f..4f,
                    steps = 0
                )
                Text(
                    text = stringResource(R.string.speed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.8f)
                )
            }
        }
    }
}

@Composable
private fun WaveformView(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    selectionColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    playheadColor: Color = MaterialTheme.colorScheme.tertiary,
    selectionRange: ClosedFloatingPointRange<Float>? = null,
    positionMs: Long = 0L,
    durationMs: Long = 0L
) {
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        if (amplitudes.isEmpty()) return@Canvas
        val baseGap = with(density) { 2.dp.toPx() }
        val barCount = amplitudes.size
        var gap = baseGap
        var barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
        var totalBarsWidth = barWidth * barCount + gap * (barCount - 1)
        if (totalBarsWidth > size.width && totalBarsWidth > 0f) {
            val scale = size.width / totalBarsWidth
            barWidth *= scale
            gap *= scale
            totalBarsWidth = barWidth * barCount + gap * (barCount - 1)
        }
        val paddingX = max(0f, (size.width - totalBarsWidth) / 2f)
        val availableWidth = totalBarsWidth.coerceAtLeast(1f)
        val centerY = size.height / 2f
        val totalDuration = durationMs.coerceAtLeast(1L).toFloat()
        val selectionStart = selectionRange?.start ?: 0f
        val selectionEnd = selectionRange?.endInclusive ?: totalDuration
        val selectionStartX = if (totalDuration > 0f) paddingX + (selectionStart / totalDuration) * availableWidth else paddingX
        val selectionEndX = if (totalDuration > 0f) paddingX + (selectionEnd / totalDuration) * availableWidth else paddingX + availableWidth
        amplitudes.forEachIndexed { index, rawAmp ->
            val amp = rawAmp.coerceIn(0f, 1f)
            val barHeight = max(with(density) { 6.dp.toPx() }, amp * size.height)
            val x = paddingX + index * (barWidth + gap)
            val barStartX = x
            val barEndX = x + barWidth
            val inSelection = barEndX >= selectionStartX && barStartX <= selectionEndX
            drawRoundRect(
                color = if (inSelection) selectionColor else unselectedColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
        if (durationMs > 0 && positionMs >= 0) {
            val posFraction = (positionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
            val x = paddingX + posFraction * availableWidth
            drawLine(
                color = playheadColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = with(density) { 2.dp.toPx() }
            )
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> OptionChipsRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            mainAxisSpacing = 8.dp,
            crossAxisSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(option) }
                ) {
                    Text(
                        text = label(option),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    uiState: AudioUiState,
    onPickAudio: () -> Unit,
    onPlayPreview: () -> Unit,
    onTrim: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onPickAudio
            ) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.import_audio))
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = uiState.uri != null && !uiState.isProcessing,
                onClick = onPlayPreview
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isPlaying) stringResource(R.string.stop_preview)
                    else stringResource(R.string.listen)
                )
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            enabled = uiState.uri != null && !uiState.isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            onClick = onTrim
        ) {
            Icon(imageVector = Icons.Default.ContentCut, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (uiState.isProcessing) stringResource(R.string.trimming)
                else stringResource(R.string.trim_audio)
            )
        }
    }
}

@Composable
private fun TimePill(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(text = value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun OutputCard(output: Uri, onSave: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(R.string.save_to_device))
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
