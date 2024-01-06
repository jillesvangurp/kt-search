package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class CommonTestKt {

    @Test
    fun nullCase() {
        val duration: Duration? = null
        duration.toElasticsearchTimeUnit() shouldBe null
    }

    @Test
    fun daysCase() {
        2.days.toElasticsearchTimeUnit() shouldBe "2d"
    }

    @Test
    fun hoursCase() {
        26.hours.toElasticsearchTimeUnit() shouldBe "26h"
    }

    @Test
    fun minutesCase() {
        65.minutes.toElasticsearchTimeUnit() shouldBe "65m"
    }

    @Test
    fun secondsCase() {
        65.seconds.toElasticsearchTimeUnit() shouldBe "65s"
    }
    @Test
    fun millisecondsCase() {
        7887.milliseconds.toElasticsearchTimeUnit() shouldBe "7887000000nanos"
    }

    @Test
    fun microsecondsCase() {
        1234.microseconds.toElasticsearchTimeUnit() shouldBe "1234000nanos"
    }

    @Test
    fun nanosecondsCase() {
        54.nanoseconds.toElasticsearchTimeUnit() shouldBe "54nanos"
    }

    @Test
    fun infiniteCase() {
        INFINITE.toElasticsearchTimeUnit() shouldBe null
    }

    @Test
    fun negativeCase() {
        (-10).seconds.toElasticsearchTimeUnit() shouldBe null
    }

    @Test
    fun zeroCase() {
        0.seconds.toElasticsearchTimeUnit() shouldBe null
    }
}