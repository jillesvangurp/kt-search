package com.jillesvangurp.ktsearch.alert

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Simple cron schedule supporting five fields: minute, hour, day of month, month, and day of week.
 *
 * Supported syntax: `*`, comma separated values, ranges (e.g. `1-5`), step values (e.g. `*\/5` or `1-10/2`).
 */
class CronSchedule private constructor(
    private val minuteField: CronField,
    private val hourField: CronField,
    private val dayOfMonthField: CronField,
    private val monthField: CronField,
    private val dayOfWeekField: CronField
) {
    fun nextAfter(reference: Instant, zone: TimeZone = TimeZone.UTC): Instant {
        var candidate = roundUpToNextMinute(reference)
        val maxIterations = 366 * 24 * 60 // roughly one year of minutes
        repeat(maxIterations) {
            val dateTime = candidate.toLocalDateTime(zone)
            if (matches(dateTime)) {
                return candidate
            }
            candidate += 60.seconds
        }
        throw IllegalStateException("Unable to compute next run for cron expression after $maxIterations iterations")
    }

    private fun matches(dateTime: LocalDateTime): Boolean {
        if (!monthField.contains(dateTime.monthNumber)) return false
        if (!hourField.contains(dateTime.hour)) return false
        if (!minuteField.contains(dateTime.minute)) return false

        val dayOfMonthMatches = dayOfMonthField.contains(dateTime.dayOfMonth)
        val dow = (dateTime.dayOfWeek.isoDayNumber % 7)
        val dayOfWeekMatches = dayOfWeekField.contains(dow)

        return when {
            dayOfMonthField.isWildcard && dayOfWeekField.isWildcard -> true
            dayOfMonthField.isWildcard -> dayOfWeekMatches
            dayOfWeekField.isWildcard -> dayOfMonthMatches
            else -> dayOfMonthMatches || dayOfWeekMatches
        }
    }

    private fun roundUpToNextMinute(reference: Instant): Instant {
        var candidate = reference
        if (candidate.nanosecondsOfSecond != 0) {
            candidate += (1_000_000_000 - candidate.nanosecondsOfSecond).nanoseconds
        }
        val remainder = (candidate.epochSeconds % 60 + 60) % 60
        if (remainder != 0L) {
            candidate += (60 - remainder).seconds
        }
        if (candidate <= reference) {
            candidate += 60.seconds
        }
        return candidate
    }

    companion object {
        fun parse(expression: String): CronSchedule {
            val fields = expression.trim().split(Regex("\\s+"))
            require(fields.size == 5) { "Cron expression must have 5 fields but was ${fields.size}" }
            return CronSchedule(
                minuteField = CronField.parse(fields[0], 0, 59),
                hourField = CronField.parse(fields[1], 0, 23),
                dayOfMonthField = CronField.parse(fields[2], 1, 31),
                monthField = CronField.parse(fields[3], 1, 12),
                dayOfWeekField = CronField.parse(fields[4], 0, 6, translateSevenToZero = true)
            )
        }
    }
}

internal class CronField private constructor(
    private val allowed: IntArray,
    val isWildcard: Boolean
) {
    fun contains(value: Int): Boolean {
        val normalized = if (value == 7) 0 else value
        return allowed.binarySearch(normalized) >= 0
    }

    companion object {
        fun parse(
            field: String,
            min: Int,
            max: Int,
            translateSevenToZero: Boolean = false
        ): CronField {
            val trimmed = field.trim()
            if (trimmed == "*") {
                return CronField((min..max).toList().toIntArray(), true)
            }
            val values = mutableSetOf<Int>()
            trimmed.split(',').forEach { token ->
                parseToken(token, min, max, translateSevenToZero, values)
            }
            val sorted = values.map { value -> if (translateSevenToZero && value == 7) 0 else value }
                .map { it.coerceIn(min, max) }
                .toSet()
                .sorted()
            require(sorted.isNotEmpty()) { "Cron field '$field' produced no values" }
            return CronField(sorted.toIntArray(), false)
        }

        private fun parseToken(
            token: String,
            min: Int,
            max: Int,
            translateSevenToZero: Boolean,
            values: MutableSet<Int>
        ) {
            val stepParts = token.split('/')
            val rangePart = stepParts[0]
            val step = if (stepParts.size == 2) stepParts[1].toInt() else 1
            require(step > 0) { "Step must be positive in cron token '$token'" }

            val range = if (rangePart == "*") {
                min..max
            } else if (rangePart.contains('-')) {
                val (start, end) = rangePart.split('-', limit = 2)
                val startValue = parseValue(start, min, max, translateSevenToZero)
                val endValue = parseValue(end, min, max, translateSevenToZero)
                require(endValue >= startValue) { "Invalid range $rangePart in cron expression" }
                startValue..endValue
            } else {
                val value = parseValue(rangePart, min, max, translateSevenToZero)
                value..value
            }

            var current = range.first
            while (current <= range.last) {
                values += current
                current += step
            }
        }

        private fun parseValue(
            value: String,
            min: Int,
            max: Int,
            translateSevenToZero: Boolean
        ): Int {
            val parsed = value.toInt()
            return when {
                translateSevenToZero && parsed == 7 -> 0
                parsed < min -> min
                parsed > max -> max
                else -> parsed
            }
        }
    }
}
