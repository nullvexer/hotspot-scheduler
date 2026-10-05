package com.iranjan.hotspotscheduler.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An internet-sharing hotspot needs an upstream. Turning the hotspot on before mobile data leaves
 * a network with no internet, which on a Galaxy A22 frequently means the hotspot fails to start at
 * all. Turning things off must go the other way.
 */
class OperationOrderTest {

    private val data = OperationOrder.Feature.MOBILE_DATA
    private val hotspot = OperationOrder.Feature.HOTSPOT

    @Test
    fun `turning both on puts mobile data first`() {
        assertEquals(listOf(data, hotspot), OperationOrder.plan(hotspot = true, mobileData = true))
    }

    @Test
    fun `turning both off puts hotspot first`() {
        assertEquals(listOf(hotspot, data), OperationOrder.plan(hotspot = false, mobileData = false))
    }

    @Test
    fun `hotspot only does not touch mobile data`() {
        assertEquals(listOf(hotspot), OperationOrder.plan(hotspot = true, mobileData = null))
    }

    @Test
    fun `mobile data only does not touch the hotspot`() {
        assertEquals(listOf(data), OperationOrder.plan(hotspot = null, mobileData = true))
    }

    @Test
    fun `mixed targets still respect dependency order`() {
        // data OFF + hotspot ON: data off is a teardown, hotspot on needs upstream, so data first.
        val plan = OperationOrder.plan(hotspot = true, mobileData = false)
        assertEquals(listOf(data, hotspot), plan)
    }

    @Test
    fun `nothing requested yields no operations`() {
        assertTrue(OperationOrder.plan(hotspot = null, mobileData = null).isEmpty())
    }

    @Test
    fun `never requests an operation that was not asked for`() {
        val plans = listOf(
            OperationOrder.plan(hotspot = true, mobileData = null),
            OperationOrder.plan(hotspot = null, mobileData = false),
            OperationOrder.plan(hotspot = false, mobileData = null),
            OperationOrder.plan(hotspot = null, mobileData = true)
        )
        for (plan in plans) {
            assertEquals(1, plan.size)
        }
    }

    @Test
    fun `hotspot on always comes after data on`() {
        val plan = OperationOrder.plan(hotspot = true, mobileData = true)
        assertTrue(plan.indexOf(data) < plan.indexOf(hotspot))
    }

    @Test
    fun `hotspot off always comes before data off`() {
        val plan = OperationOrder.plan(hotspot = false, mobileData = false)
        assertTrue(plan.indexOf(hotspot) < plan.indexOf(data))
    }

    @Test
    fun `every planned feature appears at most once`() {
        val plan = OperationOrder.plan(hotspot = true, mobileData = true)
        assertEquals(plan.size, plan.toSet().size)
    }

    @Test
    fun `describe is human readable`() {
        val text = OperationOrder.describe(hotspot = true, mobileData = true)
        assertTrue(text.contains("mobile_data=true"))
        assertTrue(text.contains("hotspot=true"))
        assertTrue(text.indexOf("mobile_data") < text.indexOf("hotspot=true"))
        assertEquals("nothing to do", OperationOrder.describe(null, null))
    }
}