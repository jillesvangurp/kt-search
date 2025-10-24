package com.jillesvangurp.ktsearch.alert.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.delay

private val retryLogger = KotlinLogging.logger {}

suspend fun <T> retrySuspend(
    description: String,
    maxAttempts: Int,
    initialDelay: Duration,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): T {
    require(maxAttempts > 0) { "maxAttempts must be > 0" }
    var currentDelay = initialDelay
    var attempt = 0
    var lastError: Throwable? = null
    while (attempt < maxAttempts) {
        try {
            return block()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            lastError = t
            attempt += 1
            val remaining = maxAttempts - attempt
            if (!shouldRetry(t) || remaining == 0) {
                throw t
            }
            retryLogger.warn { "$description attempt $attempt failed: ${t.message}. Retrying in ${currentDelay.inWholeSeconds}s" }
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(60.seconds)
        }
    }
    throw lastError ?: IllegalStateException("retrySuspend exhausted without executing block")
}

private operator fun Duration.times(multiplier: Int): Duration =
    (this.inWholeMilliseconds * multiplier).toDuration(DurationUnit.MILLISECONDS)
