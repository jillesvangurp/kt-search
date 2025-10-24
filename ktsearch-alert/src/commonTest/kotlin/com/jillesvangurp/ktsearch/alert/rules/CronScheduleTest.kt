package com.jillesvangurp.ktsearch.alert.rules

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test

class CronScheduleTest {
    @Test
    fun `should compute next minute for wildcard cron`() {
        val cron = CronSchedule.parse("* * * * *")
        val reference = LocalDateTime(2024, 1, 1, 12, 0, 30).toInstant(TimeZone.UTC)
        val next = cron.nextAfter(reference)
        val expected = LocalDateTime(2024, 1, 1, 12, 1, 0).toInstant(TimeZone.UTC)
        next shouldBe expected
    }

    @Test
    fun `should honor step expressions`() {
        val cron = CronSchedule.parse("*/15 * * * *")
        val reference = LocalDateTime(2024, 1, 1, 12, 7, 0).toInstant(TimeZone.UTC)
        val next = cron.nextAfter(reference)
        val expected = LocalDateTime(2024, 1, 1, 12, 15, 0).toInstant(TimeZone.UTC)
        next shouldBe expected
    }

    @Test
    fun `should respect day of week restrictions`() {
        val cron = CronSchedule.parse("0 9 * * 1-5")
        val friday = LocalDateTime(2024, 1, 5, 8, 59, 59).toInstant(TimeZone.UTC)
        val monday = LocalDateTime(2024, 1, 8, 9, 0, 0).toInstant(TimeZone.UTC)
        val saturday = LocalDateTime(2024, 1, 6, 12, 0, 0).toInstant(TimeZone.UTC)

        cron.nextAfter(friday) shouldBe LocalDateTime(2024, 1, 5, 9, 0, 0).toInstant(TimeZone.UTC)
        cron.nextAfter(saturday) shouldBe monday
    }

    @Test
    fun `should parse range and step combinations`() {
        val cron = CronSchedule.parse("10-20/5 0 1 * *")
        val reference = LocalDateTime(2024, 5, 1, 0, 9, 0).toInstant(TimeZone.UTC)

        val next = cron.nextAfter(reference)
        next shouldBe LocalDateTime(2024, 5, 1, 0, 10, 0).toInstant(TimeZone.UTC)

        val second = cron.nextAfter(next)
        second shouldBe LocalDateTime(2024, 5, 1, 0, 15, 0).toInstant(TimeZone.UTC)
    }
}
