package com.iranjan.hotspotscheduler.domain.model

data class DesiredNetworkState(
    val hotspot: Target,
    val mobileData: Target
) {
    companion object {
        fun allOff() = DesiredNetworkState(Target.Set(false), Target.Set(false))
        fun untouched() = DesiredNetworkState(Target.LeaveAlone, Target.LeaveAlone)
    }

    fun targetFor(feature: Feature): Target = when (feature) {
        Feature.HOTSPOT -> hotspot
        Feature.MOBILE_DATA -> mobileData
    }

    fun with(feature: Feature, target: Target): DesiredNetworkState = when (feature) {
        Feature.HOTSPOT -> copy(hotspot = target)
        Feature.MOBILE_DATA -> copy(mobileData = target)
    }

    val isFullyUntouched: Boolean
        get() = hotspot == Target.LeaveAlone && mobileData == Target.LeaveAlone
}