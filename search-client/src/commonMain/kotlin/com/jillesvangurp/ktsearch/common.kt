@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.camelCase2SnakeCase
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration

enum class OperationType {
    Create, Index, Update, Delete
}

enum class Refresh {
    WaitFor,
    True,
    False
}

enum class VersionType {
    External,
    ExternalGte
}

fun Enum<*>.snakeCase() = this.name.camelCase2SnakeCase()

@Serializable
data class Shards(val total: Int, val successful: Int, val failed: Int, val skipped: Int? = null)

private fun Int.formatTwoChars() = if (this < 10) "0$this" else this
fun formatTimestamp(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    return "${now.year}${now.monthNumber.formatTwoChars()}${now.dayOfMonth.formatTwoChars()}T${now.hour.formatTwoChars()}${now.minute.formatTwoChars()}${now.second.formatTwoChars()}"
}

@Serializable
data class AcknowledgedResponse(val acknowledged: Boolean)

fun Duration?.toElasticsearchTimeUnit(): String? = this?.toComponents { days, hours, minutes, seconds, nanoseconds ->
    when {
        this.isInfinite() -> null
        this.isNegative() -> null
        this.inWholeNanoseconds == 0L -> null
        nanoseconds != 0 -> "${this.inWholeNanoseconds}nanos"
        seconds != 0 -> "${this.inWholeSeconds}s"
        minutes != 0 -> "${this.inWholeMinutes}m"
        hours != 0 -> "${this.inWholeHours}h"
        days != 0L -> "${this.inWholeDays}d"
        else -> error("Unable to convert $this")
    }
}