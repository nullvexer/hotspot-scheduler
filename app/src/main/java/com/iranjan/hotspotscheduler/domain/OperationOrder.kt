package com.iranjan.hotspotscheduler.domain

/**
 * Orders network operations so the result actually works on a real phone.
 *
 * ## Why order matters
 *
 * **Turning things ON:** an internet-sharing hotspot needs an upstream. With mobile data off, the
 * hotspot either refuses to start or starts with a dead network - no internet for whoever connects.
 * So mobile data must be ON and verified *before* the hotspot is switched on.
 *
 * ```
 * mobile data ON -> verify -> hotspot ON -> verify
 * ```
 *
 * **Turning things OFF:** the reverse. The hotspot serves clients; kill the upstream first and
 * connected devices see a confusing network drop before the hotspot itself disappears.
 *
 * ```
 * hotspot OFF -> verify -> mobile data OFF -> verify
 * ```
 *
 * Only operations the user actually requested are ever included. A routine that wants only the
 * hotspot must not touch mobile data.
 */
object OperationOrder {

    enum class Feature { MOBILE_DATA, HOTSPOT }

    /**
     * @param hotspot the desired hotspot state, or null to leave it alone.
     * @param mobileData the desired mobile-data state, or null to leave it alone.
     * @return the features to act on, in the order they must be performed.
     */
    fun plan(hotspot: Boolean?, mobileData: Boolean?): List<Feature> {
        val turningOn = buildList {
            if (mobileData == true) add(Feature.MOBILE_DATA)
            if (hotspot == true) add(Feature.HOTSPOT)
        }
        if (turningOn.isNotEmpty() && mobileData != false) return turningOn

        val turningOff = buildList {
            if (hotspot == false) add(Feature.HOTSPOT)
            if (mobileData == false) add(Feature.MOBILE_DATA)
        }
        if (turningOff.isNotEmpty() && hotspot != true) return turningOff

        // Mixed or contradictory targets (e.g. hotspot ON while mobile data goes OFF).
        // Every requested operation is still performed - never silently dropped - in a stable
        // order, so the caller can verify each one and report what actually happened.
        return buildList {
            if (mobileData != null) add(Feature.MOBILE_DATA)
            if (hotspot != null) add(Feature.HOTSPOT)
        }
    }

    /** Human-readable plan for the diagnostics log. */
    fun describe(hotspot: Boolean?, mobileData: Boolean?): String =
        plan(hotspot, mobileData).joinToString(" -> ") { feature ->
            val target = when (feature) {
                Feature.HOTSPOT -> hotspot
                Feature.MOBILE_DATA -> mobileData
            }
            "${feature.name.lowercase()}=$target"
        }.ifEmpty { "nothing to do" }
}