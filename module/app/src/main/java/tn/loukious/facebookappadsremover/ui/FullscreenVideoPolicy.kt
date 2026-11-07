package tn.loukious.facebookappadsremover.ui

/** Route-independent player detection; a playing feed item alone is insufficient. */
internal object FullscreenVideoPolicy {
    fun isPlayer(labels: List<String>, largeVideoSurface: Boolean, immersiveActivity: Boolean,
                 hasPlayingVideo: Boolean): Boolean {
        val normalized = labels.map { it.trim().lowercase(java.util.Locale.ROOT) }
        val sidebar = normalized.any { it.contains("like button") || it.contains("react") } &&
            normalized.any { it.contains("comment") } && normalized.any { it.startsWith("share") }
        val videoMarker = normalized.any {
            it == "reel details" || it == "video player" || it == "video controls" ||
                it.contains("show video controls") || it.contains("hide video controls")
        }
        val reelsTab = "reels" in normalized && sidebar
        return reelsTab || (largeVideoSurface && videoMarker) ||
            (immersiveActivity && hasPlayingVideo && (sidebar || largeVideoSurface))
    }
}
