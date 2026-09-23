package com.dragansim.aureavision.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dragansim.aureavision.domain.HistoryItem
import com.dragansim.aureavision.domain.Measured
import com.dragansim.aureavision.domain.OutputFormat
import com.dragansim.aureavision.domain.PresetId
import com.dragansim.aureavision.domain.ReadingFactory
import com.dragansim.aureavision.domain.Swatch
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryRow(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val fileName: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val prompt: String,
    val negative: String,
    val favorite: Boolean,
    val preset: String,
    val format: String,
    val luminance: Float,
    val contrast: Float,
    val edge: Float,
    val light: String,
    val contrastNote: String,
    val frame: String,
    val detail: String,
    val palette: String,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observe(): Flow<List<HistoryRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: HistoryRow)

    @Query("UPDATE history SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryRow::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun history(): HistoryDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "aurea.db").build()
    }
}

fun HistoryRow.toItem(): HistoryItem {
    val measured = Measured(
        width = width,
        height = height,
        palette = decodePalette(palette),
        luminance = luminance,
        contrast = contrast,
        edge = edge,
        light = light,
        contrastNote = contrastNote,
        frame = frame,
        detail = detail,
    )
    return HistoryItem(
        id = id,
        createdAt = createdAt,
        fileName = fileName,
        thumbPath = thumbPath,
        width = width,
        height = height,
        measured = measured,
        reading = ReadingFactory.measured(measured),
        prompt = prompt,
        negative = negative,
        favorite = favorite,
        preset = runCatching { PresetId.valueOf(preset) }.getOrDefault(PresetId.Photorealistic),
        format = runCatching { OutputFormat.valueOf(format) }.getOrDefault(OutputFormat.Paragraph),
    )
}

fun HistoryItem.toRow(): HistoryRow = HistoryRow(
    id = id,
    createdAt = createdAt,
    fileName = fileName,
    thumbPath = thumbPath,
    width = width,
    height = height,
    prompt = prompt,
    negative = negative,
    favorite = favorite,
    preset = preset.name,
    format = format.name,
    luminance = measured.luminance,
    contrast = measured.contrast,
    edge = measured.edge,
    light = measured.light,
    contrastNote = measured.contrastNote,
    frame = measured.frame,
    detail = measured.detail,
    palette = encodePalette(measured.palette),
)

fun encodePalette(swatches: List<Swatch>): String =
    swatches.joinToString(";") { "${it.hex}|${it.name}|${it.weight}" }

fun decodePalette(raw: String): List<Swatch> =
    raw.split(";").mapNotNull { part ->
        val bits = part.split("|")
        if (bits.size < 3) null
        else Swatch(bits[0], bits[1], bits[2].toFloatOrNull() ?: 0f)
    }
