package com.jillesvangurp.ktsearch.alert

import kotlin.time.Clock
import kotlinx.datetime.Instant

internal fun currentInstant(): Instant {
    val now = Clock.System.now()
    return Instant.fromEpochSeconds(now.epochSeconds, now.nanosecondsOfSecond)
}
