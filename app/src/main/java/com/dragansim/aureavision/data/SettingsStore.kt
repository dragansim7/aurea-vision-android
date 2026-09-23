package com.dragansim.aureavision.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dragansim.aureavision.domain.DetailLevel
import com.dragansim.aureavision.domain.OutputFormat
import com.dragansim.aureavision.domain.PresetId
import com.dragansim.aureavision.domain.PreviewFit
import com.dragansim.aureavision.domain.PromptLength
import com.dragansim.aureavision.domain.Settings
import com.dragansim.aureavision.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("aurea_settings")

class SettingsStore(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val length = stringPreferencesKey("length")
    private val detail = stringPreferencesKey("detail")
    private val creativity = intPreferencesKey("creativity")
    private val format = stringPreferencesKey("format")
    private val autoCopy = booleanPreferencesKey("auto_copy")
    private val previewFit = stringPreferencesKey("preview_fit")
    private val includeNegative = booleanPreferencesKey("include_negative")
    private val preset = stringPreferencesKey("preset")

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            theme = prefs[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Dark,
            length = prefs[length]?.let { runCatching { PromptLength.valueOf(it) }.getOrNull() } ?: PromptLength.Standard,
            detail = prefs[detail]?.let { runCatching { DetailLevel.valueOf(it) }.getOrNull() } ?: DetailLevel.Balanced,
            creativity = prefs[creativity] ?: 42,
            format = prefs[format]?.let { runCatching { OutputFormat.valueOf(it) }.getOrNull() } ?: OutputFormat.Paragraph,
            autoCopy = prefs[autoCopy] ?: false,
            previewFit = prefs[previewFit]?.let { runCatching { PreviewFit.valueOf(it) }.getOrNull() } ?: PreviewFit.Contain,
            includeNegative = prefs[includeNegative] ?: false,
            preset = prefs[preset]?.let { runCatching { PresetId.valueOf(it) }.getOrNull() } ?: PresetId.Photorealistic,
        )
    }

    suspend fun save(value: Settings) {
        context.dataStore.edit { prefs ->
            prefs[theme] = value.theme.name
            prefs[length] = value.length.name
            prefs[detail] = value.detail.name
            prefs[creativity] = value.creativity
            prefs[format] = value.format.name
            prefs[autoCopy] = value.autoCopy
            prefs[previewFit] = value.previewFit.name
            prefs[includeNegative] = value.includeNegative
            prefs[preset] = value.preset.name
        }
    }
}
