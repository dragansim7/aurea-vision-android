package com.dragansim.aureavision.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dragansim.aureavision.data.AppDb
import com.dragansim.aureavision.data.SettingsStore
import com.dragansim.aureavision.data.toItem
import com.dragansim.aureavision.data.toRow
import com.dragansim.aureavision.domain.HistoryItem
import com.dragansim.aureavision.domain.ImageMeasure
import com.dragansim.aureavision.domain.OutputFormat
import com.dragansim.aureavision.domain.PresetId
import com.dragansim.aureavision.domain.ReadingFactory
import com.dragansim.aureavision.domain.Session
import com.dragansim.aureavision.domain.Settings
import com.dragansim.aureavision.domain.composePrompt
import com.dragansim.aureavision.domain.exportMarkdown
import com.dragansim.aureavision.domain.exportPlain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class StudioUi(
    val settings: Settings = Settings(),
    val history: List<HistoryItem> = emptyList(),
    val session: Session? = null,
    val pending: Boolean = false,
    val error: String? = null,
    val copied: String? = null,
    val ready: Boolean = false,
)

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val db = AppDb.create(app)
    private val session = MutableStateFlow<Session?>(null)
    private val pending = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val copied = MutableStateFlow<String?>(null)

    val ui: StateFlow<StudioUi> = combine(
        store.settings,
        db.history().observe(),
        session,
        pending,
        error,
    ) { settings, rows, current, busy, err ->
        StudioUi(
            settings = settings,
            history = rows.map { it.toItem() },
            session = current,
            pending = busy,
            error = err,
            ready = true,
        )
    }.combine(copied) { base, copyFlag ->
        base.copy(copied = copyFlag)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioUi())

    fun updateSettings(next: Settings) {
        viewModelScope.launch {
            store.save(next)
            val current = session.value ?: return@launch
            val reading = current.reading ?: return@launch
            if (current.dirty) {
                session.value = current
                return@launch
            }
            val draft = composePrompt(reading, next, current.measured.palette)
            session.value = current.copy(prompt = draft.prompt, negative = draft.negative, dirty = false)
        }
    }

    fun setPreset(id: PresetId) {
        updateSettings(ui.value.settings.copy(preset = id))
    }

    fun setFormat(format: OutputFormat) {
        updateSettings(ui.value.settings.copy(format = format))
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            error.value = null
            pending.value = true
            try {
                session.value = withContext(Dispatchers.IO) { prepare(uri) }
            } catch (ex: Exception) {
                error.value = ex.message ?: "That file could not be opened."
            } finally {
                pending.value = false
            }
        }
    }

    fun readImage() {
        val current = session.value ?: return
        if (pending.value) return
        viewModelScope.launch {
            pending.value = true
            error.value = null
            try {
                val reading = ReadingFactory.measured(current.measured)
                val draft = composePrompt(reading, ui.value.settings, current.measured.palette)
                val id = current.historyId ?: UUID.randomUUID().toString()
                val next = current.copy(
                    reading = reading,
                    prompt = if (current.dirty) current.prompt else draft.prompt,
                    negative = if (current.dirty) current.negative else draft.negative,
                    historyId = id,
                )
                session.value = next
                persist(next, id)
                if (ui.value.settings.autoCopy && next.prompt.isNotBlank()) copied.value = "prompt"
            } catch (ex: Exception) {
                error.value = ex.message ?: "The reading could not be completed."
            } finally {
                pending.value = false
            }
        }
    }

    fun editPrompt(text: String) {
        session.update { it?.copy(prompt = text, dirty = true) }
        persistCurrent()
    }

    fun editNegative(text: String) {
        session.update { it?.copy(negative = text, dirty = true) }
        persistCurrent()
    }

    fun restoreDraft() {
        val current = session.value ?: return
        val reading = current.reading ?: return
        val draft = composePrompt(reading, ui.value.settings, current.measured.palette)
        session.value = current.copy(prompt = draft.prompt, negative = draft.negative, dirty = false)
        persistCurrent()
    }

    fun toggleFavorite() {
        val id = session.value?.historyId ?: return
        val current = ui.value.history.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { db.history().setFavorite(id, !current.favorite) }
    }

    fun toggleFavorite(id: String) {
        val current = ui.value.history.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { db.history().setFavorite(id, !current.favorite) }
    }

    fun deleteHistory(id: String) {
        viewModelScope.launch {
            db.history().delete(id)
            if (session.value?.historyId == id) session.value = session.value?.copy(historyId = null)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { db.history().clear() }
    }

    fun openHistory(item: HistoryItem) {
        updateSettings(ui.value.settings.copy(preset = item.preset, format = item.format))
        session.value = Session(
            fileName = item.fileName,
            imagePath = item.thumbPath,
            width = item.width,
            height = item.height,
            measured = item.measured,
            reading = item.reading,
            prompt = item.prompt,
            negative = item.negative,
            dirty = true,
            historyId = item.id,
        )
        error.value = null
    }

    fun markCopied(which: String) {
        copied.value = which
        viewModelScope.launch {
            kotlinx.coroutines.delay(1600)
            if (copied.value == which) copied.value = null
        }
    }

    fun exportText(): String? {
        val current = session.value ?: return null
        return exportPlain(current.fileName, current.prompt, current.negative, ui.value.settings.includeNegative)
    }

    fun exportMd(): String? {
        val current = session.value ?: return null
        return exportMarkdown(
            current.fileName, current.prompt, current.negative,
            ui.value.settings.includeNegative, current.measured,
        )
    }

    fun shareIntent(text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

    private fun persistCurrent() {
        val current = session.value ?: return
        val id = current.historyId ?: return
        viewModelScope.launch { persist(current, id) }
    }

    private suspend fun persist(current: Session, id: String) {
        val reading = current.reading ?: return
        val existing = ui.value.history.firstOrNull { it.id == id }
        val item = HistoryItem(
            id = id,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            fileName = current.fileName,
            thumbPath = current.imagePath,
            width = current.width,
            height = current.height,
            measured = current.measured,
            reading = reading,
            prompt = current.prompt,
            negative = current.negative,
            favorite = existing?.favorite ?: false,
            preset = ui.value.settings.preset,
            format = ui.value.settings.format,
        )
        db.history().upsert(item.toRow())
    }

    private fun prepare(uri: Uri): Session {
        val app = getApplication<Application>()
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
        val bitmap = decode(app, uri) ?: throw IllegalStateException("This image could not be decoded.")
        if (bitmap.width < 8 || bitmap.height < 8) throw IllegalStateException("That image is too small to read.")
        val scaled = scaleForMeasure(bitmap)
        val measured = ImageMeasure.analyze(scaled)
        val dir = File(app.filesDir, "thumbs").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 82, out) }
        if (scaled !== bitmap) scaled.recycle()
        return Session(
            fileName = name,
            imagePath = file.absolutePath,
            width = measured.width,
            height = measured.height,
            measured = measured.copy(width = bitmap.width, height = bitmap.height),
        )
    }

    private fun decode(app: Application, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(app.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }

    private fun scaleForMeasure(src: Bitmap): Bitmap {
        val max = 1024
        val longest = maxOf(src.width, src.height)
        if (longest <= max) return src
        val scale = max.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
