package com.iranjan.hotspotscheduler.platform.keyguard

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.platform.accessibility.NodeSelector
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinPadResolver @Inject constructor(
    private val accessibility: AccessibilityRuntime
) {

    suspend fun detectGeometry(): Result<PinPadGeometry> {
        val bounds = mutableMapOf<Int, IntArray>()
        var foundCount = 0

        for (digit in 0..9) {
            val candidates = KeyguardIds.digitCandidates(digit)
            for (candidate in candidates) {
                val selector = NodeSelector(resourceIds = listOf(candidate))
                val result = accessibility.findNodes(selector)
                if (result is Result.Success) {
                    val node = result.value.firstOrNull { it.isVisibleToUser || it.isEnabled }
                    if (node != null) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (!rect.isEmpty) {
                            bounds[digit] = intArrayOf(rect.left, rect.top, rect.right, rect.bottom)
                            foundCount++
                            break
                        }
                    }
                }
                delay(10)
            }
        }

        if (foundCount < 10) {
            return Result.failure(Result.Error.NotAvailable("only $foundCount/10 digit keys found"))
        }

        val validation = KeypadGeometryValidator.build(
            bounds = bounds,
            displayWidth = getDisplayWidth(),
            displayHeight = getDisplayHeight()
        )

        return when (validation) {
            is KeypadGeometryValidator.Result.Valid -> Result.success(
                PinPadGeometry(
                    digitCentres = validation.geometry.digitCentres,
                    enterCentre = findEnterCentre(),
                    sourcePackage = validation.geometry.sourcePackage,
                    validated = true
                )
            )
            is KeypadGeometryValidator.Result.Invalid -> {
                Log.w("PinPadResolver", "keypad geometry rejected: ${validation.reason}")
                Result.failure(Result.Error.NotAvailable("keypad geometry invalid: ${validation.reason}"))
            }
        }
    }

    private fun findEnterCentre(): Pair<Float, Float>? {
        for (candidate in KeyguardIds.enterCandidates()) {
            val selector = NodeSelector(resourceIds = listOf(candidate))
            val result = accessibility.findNodes(selector)
            if (result is Result.Success) {
                val node = result.value.firstOrNull { it.isVisibleToUser || it.isEnabled }
                node?.let {
                    val rect = Rect()
                    it.getBoundsInScreen(rect)
                    if (!rect.isEmpty) {
                        val dw = getDisplayWidth().toFloat()
                        val dh = getDisplayHeight().toFloat()
                        return Pair(rect.centerX() / dw, rect.centerY() / dh)
                    }
                }
            }
        }
        return null
    }

    private fun getDisplayWidth(): Int =
        try {
            android.app.Activity().resources.displayMetrics.widthPixels
        } catch (e: Exception) { 1080 }

    private fun getDisplayHeight(): Int =
        try {
            android.app.Activity().resources.displayMetrics.heightPixels
        } catch (e: Exception) { 2400 }
}

object KeypadGeometryValidator {

    private const val MIN_KEY_FRACTION = 0.02f
    private const val MAX_KEY_FRACTION = 0.45f
    private const val MIN_ALLOWED_CENTRE_Y = 0.25f

    sealed interface Result {
        data class Valid(val geometry: KeypadGeometry) : Result
        data class Invalid(val reason: String, val foundDigits: List<Int>) : Result
    }

    data class KeypadGeometry(
        val digitCentres: Map<Int, Pair<Float, Float>>,
        val sourcePackage: String? = null
    ) {
        val isUsable: Boolean get() = digitCentres.size == 10
        fun centreOf(digit: Int): Pair<Float, Float>? = digitCentres[digit]
    }

    fun build(
        bounds: Map<Int, IntArray>,
        displayWidth: Int,
        displayHeight: Int,
        sourcePackage: String? = null
    ): Result {
        val found = bounds.keys.filter { it in 0..9 }.sorted()
        if (displayWidth <= 0 || displayHeight <= 0) {
            return Result.Invalid("display size unknown (${displayWidth}x$displayHeight)", found)
        }
        if (found.size < 10) {
            return Result.Invalid("only ${found.size}/10 digit keys found: ${found.joinToString()}", found)
        }

        val centres = found.associateWith { digit ->
            val b = bounds.getValue(digit)
            if (b.size < 4) return Result.Invalid("key $digit malformed bounds", found)
            val cx = ((b[0] + b[2]) / 2f) / displayWidth
            val cy = ((b[1] + b[3]) / 2f) / displayHeight
            val w = (b[2] - b[0]).toFloat() / displayWidth
            val h = (b[3] - b[1]).toFloat() / displayHeight
            Triple(cx, cy, minOf(w, h))
        }

        for ((digit, triple) in centres) {
            val (cx, cy, size) = triple
            if (size < MIN_KEY_FRACTION) return Result.Invalid("key $digit too small ($size)", found)
            if (size > MAX_KEY_FRACTION) return Result.Invalid("key $digit too large ($size)", found)
            if (cx < 0f || cx > 1f || cy < MIN_ALLOWED_CENTRE_Y || cy > 1f) return Result.Invalid("key $digit outside region (y=$cy)", found)
        }

        val xs = found.map { centres.getValue(it).first }.sorted()
        val columns = cluster(xs, 0.12f)
        if (columns.size != 3) return Result.Invalid("expected 3 columns, found ${columns.size}", found)

        val ys = found.map { centres.getValue(it).second }.sorted()
        val rows = cluster(ys, 0.06f)
        if (rows.size != 4) return Result.Invalid("expected 4 rows, found ${rows.size}", found)

        val one = centres.getValue(1); val two = centres.getValue(2); val four = centres.getValue(4)
        if (!(one.first < two.first)) return Result.Invalid("keys 1,2 not left-to-right", found)
        if (!(one.second < four.second)) return Result.Invalid("keys 1,4 not top-to-bottom", found)
        val zero = centres.getValue(0); val eight = centres.getValue(8)
        if (zero.second <= eight.second) return Result.Invalid("key 0 not below key 8", found)

        for (a in found) for (b in found) if (a < b) {
            val ba = bounds.getValue(a); val bb = bounds.getValue(b)
            if (ba[0] < bb[2] && bb[0] < ba[2] && ba[1] < bb[3] && bb[1] < ba[3]) {
                return Result.Invalid("keys $a and $b overlap", found)
            }
        }

        return Result.Valid(KeypadGeometry(
            digitCentres = found.associateWith { centres.getValue(it).first to centres.getValue(it).second },
            sourcePackage = sourcePackage
        ))
    }

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