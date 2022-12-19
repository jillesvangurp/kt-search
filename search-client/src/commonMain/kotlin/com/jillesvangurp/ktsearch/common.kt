@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.camelCase2SnakeCase
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

enum class OperationType {
    Create,Index,Update,Delete
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
data class Shards(val total: Int, val successful: Int, val failed: Int, val skipped: Int?=null)

enum class SearchEngineVariant { ES7, ES8, OS1, OS2 }

private fun Int.formatTwoChars() = if(this<10) "0$this" else this
fun formatTimestamp(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    return "${now.year}${now.monthNumber.formatTwoChars()}${now.dayOfMonth.formatTwoChars()}T${now.hour.formatTwoChars()}${now.minute.formatTwoChars()}${now.second.formatTwoChars()}"
}

@Serializable
data class AcknowledgedResponse(val acknowledged: Boolean)