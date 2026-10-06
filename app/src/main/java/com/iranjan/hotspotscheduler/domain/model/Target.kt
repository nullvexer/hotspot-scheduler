package com.iranjan.hotspotscheduler.domain.model

sealed interface Target {
    data class Set(val on: Boolean) : Target
    object LeaveAlone : Target
}