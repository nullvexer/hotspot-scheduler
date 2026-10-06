package com.iranjan.hotspotscheduler.platform

sealed class Result<out T> {
    @Suppress("UNUSED_PARAMETER")
    data class Success<out T>(val value: T) : Result<T>()
    data class Failure(val error: Error) : Result<Nothing>()

    sealed class Error {
        data class ServiceUnavailable(val detail: String) : Error()
        data class Timeout(val detail: String) : Error()
        data class ActionRejected(val detail: String) : Error()
        data class NotAvailable(val detail: String) : Error()
    }

    companion object {
        fun <T> success(value: T) = Success(value)
        fun <T> failure(error: Error) = Failure(error)
    }

    val isSuccess: Boolean get() = this is Success<*>
    val isFailure: Boolean get() = this is Failure
}