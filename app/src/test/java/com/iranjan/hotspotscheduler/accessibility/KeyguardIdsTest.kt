package com.iranjan.hotspotscheduler.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keypad resource ids are the part that silently breaks when One UI changes. These tests pin
 * the candidate order and the fallback breadth so a regression is obvious in review.
 */
class KeyguardIdsTest {

    @Test
    fun `every digit resolves candidates`() {
        for (d in 0..9) {
            val candidates = KeyguardIds.digitCandidates(d)
            assertTrue("digit $d had no candidates", candidates.isNotEmpty())
            assertEquals(4, candidates.size)
        }
    }

    @Test
    fun `the AOSP keyguard id is tried first`() {
        val first = KeyguardIds.digitCandidates(5).first()
        assertEquals("com.android.keyguard:id/key5", first)
    }

    @Test
    fun `systemui id is the first fallback because Android 13 builds use it`() {
        val candidates = KeyguardIds.digitCandidates(5)
        assertEquals("com.android.systemui:id/key5", candidates[1])
    }

    @Test
    fun `every candidate names the right digit`() {
        for (d in 0..9) {
            for (c in KeyguardIds.digitCandidates(d)) {
                assertTrue("'$c' does not end with the digit $d", c.endsWith("key$d"))
            }
        }
    }

    @Test
    fun `candidates do not collide between digits`() {
        val all = (0..9).flatMap { KeyguardIds.digitCandidates(it) }
        assertEquals(all.size, all.toSet().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a non digit`() {
        KeyguardIds.digitCandidates(10)
    }

    @Test
    fun `enter candidates cover the known id spellings`() {
        val enter = KeyguardIds.enterCandidates()
        assertTrue(enter.contains("com.android.keyguard:id/key_enter"))
        assertTrue(enter.any { it.endsWith("key_enter") })
    }

    @Test
    fun `bouncer markers detect a visible keypad`() {
        val markers = KeyguardIds.bouncerMarkers()
        assertTrue(markers.contains("com.android.keyguard:id/key1"))
        assertTrue(markers.contains("com.android.systemui:id/key1"))
    }

    @Test
    fun `delete candidates exist for both packages`() {
        val delete = KeyguardIds.deleteCandidates()
        assertTrue(delete.any { it.startsWith("com.android.keyguard") })
        assertTrue(delete.any { it.startsWith("com.android.systemui") })
    }

    @Test
    fun `swipe fraction goes upward`() {
        val (fx, startY, endY) = KeyguardIds.swipeFraction()
        assertEquals(0.5f, fx, 0.001f)
        assertTrue("swipe must move up the screen", endY < startY)
    }

    @Test
    fun `every candidate id is fully qualified`() {
        val all = (0..9).flatMap { KeyguardIds.digitCandidates(it) } +
            KeyguardIds.enterCandidates() +
            KeyguardIds.deleteCandidates() +
            KeyguardIds.bouncerMarkers() +
            KeyguardIds.confirmCandidates()
        for (id in all) {
            assertTrue("'$id' is not package-qualified", id.contains(":id/"))
        }
    }

    @Test
    fun `lockout guard is long enough to protect the phone`() {
        // Five minutes is the minimum sensible backoff: enough that a per-routine retry loop can
        // never reach Android's failed-attempt threshold.
        assertTrue(KeyguardAutomator.LOCKOUT_GUARD_MS >= 5 * 60 * 1000L)
    }

    @Test
    fun `all outcome cases are covered by the sealed hierarchy`() {
        assertNotNull(KeyguardAutomator.Outcome.Unlocked)
        assertNotNull(KeyguardAutomator.Outcome.KeypadNotFound)
        assertNotNull(KeyguardAutomator.Outcome.StillLocked)
        assertNotNull(KeyguardAutomator.Outcome.NotConfigured)
        assertNotNull(KeyguardAutomator.Outcome.Backoff)
    }
}