package com.iranjan.hotspotscheduler.domain.scheduler

import com.iranjan.hotspotscheduler.domain.model.Feature
import com.iranjan.hotspotscheduler.domain.model.Target
import org.junit.Assert.*
import org.junit.Test

class OperationOrderTest {

    @Test
    fun `both on: mobile data first then hotspot`() {
        val plan = OperationOrder.plan(Target.Set(true), Target.Set(true))
        assertEquals(listOf(Feature.MOBILE_DATA, Feature.HOTSPOT), plan)
    }

    @Test
    fun `both off: hotspot first then mobile data`() {
        val plan = OperationOrder.plan(Target.Set(false), Target.Set(false))
        assertEquals(listOf(Feature.HOTSPOT, Feature.MOBILE_DATA), plan)
    }

    @Test
    fun `hotspot only on: only hotspot`() {
        val plan = OperationOrder.plan(Target.Set(true), Target.LeaveAlone)
        assertEquals(listOf(Feature.HOTSPOT), plan)
    }

    @Test
    fun `data only on: only mobile data`() {
        val plan = OperationOrder.plan(Target.LeaveAlone, Target.Set(true))
        assertEquals(listOf(Feature.MOBILE_DATA), plan)
    }

    @Test
    fun `hotspot off data untouched: only hotspot`() {
        val plan = OperationOrder.plan(Target.Set(false), Target.LeaveAlone)
        assertEquals(listOf(Feature.HOTSPOT), plan)
    }

    @Test
    fun `data off hotspot untouched: only mobile data`() {
        val plan = OperationOrder.plan(Target.LeaveAlone, Target.Set(false))
        assertEquals(listOf(Feature.MOBILE_DATA), plan)
    }

    @Test
    fun `hotspot on data off: both in stable order`() {
        val plan = OperationOrder.plan(Target.Set(true), Target.Set(false))
        assertEquals(listOf(Feature.MOBILE_DATA, Feature.HOTSPOT), plan)
    }

    @Test
    fun `hotspot off data on: both in stable order`() {
        val plan = OperationOrder.plan(Target.Set(false), Target.Set(true))
        assertEquals(listOf(Feature.HOTSPOT, Feature.MOBILE_DATA), plan)
    }

    @Test
    fun `nothing to do when both untouched`() {
        val plan = OperationOrder.plan(Target.LeaveAlone, Target.LeaveAlone)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `describe formats plan correctly`() {
        val desc = OperationOrder.describe(Target.Set(true), Target.Set(true))
        assertEquals("mobile data=true -> hotspot=true", desc)
    }
}