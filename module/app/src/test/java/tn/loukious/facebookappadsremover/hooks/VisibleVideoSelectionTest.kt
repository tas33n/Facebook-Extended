package tn.loukious.facebookappadsremover.hooks

import org.junit.Assert.*
import org.junit.Test

class VisibleVideoSelectionTest {
    @Test fun stalePlayingIdCannotOverrideNewVisibleReel() {
        assertEquals("222222", VisibleVideoSelection.select(listOf("222222"), "111111"))
        assertNull(VisibleVideoSelection.select(emptyList(), "111111"))
    }
    @Test fun ambiguityOnlyUsesPlayingIdIfItIsCurrentlyVisible() {
        assertNull(VisibleVideoSelection.select(listOf("111111", "222222"), "333333"))
        assertEquals("222222", VisibleVideoSelection.select(listOf("111111", "222222"), "222222"))
    }
    @Test fun duplicatesAndInvalidParametersDoNotCreateFalseIdentity() {
        assertEquals("111111", VisibleVideoSelection.select(listOf("111111", "111111", "VideoId: null"), null))
        assertNull(VisibleVideoSelection.select(listOf("null", "https://example.org"), null))
    }
}
