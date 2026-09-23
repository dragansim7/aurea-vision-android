package com.dragansim.aureavision.domain

private val PLACEHOLDER = Regex("not identified|not inferred|was not ", RegexOption.IGNORE_CASE)

fun isUseful(finding: Finding?): Boolean =
    finding != null && finding.text.isNotBlank() && !PLACEHOLDER.containsMatchIn(finding.text)

private fun clause(text: String): String =
    text.trim().replace(Regex("\\s+"), " ").replace(Regex("[.!?]+$"), "")

private fun sentence(text: String): String {
    val clean = clause(text)
    if (clean.isEmpty()) return ""
    return clean.replaceFirstChar { it.uppercase() } + "."
}

private fun phrase(finding: Finding): String {
    val text = clause(finding.text)
    if (text.isEmpty()) return ""
    if (finding.confidence != Confidence.Low) return text
    if (Regex("^(possibly|something like|unidentified|unclear|not )", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
        return text
    }
    return "something like $text"
}

private fun lowerFirst(text: String): String {
    if (text.length < 2) return text.lowercase()
    return if (text[0].isUpperCase() && text[1].isLowerCase()) {
        text[0].lowercase() + text.substring(1)
    } else {
        text
    }
}

private fun joinList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    2 -> "${items[0]} and ${items[1]}"
    else -> items.dropLast(1).joinToString(", ") + ", and ${items.last()}"
}

private fun paletteLine(swatches: List<Swatch>): String {
    val names = swatches.map { it.name.lowercase() }.distinct().take(4)
    return when {
        names.isEmpty() -> ""
        names.size == 1 -> "Palette of ${names[0]}"
        else -> "Palette of ${joinList(names)}"
    }
}

private fun wordBudget(settings: Settings): Int {
    val base = when (settings.length) {
        PromptLength.Brief -> 58
        PromptLength.Extended -> 210
        PromptLength.Standard -> 120
    }
    return when (settings.detail) {
        DetailLevel.Lean -> (base * 0.8).toInt()
        DetailLevel.Exhaustive -> (base * 1.15).toInt()
        DetailLevel.Balanced -> base
    }
}

private fun fitWords(text: String, max: Int): String {
    val words = text.trim().split(Regex("\\s+"))
    if (words.size <= max) return text.trim()
    val sentences = text.trim().split(Regex("(?<=[.!?])\\s+")).toMutableList()
    while (sentences.size > 1 && sentences.joinToString(" ").split(Regex("\\s+")).size > max) {
        sentences.removeAt(sentences.lastIndex)
    }
    var out = sentences.joinToString(" ")
    if (out.split(Regex("\\s+")).size > max) {
        out = out.split(Regex("\\s+")).take(max).joinToString(" ").replace(Regex("[,:;]$"), "") + "."
    }
    return out
}

private fun collect(reading: Reading, settings: Settings, palette: List<Swatch>): List<String> {
    val preset = Presets.byId(settings.preset)
    val parts = mutableListOf<String>()
    val subject = if (isUseful(reading.subject)) phrase(reading.subject) else ""
    val lead = if (settings.creativity < 28 && !preset.stylistic) "Photograph" else preset.lead
    parts += if (subject.isNotEmpty()) sentence("$lead of ${lowerFirst(subject)}") else sentence(lead)
    if (isUseful(reading.scene)) parts += sentence(phrase(reading.scene))
    if (settings.detail != DetailLevel.Lean && isUseful(reading.materials)) parts += sentence(phrase(reading.materials))
    if (settings.detail != DetailLevel.Lean && isUseful(reading.texture)) parts += sentence(phrase(reading.texture))
    if (isUseful(reading.lighting)) parts += sentence(phrase(reading.lighting))
    if (settings.detail != DetailLevel.Lean && isUseful(reading.composition)) parts += sentence(phrase(reading.composition))
    if ((settings.detail == DetailLevel.Exhaustive || settings.creativity > 62) && isUseful(reading.camera)) {
        parts += sentence(phrase(reading.camera))
    }
    val colors = paletteLine(palette)
    if (colors.isNotEmpty()) parts += sentence(colors)
    if (settings.detail == DetailLevel.Exhaustive && reading.objects.isNotEmpty()) {
        val names = reading.objects.take(6).map { if (it.second == Confidence.Low) "possibly ${it.first}" else it.first }
        parts += sentence("Also visible: ${joinList(names)}")
    }
    if (settings.creativity > 30 && settings.detail != DetailLevel.Lean && isUseful(reading.mood)) {
        parts += sentence(phrase(reading.mood))
    }
    if (settings.creativity > 48 && (preset.stylistic || isUseful(reading.style))) {
        val style = if (isUseful(reading.style)) phrase(reading.style) else ""
        val voice = if (settings.creativity > 68) preset.voice.take(3).joinToString(", ") else preset.voice.first()
        val layer = listOf(style, voice).filter { it.isNotBlank() }.joinToString(", ")
        if (layer.isNotBlank()) parts += sentence("Style direction: $layer")
    }
    return parts.filter { it.isNotBlank() }
}

private fun paragraph(reading: Reading, settings: Settings, palette: List<Swatch>): String =
    fitWords(collect(reading, settings, palette).joinToString(" "), wordBudget(settings))

private fun comma(reading: Reading, settings: Settings, palette: List<Swatch>): String {
    val preset = Presets.byId(settings.preset)
    val tags = mutableListOf<String>()
    if (isUseful(reading.subject)) tags += phrase(reading.subject)
    if (isUseful(reading.scene)) tags += phrase(reading.scene)
    if (isUseful(reading.lighting)) tags += phrase(reading.lighting)
    if (settings.detail != DetailLevel.Lean && isUseful(reading.materials)) tags += phrase(reading.materials)
    if (settings.detail != DetailLevel.Lean && isUseful(reading.texture)) tags += phrase(reading.texture)
    if (settings.detail != DetailLevel.Lean && isUseful(reading.composition)) tags += phrase(reading.composition)
    palette.take(4).forEach { tags += it.name.lowercase() }
    if (settings.creativity > 34 && isUseful(reading.mood)) tags += phrase(reading.mood)
    if (settings.creativity > 40) tags += preset.voice.take(if (settings.creativity > 70) 4 else 2)
    val cap = when (settings.length) {
        PromptLength.Brief -> 10
        PromptLength.Extended -> 24
        PromptLength.Standard -> 16
    }
    return tags.map { it.lowercase() }.distinct().take(cap).joinToString(", ")
}

private fun template(reading: Reading, settings: Settings, palette: List<Swatch>): String {
    val preset = Presets.byId(settings.preset)
    fun line(label: String, finding: Finding): String {
        if (!isUseful(finding)) return ""
        val mark = when {
            finding.confidence == Confidence.Low -> " (uncertain)"
            finding.source == Source.Inferred -> " (inferred)"
            else -> ""
        }
        return "$label: ${clause(finding.text)}$mark"
    }
    val lines = listOf(
        line("Subject", reading.subject),
        line("Scene", reading.scene),
        if (settings.detail == DetailLevel.Lean) "" else line("Materials", reading.materials),
        if (settings.detail == DetailLevel.Lean) "" else line("Texture", reading.texture),
        line("Lighting", reading.lighting),
        if (settings.detail == DetailLevel.Lean) "" else line("Composition", reading.composition),
        if (palette.isNotEmpty()) {
            "Palette: " + palette.take(5).joinToString(", ") { "${it.name} ${it.hex}" }
        } else {
            ""
        },
        if (settings.creativity > 35) {
            "Direction: ${preset.lead}. ${preset.voice.joinToString(", ")}."
        } else {
            "Direction: ${preset.lead}."
        },
    )
    return lines.filter { it.isNotBlank() }.joinToString("\n")
}

private val SHARED_NEGATIVE = listOf(
    "watermark", "logo overlay", "misspelled text", "extra fingers", "distorted anatomy", "warped geometry",
)

fun composeNegative(reading: Reading, settings: Settings): String {
    val tags = SHARED_NEGATIVE + Presets.byId(settings.preset).negative
    val extra = buildList {
        addAll(tags)
        if (reading.visibleText.isBlank()) add("unwanted text")
        if (reading.mode == "measured") add("invented subject details")
    }
    return extra.distinct().joinToString(", ")
}

data class Draft(val prompt: String, val negative: String)

fun composePrompt(reading: Reading, settings: Settings, palette: List<Swatch>): Draft {
    val prompt = when (settings.format) {
        OutputFormat.Comma -> comma(reading, settings, palette)
        OutputFormat.Template -> template(reading, settings, palette)
        OutputFormat.Paragraph -> paragraph(reading, settings, palette)
    }
    return Draft(prompt, composeNegative(reading, settings))
}

fun wordCount(text: String): Int {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    return trimmed.split(Regex("\\s+")).size
}

fun exportPlain(fileName: String, prompt: String, negative: String, includeNegative: Boolean): String = buildString {
    appendLine("Aurea Vision")
    appendLine(fileName)
    appendLine()
    appendLine(prompt)
    if (includeNegative && negative.isNotBlank()) {
        appendLine()
        appendLine("Negative")
        appendLine(negative)
    }
}

fun exportMarkdown(fileName: String, prompt: String, negative: String, includeNegative: Boolean, measured: Measured): String =
    buildString {
        appendLine("# $fileName")
        appendLine()
        appendLine("## Prompt")
        appendLine()
        appendLine(prompt)
        if (includeNegative && negative.isNotBlank()) {
            appendLine()
            appendLine("## Negative")
            appendLine()
            appendLine(negative)
        }
        appendLine()
        appendLine("## Measured")
        appendLine()
        appendLine("- Frame ${measured.width}×${measured.height}")
        appendLine("- ${measured.light} ${measured.contrastNote}")
        appendLine("- ${measured.frame}")
        appendLine("- Palette: ${measured.palette.joinToString(", ") { "${it.name} ${it.hex}" }}")
    }
