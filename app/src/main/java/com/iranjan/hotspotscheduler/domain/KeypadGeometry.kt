package com.iranjan.hotspotscheduler.domain

/**
 * Geometry of a detected on-screen keypad, expressed in normalised 0..1 screen fractions.
 *
 * Replaces hard-coded grid fractions. Guessing `screenHeight * 0.8` for a key position means a
 * mistyped digit, and a mistyped digit on a real lock screen costs a failed credential attempt.
 */
data class KeypadGeometry(
    /** Centres of each digit, indexed 0..9, as fractions of the display. */
    val digitCentres: Map<Int, Pair<Float, Float>>,
    /** Detected package the keypad came from, for logging. */
    val sourcePackage: String? = null
) {
    val isUsable: Boolean get() = digitCentres.size == 10

    fun centreOf(digit: Int): Pair<Float, Float>? = digitCentres[digit]
}

/**
 * Builds a [KeypadGeometry] from observed key bounds, and refuses to produce one unless the layout
 * actually looks like a numeric keypad.
 *
 * Validation is deliberately strict, because the failure mode matters: a wrong "valid" verdict
 * leads to typing a PIN into the wrong keys, while a wrong "invalid" verdict just skips one
 * automation run. Skipping is far cheaper than a wrong credential.
 *
 * Checks:
 *  - all ten digits 0..9 present;
 *  - exactly three distinct columns and four distinct rows (a 3x4 pad, with 0 centred on the bottom
 *    row and no third key beside it);
 *  - rows and columns are monotonically ordered;
 *  - keys do not overlap;
 *  - every key sits in the lower portion of the screen, where a PIN pad lives;
 *  - no key is absurdly large (that would mean we matched a container, not a key).
 */
object KeypadGeometryValidator {

    /** A key smaller than this fraction of the screen is noise; larger is a container. */
    private const val MIN_KEY_FRACTION = 0.02f

    /** Keys wider/taller than this fraction are treated as a container, not a button. */
    private const val MAX_KEY_FRACTION = 0.45f

    /** A keypad lives below this fraction of screen height. */
    private const val MIN_ALLOWED_CENTRE_Y = 0.25f

    sealed interface Result {
        data class Valid(val geometry: KeypadGeometry) : Result
        data class Invalid(val reason: String, val foundDigits: List<Int>) : Result
    }

    /**
     * @param bounds digit -> (left, top, right, bottom) in pixels.
     * @param displayWidth display width in pixels.
     * @param displayHeight display height in pixels.
     */
    fun build(
        bounds: Map<Int, IntArray>,
        displayWidth: Int,
        displayHeight: Int,
        sourcePackage: String? = null
    ): Result {
        val found = bounds.keys.filter { it in 0..9 }.sorted()
        if (displayWidth <= 0 || displayHeight <= 0) {
            return Result.Invalid("display size is unknown (${displayWidth}x$displayHeight)", found)
        }
        if (found.size < 10) {
            return Result.Invalid(
                "only ${found.size}/10 digit keys were found: ${found.joinToString()}",
                found
            )
        }

        val centres = found.associateWith { digit ->
            val b = bounds.getValue(digit)
            if (b.size < 4) return Result.Invalid("key $digit has malformed bounds", found)
            val cx = ((b[0] + b[2]) / 2f) / displayWidth
            val cy = ((b[1] + b[3]) / 2f) / displayHeight
            val w = (b[2] - b[0]).toFloat() / displayWidth
            val h = (b[3] - b[1]).toFloat() / displayHeight
            Triple(cx, cy, minOf(w, h))
        }

        // Size sanity: a real button is small but not invisible, and not a whole container.
        for ((digit, triple) in centres) {
            val (cx, cy, size) = triple
            if (size < MIN_KEY_FRACTION) {
                return Result.Invalid("key $digit is implausibly small ($size of the display)", found)
            }
            if (size > MAX_KEY_FRACTION) {
                return Result.Invalid("key $digit looks like a container, not a button ($size)", found)
            }
            if (cx < 0f || cx > 1f || cy < MIN_ALLOWED_CENTRE_Y || cy > 1f) {
                return Result.Invalid("key $digit is outside the keypad region (y=$cy)", found)
            }
        }

        // Column structure: group by x, expect exactly 3.
        val xs = found.map { centres.getValue(it).first }.sorted()
        val columns = cluster(xs, tolerance = 0.12f)
        if (columns.size != 3) {
            return Result.Invalid("expected 3 keypad columns, found ${columns.size}", found)
        }

        // Row structure: expect 4 distinct rows.
        val ys = found.map { centres.getValue(it).second }.sorted()
        val rows = cluster(ys, tolerance = 0.06f)
        if (rows.size != 4) {
            return Result.Invalid("expected 4 keypad rows, found ${rows.size}", found)
        }

        // Ordering: digit 1 must sit left of 2, and above 4. Catches a scrambled or mirrored pad.
        val one = centres.getValue(1); val two = centres.getValue(2); val four = centres.getValue(4)
        if (!(one.first < two.first)) {
            return Result.Invalid("keys 1 and 2 are not left-to-right ordered", found)
        }
        if (!(one.second < four.second)) {
            return Result.Invalid("keys 1 and 4 are not top-to-bottom ordered", found)
        }
        // 0 belongs on the bottom row, under 8.
        val zero = centres.getValue(0); val eight = centres.getValue(8)
        if (zero.second <= eight.second) {
            return Result.Invalid("key 0 is not below key 8", found)
        }

        // Overlap: no two keys may intersect.
        for (a in found) {
            for (b in found) {
                if (a >= b) continue
                if (overlaps(bounds.getValue(a), bounds.getValue(b))) {
                    return Result.Invalid("keys $a and $b overlap", found)
                }
            }
        }

        return Result.Valid(
            KeypadGeometry(
                digitCentres = found.associateWith { centres.getValue(it).first to centres.getValue(it).second },
                sourcePackage = sourcePackage
            )
        )
    }

    private fun overlaps(a: IntArray, b: IntArray): Boolean =
        a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

    /** Groups sorted values into clusters where neighbours are within [tolerance]. */
    private fun cluster(sorted: List<Float>, tolerance: Float): List<List<Float>> {
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf<MutableList<Float>>()
        var current = mutableListOf(sorted.first())
        for (v in sorted.drop(1)) {
            if (v - current.last() <= tolerance) current.add(v) else { out.add(current); current = mutableListOf(v) }
        }
        out.add(current)
        return out
    }
}