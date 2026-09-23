package com.dragansim.aureavision.domain

data class Preset(
    val id: PresetId,
    val label: String,
    val blurb: String,
    val lead: String,
    val voice: List<String>,
    val negative: List<String>,
    val stylistic: Boolean,
)

object Presets {
    val all: List<Preset> = listOf(
        Preset(
            PresetId.Photorealistic, "Photoreal", "Lens-true, little garnish",
            "Photorealistic photograph",
            listOf("natural color", "believable materials", "optically plausible depth"),
            listOf("illustration look", "plastic skin", "waxy surfaces", "CGI sheen"),
            stylistic = false,
        ),
        Preset(
            PresetId.Cinematic, "Cinematic", "Motivated light, film still",
            "Cinematic film still",
            listOf("motivated lighting", "gentle falloff", "restrained grain", "widescreen framing"),
            listOf("flat sitcom lighting", "harsh on-camera flash", "oversaturated grade"),
            stylistic = true,
        ),
        Preset(
            PresetId.Editorial, "Editorial", "Controlled, magazine-quiet",
            "Editorial photograph",
            listOf("controlled styling", "precise crop", "print-like color"),
            listOf("snapshot clutter", "crooked horizon", "busy background"),
            stylistic = true,
        ),
        Preset(
            PresetId.Product, "Product", "Clean surface, clear form",
            "Studio product photograph",
            listOf("clean sweep", "controlled highlight", "true material color", "sharp product edges"),
            listOf("cluttered set", "warped geometry", "muddy reflections"),
            stylistic = true,
        ),
        Preset(
            PresetId.Concept, "Concept", "Design-forward, still specific",
            "Concept art",
            listOf("designed shapes", "clear silhouette", "painterly finish held in check"),
            listOf("muddy values", "illegible silhouette", "random detail noise"),
            stylistic = true,
        ),
        Preset(
            PresetId.Minimalist, "Minimal", "Few forms, open space",
            "Minimalist photograph",
            listOf("generous negative space", "few objects", "quiet palette"),
            listOf("clutter", "competing focal points", "decorative excess"),
            stylistic = true,
        ),
        Preset(
            PresetId.Fantasy, "Fantasy", "A style layer, not a fact",
            "Fantasy illustration",
            listOf("mythic atmosphere", "tactile costume materials", "storybook light"),
            listOf("modern logos", "photographic snapshot", "flat vector shapes"),
            stylistic = true,
        ),
        Preset(
            PresetId.Luxury, "Luxury", "Quiet materials, brand still",
            "Luxury brand photograph",
            listOf("quiet luxury", "refined material", "uncluttered set", "soft specular highlights"),
            listOf("cheap plastic", "loud graphics", "cluttered props", "harsh flash"),
            stylistic = true,
        ),
    )

    fun byId(id: PresetId): Preset = all.first { it.id == id }
}
