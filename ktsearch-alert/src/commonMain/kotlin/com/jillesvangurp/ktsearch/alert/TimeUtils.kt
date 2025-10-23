package com.jillesvangurp.ktsearch.alert

import kotlin.time.Clock
import kotlin.time.Instant

internal fun currentInstant(): Instant {
    val now = Clock.System.now()
    return Instant.fromEpochSeconds(now.epochSeconds, now.nanosecondsOfSecond)
}
