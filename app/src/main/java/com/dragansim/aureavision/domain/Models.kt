package com.dragansim.aureavision.domain

enum class Confidence { High, Medium, Low }
enum class Source { Observed, Inferred }
enum class PresetId { Cinematic, Editorial, Photorealistic, Product, Concept, Minimalist, Fantasy, Luxury }
enum class PromptLength { Brief, Standard, Extended }
enum class DetailLevel { Lean, Balanced, Exhaustive }
enum class OutputFormat { Paragraph, Comma, Template }
enum class ThemeMode { Dark, Light, System }
enum class PreviewFit { Contain, Cover }

data class Finding(
    val text: String,
    val confidence: Confidence = Confidence.Medium,
    val source: Source = Source.Inferred,
)

data class Swatch(
    val hex: String,
    val name: String,
    val weight: Float,
)

data class Measured(
    val width: Int,
    val height: Int,
    val palette: List<Swatch>,
    val luminance: Float,
    val contrast: Float,
    val edge: Float,
    val light: String,
    val contrastNote: String,
    val frame: String,
    val detail: String,
)

data class Reading(
    val mode: String,
    val subject: Finding,
    val scene: Finding,
    val objects: List<Pair<String, Confidence>> = emptyList(),
    val composition: Finding,
    val lighting: Finding,
    val texture: Finding,
    val mood: Finding,
    val style: Finding,
    val camera: Finding,
    val materials: Finding,
    val qualityNotes: List<Finding> = emptyList(),
    val visibleText: String = "",
    val uncertainties: List<String> = emptyList(),
)

data class Settings(
    val theme: ThemeMode = ThemeMode.Dark,
    val length: PromptLength = PromptLength.Standard,
    val detail: DetailLevel = DetailLevel.Balanced,
    val creativity: Int = 42,
    val format: OutputFormat = OutputFormat.Paragraph,
    val autoCopy: Boolean = false,
    val previewFit: PreviewFit = PreviewFit.Contain,
    val includeNegative: Boolean = false,
    val preset: PresetId = PresetId.Photorealistic,
)

data class HistoryItem(
    val id: String,
    val createdAt: Long,
    val fileName: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val measured: Measured,
    val reading: Reading,
    val prompt: String,
    val negative: String,
    val favorite: Boolean,
    val preset: PresetId,
    val format: OutputFormat,
)

data class Session(
    val fileName: String,
    val imagePath: String,
    val width: Int,
    val height: Int,
    val measured: Measured,
    val reading: Reading? = null,
    val prompt: String = "",
    val negative: String = "",
    val dirty: Boolean = false,
    val historyId: String? = null,
)
