package tn.loukious.facebookappadsremover.ui

import org.junit.Assert.*
import org.junit.Test

class FullscreenVideoPolicyTest {
    @Test fun suggestionReelNeedsNoReelsTabTitle() {
        assertTrue(FullscreenVideoPolicy.isPlayer(listOf("Reel details", "Tap to show video controls"), true, true, true))
    }
    @Test fun ordinaryFeedPlaybackDoesNotQualify() {
        assertFalse(FullscreenVideoPolicy.isPlayer(listOf("Like", "Comments", "Share"), false, false, true))
    }
    @Test fun fullscreenVideoWithoutSidebarQualifies() {
        assertTrue(FullscreenVideoPolicy.isPlayer(listOf("Video player", "Pause"), true, false, true))
    }
    @Test fun nonVideoImmersivePageDoesNotQualify() {
        assertFalse(FullscreenVideoPolicy.isPlayer(listOf("Photo", "Share"), false, true, false))
    }
    @Test fun smallInlineVideoMarkerIsNotFullscreen() {
        assertFalse(FullscreenVideoPolicy.isPlayer(listOf("Tap to show video controls"), false, false, true))
    }
}
