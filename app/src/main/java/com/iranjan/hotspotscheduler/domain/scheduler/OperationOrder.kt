package com.iranjan.hotspotscheduler.domain.scheduler

import com.iranjan.hotspotscheduler.domain.model.Feature
import com.iranjan.hotspotscheduler.domain.model.Target

object OperationOrder {
    fun plan(hotspotTarget: Target, mobileDataTarget: Target): List<Feature> {
        val turningOn = buildList {
            if (mobileDataTarget == Target.Set(true)) add(Feature.MOBILE_DATA)
            if (hotspotTarget == Target.Set(true)) add(Feature.HOTSPOT)
        }
        if (turningOn.isNotEmpty() && mobileDataTarget != Target.Set(false)) return turningOn

        val turningOff = buildList {
            if (hotspotTarget == Target.Set(false)) add(Feature.HOTSPOT)
            if (mobileDataTarget == Target.Set(false)) add(Feature.MOBILE_DATA)
        }
        if (turningOff.isNotEmpty() && hotspotTarget != Target.Set(true)) return turningOff

        return buildList {
            if (mobileDataTarget != Target.LeaveAlone) add(Feature.MOBILE_DATA)
            if (hotspotTarget != Target.LeaveAlone) add(Feature.HOTSPOT)
        }
    }

    fun describe(hotspotTarget: Target, mobileDataTarget: Target): String =
        plan(hotspotTarget, mobileDataTarget).joinToString(" -> ") { feature ->
            val target = when (feature) {
                Feature.HOTSPOT -> hotspotTarget
                Feature.MOBILE_DATA -> mobileDataTarget
            }
            "${feature.name.lowercase()}=${describeTarget(target)}"
        }.ifEmpty { "nothing to do" }

    private fun describeTarget(target: Target): String = when (target) {
        Target.LeaveAlone -> "leave alone"
        is Target.Set -> target.on.toString()
    }
}