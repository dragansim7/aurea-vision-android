package com.dragansim.aureavision.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragansim.aureavision.domain.DetailLevel
import com.dragansim.aureavision.domain.HistoryItem
import com.dragansim.aureavision.domain.Measured
import com.dragansim.aureavision.domain.OutputFormat
import com.dragansim.aureavision.domain.PresetId
import com.dragansim.aureavision.domain.Presets
import com.dragansim.aureavision.domain.PreviewFit
import com.dragansim.aureavision.domain.PromptLength
import com.dragansim.aureavision.domain.Settings
import com.dragansim.aureavision.domain.Swatch
import com.dragansim.aureavision.domain.ThemeMode
import com.dragansim.aureavision.domain.wordCount
import com.dragansim.aureavision.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(vm: StudioViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var historyOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.importUri(uri)
    }
    val exportTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val body = vm.exportText() ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
    }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val body = vm.exportMd() ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
    }
    fun copy(label: String, text: String) {
        if (text.isBlank()) return
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        vm.markCopied(label)
        scope.launch { snack.showSnackbar("Copied") }
    }
    AppTheme(mode = state.settings.theme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Aurea Vision")
                            Text("Read an image. Draft a prompt.", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { historyOpen = true }) { Icon(Icons.Outlined.History, "History") }
                        IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Outlined.Settings, "Settings") }
                    },
                )
            },
        ) { pad ->
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Stage(
                    path = state.session?.imagePath,
                    name = state.session?.fileName,
                    width = state.session?.width ?: 0,
                    height = state.session?.height ?: 0,
                    fit = state.settings.previewFit,
                    onChoose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                if (state.session != null) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Presets.all.forEach { preset ->
                            FilterChip(selected = preset.id == state.settings.preset, onClick = { vm.setPreset(preset.id) }, label = { Text(preset.label) })
                        }
                    }
                    Button(onClick = vm::readImage, enabled = !state.pending, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.pending) "Reading" else if (state.session?.reading != null) "Read again" else "Draft from measurement")
                    }
                    Text("Vision is not bundled. Drafts use measured color, light, and frame.", style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Insights(state.session?.measured, state.pending)
                PromptBlock(
                    prompt = state.session?.prompt.orEmpty(),
                    negative = state.session?.negative.orEmpty(),
                    format = state.settings.format,
                    includeNegative = state.settings.includeNegative,
                    dirty = state.session?.dirty == true,
                    copied = state.copied,
                    favorite = state.history.any { it.id == state.session?.historyId && it.favorite },
                    enabled = state.session?.reading != null,
                    onFormat = vm::setFormat,
                    onPrompt = vm::editPrompt,
                    onNegative = vm::editNegative,
                    onToggleNegative = { vm.updateSettings(state.settings.copy(includeNegative = it)) },
                    onCopy = { copy("prompt", state.session?.prompt.orEmpty()) },
                    onCopyNegative = { copy("negative", state.session?.negative.orEmpty()) },
                    onExportText = { exportTxt.launch("${slug(state.session?.fileName)}.txt") },
                    onExportMarkdown = { exportMd.launch("${slug(state.session?.fileName)}.md") },
                    onShare = { vm.exportText()?.let { context.startActivity(Intent.createChooser(vm.shareIntent(it), "Share prompt")) } },
                    onFavorite = vm::toggleFavorite,
                    onRestore = vm::restoreDraft,
                    shape = shapeLabel(state.settings),
                )
            }
        }
        if (historyOpen) HistorySheet(state.history, { historyOpen = false }, { vm.openHistory(it); historyOpen = false }, vm::toggleFavorite, vm::deleteHistory, vm::clearHistory)
        if (settingsOpen) SettingsSheet(state.settings, { settingsOpen = false }, vm::updateSettings)
    }
}

private fun slug(name: String?) = name?.substringBeforeLast('.')?.ifBlank { "aurea" } ?: "aurea"

private fun shapeLabel(s: Settings): String {
    val length = when (s.length) { PromptLength.Brief -> "Brief"; PromptLength.Extended -> "Extended"; PromptLength.Standard -> "Standard" }
    val detail = when (s.detail) { DetailLevel.Lean -> "Lean"; DetailLevel.Exhaustive -> "Full"; DetailLevel.Balanced -> "Balanced" }
    return "$length · $detail · Creativity ${s.creativity}"
}

@Composable
private fun Stage(path: String?, name: String?, width: Int, height: Int, fit: PreviewFit, onChoose: () -> Unit) {
    val bitmap = remember(path) { path?.let { if (File(it).exists()) BitmapFactory.decodeFile(it) else null } }
    if (bitmap == null) {
        Column(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text("Place an image", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("JPEG, PNG, WebP, or GIF. Measured on-device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onChoose) { Text("Choose image") }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                bitmap.asImageBitmap(), name,
                contentScale = if (fit == PreviewFit.Cover) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${name.orEmpty()}  $width×$height", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onChoose) { Text("Replace") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Insights(measured: Measured?, pending: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pending) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("Reading light and material")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (measured == null) {
            Text("Nothing on the desk yet.", style = MaterialTheme.typography.headlineSmall)
            return
        }
        Card("Light", "${measured.light} ${measured.contrastNote}")
        Card("Frame", measured.frame)
        Card("Surface", measured.detail)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text("Palette", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                measured.palette.forEach { swatch -> SwatchView(swatch) }
            }
        }
    }
}

@Composable
private fun Card(title: String, body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwatchView(swatch: Swatch) {
    val hex = swatch.hex.removePrefix("#")
    val color = if (hex.length >= 6) Color(android.graphics.Color.parseColor("#$hex")) else Color.Gray
    Column(Modifier.width(72.dp)) {
        Box(Modifier.size(72.dp, 36.dp).clip(RoundedCornerShape(6.dp)).background(color))
        Text(swatch.name, style = MaterialTheme.typography.labelSmall)
        Text(swatch.hex, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptBlock(
    prompt: String, negative: String, format: OutputFormat, includeNegative: Boolean,
    dirty: Boolean, copied: String?, favorite: Boolean, enabled: Boolean, shape: String,
    onFormat: (OutputFormat) -> Unit, onPrompt: (String) -> Unit, onNegative: (String) -> Unit,
    onToggleNegative: (Boolean) -> Unit, onCopy: () -> Unit, onCopyNegative: () -> Unit,
    onExportText: () -> Unit, onExportMarkdown: () -> Unit, onShare: () -> Unit,
    onFavorite: () -> Unit, onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Prompt", style = MaterialTheme.typography.headlineSmall)
                Text(if (enabled) "Measured draft." else "A prompt appears after you read the image.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onCopy, enabled = prompt.isNotBlank()) {
                Icon(Icons.Outlined.ContentCopy, null)
                Spacer(Modifier.width(6.dp))
                Text(if (copied == "prompt") "Copied" else "Copy")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputFormat.entries.forEach { item ->
                FilterChip(selected = item == format, onClick = { onFormat(item) }, label = {
                    Text(when (item) { OutputFormat.Paragraph -> "Paragraph"; OutputFormat.Comma -> "Tags"; OutputFormat.Template -> "Template" })
                })
            }
        }
        OutlinedTextField(value = prompt, onValueChange = onPrompt, modifier = Modifier.fillMaxWidth().height(180.dp), placeholder = { Text("The draft will land here.") })
        Text("$shape · ${wordCount(prompt)} words", style = MaterialTheme.typography.labelSmall)
        if (dirty && enabled) TextButton(onClick = onRestore) { Text("Rebuild from measurement") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Negative prompt", modifier = Modifier.weight(1f))
            Switch(checked = includeNegative, onCheckedChange = onToggleNegative)
        }
        if (includeNegative) {
            OutlinedTextField(value = negative, onValueChange = onNegative, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedButton(onClick = onCopyNegative, enabled = negative.isNotBlank()) { Text(if (copied == "negative") "Copied" else "Copy negative") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportText, enabled = prompt.isNotBlank()) { Text("Export text") }
            OutlinedButton(onClick = onExportMarkdown, enabled = enabled) { Text("Export markdown") }
            OutlinedButton(onClick = onShare, enabled = prompt.isNotBlank()) { Text("Share") }
            OutlinedButton(onClick = onFavorite, enabled = enabled) {
                Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, null)
                Spacer(Modifier.width(4.dp))
                Text(if (favorite) "Favorited" else "Favorite")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    items: List<HistoryItem>, onDismiss: () -> Unit, onOpen: (HistoryItem) -> Unit,
    onFavorite: (String) -> Unit, onDelete: (String) -> Unit, onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("History", style = MaterialTheme.typography.headlineSmall)
            Text("Readings stay on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) Text("No readings yet.")
            items.forEach { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text(item.fileName, style = MaterialTheme.typography.titleSmall)
                    Text(SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(item.createdAt)), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { onOpen(item) }) { Text("Open") }
                        TextButton(onClick = { onFavorite(item.id) }) { Text(if (item.favorite) "Unfavorite" else "Favorite") }
                        TextButton(onClick = { onDelete(item.id) }) { Text("Delete") }
                    }
                }
            }
            if (items.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear history") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(settings: Settings, onDismiss: () -> Unit, onChange: (Settings) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Text("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { FilterChip(selected = settings.theme == it, onClick = { onChange(settings.copy(theme = it)) }, label = { Text(it.name) }) }
            }
            Text("Prompt length")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PromptLength.entries.forEach { FilterChip(selected = settings.length == it, onClick = { onChange(settings.copy(length = it)) }, label = { Text(it.name) }) }
            }
            Text("Detail")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLevel.entries.forEach { FilterChip(selected = settings.detail == it, onClick = { onChange(settings.copy(detail = it)) }, label = { Text(it.name) }) }
            }
            Text("Creativity ${settings.creativity}")
            Slider(value = settings.creativity.toFloat(), onValueChange = { onChange(settings.copy(creativity = it.toInt())) }, valueRange = 0f..100f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Copy after reading", modifier = Modifier.weight(1f))
                Switch(checked = settings.autoCopy, onCheckedChange = { onChange(settings.copy(autoCopy = it)) })
            }
            Text("Preview")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewFit.entries.forEach { FilterChip(selected = settings.previewFit == it, onClick = { onChange(settings.copy(previewFit = it)) }, label = { Text(it.name) }) }
            }
            OutlinedButton(onClick = { onChange(Settings(theme = settings.theme)) }) { Text("Reset preferences") }
        }
    }
}
