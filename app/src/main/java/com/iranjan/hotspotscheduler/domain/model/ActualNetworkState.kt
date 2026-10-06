package com.iranjan.hotspotscheduler.domain.model

data class ActualNetworkState(
    val hotspot: Boolean?,
    val mobileData: Boolean?
) {
    companion object {
        val UNKNOWN = ActualNetworkState(null, null)
    }

    fun stateOf(feature: Feature): Boolean? = when (feature) {
        Feature.HOTSPOT -> hotspot
        Feature.MOBILE_DATA -> mobileData
    }

    fun workRequired(desired: DesiredNetworkState): List<Feature> =
        Feature.entries.filter { feature ->
            when (val target = desired.targetFor(feature)) {
                is Target.LeaveAlone -> false
                is Target.Set -> stateOf(feature) != target.on
            }
        }

    fun satisfies(desired: DesiredNetworkState): Boolean =
        workRequired(desired).isEmpty()
}