package tn.loukious.facebookappadsremover.hooks

internal object VisibleVideoSelection {
    fun select(candidates: List<String>, playingId: String?): String? {
        val ids = candidates.filter { it.matches(Regex("[0-9]{5,25}")) }.distinct()
        return ids.singleOrNull() ?: playingId?.takeIf { it in ids }
    }
}
