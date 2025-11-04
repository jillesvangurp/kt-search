package com.jillesvangurp.ktsearch.alert.testutil

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

expect fun coRun(timeout: Duration = 30.seconds, block: suspend () -> Unit)
