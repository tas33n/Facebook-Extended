package tn.loukious.facebookappadsremover.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPanBoundsTest {
    @Test fun smallPreviewIsCenteredEvenAfterLargeDrag() {
        assertEquals(50f, PreviewPanBounds.correction(0f, 100f, 200f), 0.001f)
        assertEquals(-950f, PreviewPanBounds.correction(1000f, 1100f, 200f), 0.001f)
    }
    @Test fun oversizedImageNeverLeavesBlankEdgesAcrossDragRange() {
        for (start in -1000..1000 step 13) {
            val a = start.toFloat()
            val correction = PreviewPanBounds.correction(a, a + 400f, 200f)
            assertTrue(a + correction <= 0f)
            assertTrue(a + 400f + correction >= 200f)
        }
    }
    @Test fun validPanStaysPut() {
        assertEquals(0f, PreviewPanBounds.correction(-100f, 300f, 200f), 0.001f)
    }
}
