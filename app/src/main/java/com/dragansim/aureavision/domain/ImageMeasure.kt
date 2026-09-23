package com.dragansim.aureavision.domain

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageMeasure {
    fun analyze(bitmap: Bitmap): Measured {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(1, floor(sqrt((w * h) / 5000.0)).toInt())
        val buckets = mutableMapOf<String, FloatArray>()
        var lumSum = 0.0
        var lumSq = 0.0
        var n = 0
        var edgeSum = 0.0
        var edgeN = 0
        val cells = Array(9) { doubleArrayOf(0.0, 0.0) }

        fun lumAt(x: Int, y: Int): Double {
            val c = bitmap.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
            return (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0
        }

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 180) {
                    x += step
                    continue
                }
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                lumSum += lum
                lumSq += lum * lum
                n++
                val cx = min(2, floor((x.toDouble() / w) * 3).toInt())
                val cy = min(2, floor((y.toDouble() / h) * 3).toInt())
                val cell = cells[cy * 3 + cx]
                cell[0] += lum
                cell[1] += 1.0
                val key = "${(r / 24)}-${(g / 24)}-${(b / 24)}"
                val bucket = buckets.getOrPut(key) { floatArrayOf(0f, 0f, 0f, 0f) }
                bucket[0] += r.toFloat()
                bucket[1] += g.toFloat()
                bucket[2] += b.toFloat()
                bucket[3] += 1f
                if (x + step < w && y + step < h) {
                    edgeSum += abs(lum - lumAt(min(w - 1, x + step), y))
                    edgeSum += abs(lum - lumAt(x, min(h - 1, y + step)))
                    edgeN++
                }
                x += step
            }
            y += step
        }

        val luminance = if (n == 0) 0.0 else lumSum / n
        val variance = if (n == 0) 0.0 else max(0.0, lumSq / n - luminance * luminance)
        val contrast = sqrt(variance)
        val edge = if (edgeN == 0) 0.0 else edgeSum / edgeN
        val cellLum = cells.map { if (it[1] == 0.0) luminance else it[0] / it[1] }
        val palette = paletteFrom(buckets)
        return describe(
            width = w,
            height = h,
            luminance = luminance.toFloat(),
            contrast = contrast.toFloat(),
            edge = edge.toFloat(),
            palette = palette,
            frame = frameNote(cellLum),
        )
    }

    private fun paletteFrom(buckets: Map<String, FloatArray>): List<Swatch> {
        val ranked = buckets.values.sortedByDescending { it[3] }
        val picked = mutableListOf<Swatch>()
        var total = 0f
        for (bucket in ranked) {
            if (picked.size >= 6) break
            val n = bucket[3]
            if (n <= 0f) continue
            val hex = rgbToHex((bucket[0] / n).roundToInt(), (bucket[1] / n).roundToInt(), (bucket[2] / n).roundToInt())
            val twin = picked.firstOrNull { colorDistance(it.hex, hex) < 28 }
            if (twin != null) {
                val idx = picked.indexOf(twin)
                picked[idx] = twin.copy(weight = twin.weight + n)
            } else {
                picked += Swatch(hex, nameColor(hex), n)
            }
            total += n
        }
        return picked
            .map { it.copy(weight = if (total == 0f) 0f else it.weight / total) }
            .sortedByDescending { it.weight }
    }

    private fun frameNote(cells: List<Double>): String {
        val labels = listOf(
            "upper left", "upper center", "upper right",
            "middle left", "center", "middle right",
            "lower left", "lower center", "lower right",
        )
        var maxI = 0
        var minI = 0
        cells.forEachIndexed { index, value ->
            if (value > cells[maxI]) maxI = index
            if (value < cells[minI]) minI = index
        }
        val spread = cells[maxI] - cells[minI]
        if (spread < 0.08) return "Tonal weight is spread fairly evenly across the frame."
        return "The brightest weight sits in the ${labels[maxI]}. The darkest passage is ${labels[minI]}."
    }

    private fun describe(
        width: Int,
        height: Int,
        luminance: Float,
        contrast: Float,
        edge: Float,
        palette: List<Swatch>,
        frame: String,
    ): Measured {
        val light = when {
            luminance < 0.28f -> "Low key. Most of the frame sits in shadow."
            luminance > 0.72f -> "High key. The frame is bright overall."
            else -> "Mid key. Highlights and shadows share the frame."
        }
        val contrastNote = when {
            contrast < 0.1f -> "Tonal range is compressed."
            contrast > 0.22f -> "Tonal range is wide."
            else -> "Contrast is moderate."
        }
        val detail = when {
            edge < 0.045f -> "Surfaces read as soft or very smooth."
            edge > 0.14f -> "Edges are crisp, with fine local detail."
            else -> "Surface detail is moderate."
        }
        return Measured(width, height, palette, luminance, contrast, edge, light, contrastNote, frame, detail)
    }

    private fun rgbToHex(r: Int, g: Int, b: Int): String =
        "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    private fun colorDistance(a: String, b: String): Double {
        fun parse(hex: String): IntArray {
            val h = hex.removePrefix("#")
            return intArrayOf(
                h.substring(0, 2).toInt(16),
                h.substring(2, 4).toInt(16),
                h.substring(4, 6).toInt(16),
            )
        }
        val pa = parse(a)
        val pb = parse(b)
        return hypot((pa[0] - pb[0]).toDouble(), hypot((pa[1] - pb[1]).toDouble(), (pa[2] - pb[2]).toDouble()))
    }

    private fun nameColor(hex: String): String {
        val h = hex.removePrefix("#")
        val r = h.substring(0, 2).toInt(16)
        val g = h.substring(2, 4).toInt(16)
        val b = h.substring(4, 6).toInt(16)
        val names = listOf(
            Triple("black", 20, 20 to 20),
            Triple("charcoal", 50, 50 to 55),
            Triple("slate", 90, 100 to 110),
            Triple("warm gray", 140, 130 to 120),
            Triple("ivory", 230, 225 to 210),
            Triple("white", 245, 245 to 245),
            Triple("crimson", 170, 40 to 45),
            Triple("rust", 160, 70 to 40),
            Triple("amber", 200, 150 to 50),
            Triple("gold", 200, 170 to 70),
            Triple("olive", 110, 120 to 60),
            Triple("forest", 40, 90 to 50),
            Triple("sage", 140, 160 to 130),
            Triple("teal", 40, 120 to 120),
            Triple("navy", 30, 50 to 90),
            Triple("indigo", 60, 60 to 140),
            Triple("violet", 110, 70 to 150),
            Triple("blush", 210, 150 to 150),
            Triple("sand", 200, 175 to 140),
            Triple("brown", 110, 70 to 40),
        )
        var best = "neutral"
        var bestD = Double.MAX_VALUE
        for ((name, rr, gb) in names) {
            val d = hypot((r - rr).toDouble(), hypot((g - gb.first).toDouble(), (b - gb.second).toDouble()))
            if (d < bestD) {
                bestD = d
                best = name
            }
        }
        return best
    }
}

object ReadingFactory {
    fun measured(measured: Measured): Reading {
        val notes = localQuality(measured)
        return Reading(
            mode = "measured",
            subject = Finding("Subject not identified from pixels alone.", Confidence.Low, Source.Inferred),
            scene = Finding("Setting not identified from pixels alone.", Confidence.Low, Source.Inferred),
            composition = Finding(measured.frame, Confidence.Medium, Source.Observed),
            lighting = Finding("${measured.light} ${measured.contrastNote}", Confidence.High, Source.Observed),
            texture = Finding(measured.detail, Confidence.Medium, Source.Observed),
            mood = Finding("Mood was not inferred.", Confidence.Low, Source.Inferred),
            style = Finding("Style was not inferred.", Confidence.Low, Source.Inferred),
            camera = Finding("Camera character was not inferred.", Confidence.Low, Source.Inferred),
            materials = Finding("Materials were not identified.", Confidence.Low, Source.Inferred),
            qualityNotes = notes,
            uncertainties = listOf("Subject, setting, and materials need a vision reading."),
        )
    }

    private fun localQuality(measured: Measured): List<Finding> {
        val notes = mutableListOf<Finding>()
        val megapixels = (measured.width * measured.height) / 1_000_000f
        if (megapixels < 0.35f) {
            notes += Finding("The source is small, so fine detail is limited.", Confidence.High, Source.Observed)
        }
        val ratio = measured.width.toFloat() / measured.height
        if (ratio > 2.15f || ratio < 0.46f) {
            notes += Finding("The frame is strongly wide or strongly tall.", Confidence.High, Source.Observed)
        }
        notes += Finding(measured.detail, Confidence.Medium, Source.Observed)
        return notes
    }
}
