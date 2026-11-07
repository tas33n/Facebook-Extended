package tn.loukious.facebookappadsremover.ui

internal object PreviewPanBounds {
    fun correction(start: Float, end: Float, viewport: Float): Float = when {
        end - start <= viewport -> viewport / 2 - (start + end) / 2
        start > 0 -> -start
        end < viewport -> viewport - end
        else -> 0f
    }
}
