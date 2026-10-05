package com.iranjan.hotspotscheduler.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A numeric keypad has a recognisable shape. Validating it before deriving tap positions matters
 * more than it looks: a mistyped digit on a real lock screen costs a credential attempt, and one
 * failure triggers a five-minute backoff. Skipping a run is far cheaper than a wrong PIN.
 */
class KeypadGeometryValidatorTest {

    private val w = 1080
    private val h = 2400

    /** A realistic 3x4 pad on the lower half of the screen. */
    private fun perfectPad(): MutableMap<Int, IntArray> {
        val keyW = 240
        val keyH = 180
        val gapX = 90
        val gapY = 60
        val startX = 90
        val startY = 1500
        val out = LinkedHashMap<Int, IntArray>()
        val layout = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9)
        )
        for ((rowIndex, row) in layout.withIndex()) {
            for ((colIndex, digit) in row.withIndex()) {
                val left = startX + colIndex * (keyW + gapX)
                val top = startY + rowIndex * (keyH + gapY)
                out[digit] = intArrayOf(left, top, left + keyW, top + keyH)
            }
        }
        // 0 sits centred on the bottom row.
        val zeroLeft = startX + (keyW + gapX)
        val zeroTop = startY + 3 * (keyH + gapY)
        out[0] = intArrayOf(zeroLeft, zeroTop, zeroLeft + keyW, zeroTop + keyH)
        return out
    }

    private fun build(bounds: Map<Int, IntArray>) =
        KeypadGeometryValidator.build(bounds, w, h, "com.android.keyguard")

    @Test
    fun `a realistic pad validates`() {
        val r = build(perfectPad())
        assertTrue("expected valid but got $r", r is KeypadGeometryValidator.Result.Valid)
        val geometry = (r as KeypadGeometryValidator.Result.Valid).geometry
        assertTrue(geometry.isUsable)
        assertEquals(10, geometry.digitCentres.size)
        assertEquals("com.android.keyguard", geometry.sourcePackage)
    }

    @Test
    fun `centres are normalised into the screen`() {
        val geometry = (build(perfectPad()) as KeypadGeometryValidator.Result.Valid).geometry
        for ((digit, point) in geometry.digitCentres) {
            val x = point.first
            val y = point.second
            assertTrue("digit $digit x=$x", x in 0f..1f)
            assertTrue("digit $digit y=$y", y in 0f..1f)
        }
        // Key 1 is upper-left, key 9 is lower-right.
        val (x1, y1) = geometry.centreOf(1)!!
        val (x9, y9) = geometry.centreOf(9)!!
        assertTrue(x1 < x9)
        assertTrue(y1 < y9)
    }

    @Test
    fun `missing digits are rejected`() {
        val bounds = perfectPad()
        bounds.remove(7)
        val r = build(bounds)
        assertTrue(r is KeypadGeometryValidator.Result.Invalid)
        assertTrue((r as KeypadGeometryValidator.Result.Invalid).reason.contains("9/10"))
    }

    @Test
    fun `a two column layout is rejected`() {
        val bounds = LinkedHashMap<Int, IntArray>()
        for (d in 0..9) bounds[d] = intArrayOf(d * 50, 1600 + d * 100, d * 50 + 40, 1700 + d * 100)
        val r = build(bounds)
        assertTrue("expected rejection: $r", r is KeypadGeometryValidator.Result.Invalid)
    }

    @Test
    fun `keys in the top half of the screen are rejected`() {
        val bounds = LinkedHashMap<Int, IntArray>()
        val startY = 100
        for (d in 0..9) {
            val col = d % 3
            val row = d / 3
            val left = 100 + col * 300
            val top = startY + row * 60
            bounds[d] = intArrayOf(left, top, left + 200, top + 50)
        }
        val r = build(bounds)
        assertTrue("keys near the top must be refused: $r", r is KeypadGeometryValidator.Result.Invalid)
        assertTrue((r as KeypadGeometryValidator.Result.Invalid).reason.contains("outside"))
    }

    @Test
    fun `an absurdly large key is treated as a container`() {
        val bounds = perfectPad()
        bounds[5] = intArrayOf(0, 1200, w, h)
        val r = build(bounds)
        assertTrue(r is KeypadGeometryValidator.Result.Invalid)
        assertTrue((r as KeypadGeometryValidator.Result.Invalid).reason.contains("container"))
    }

    @Test
    fun `an implausibly tiny key is rejected`() {
        val bounds = perfectPad()
        val original = bounds.getValue(4)
        bounds[4] = intArrayOf(original[0], original[1], original[0] + 4, original[1] + 4)
        val r = build(bounds)
        assertTrue(r is KeypadGeometryValidator.Result.Invalid)
        assertTrue((r as KeypadGeometryValidator.Result.Invalid).reason.contains("small"))
    }

    @Test
    fun `overlapping keys are rejected`() {
        val bounds = perfectPad()
        val five = bounds.getValue(5)
        bounds[6] = intArrayOf(five[0], five[1], five[2], five[3])
        val r = build(bounds)
        assertTrue(r is KeypadGeometryValidator.Result.Invalid)
        assertTrue((r as KeypadGeometryValidator.Result.Invalid).reason.contains("overlap"))
    }

    @Test
    fun `unknown display size is rejected rather than guessed`() {
        val r = KeypadGeometryValidator.build(perfectPad(), 0, 0, null)
        assertTrue(r is KeypadGeometryValidator.Result.Invalid)
        assertTrue((r as KeypadGeometryValidator.Result.Invalid).reason.contains("display size"))
    }

    @Test
    fun `a scrambled layout is rejected`() {
        // 2 to the left of 1: the ordering check must catch a mirrored or scrambled pad.
        val good = perfectPad()
        val one = good.getValue(1)
        val two = good.getValue(2)
        good[1] = two
        good[2] = one
        val r = build(good)
        assertTrue("mirrored pad must be refused: $r", r is KeypadGeometryValidator.Result.Invalid)
    }

    @Test
    fun `zero on the wrong row is rejected`() {
        val good = perfectPad()
        val zero = good.getValue(0)
        val one = good.getValue(1)
        good[0] = intArrayOf(one[0], one[1], one[2], one[3])
        good[1] = zero
        val r = build(good)
        assertTrue(r is KeypadGeometryValidator.Result.Invalid)
    }

    @Test
    fun `geometry exposes no centre for an unknown digit`() {
        val geometry = (build(perfectPad()) as KeypadGeometryValidator.Result.Valid).geometry
        assertEquals(null, geometry.centreOf(42))
    }
}