package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.camelCase2SnakeCase
import kotlinx.serialization.Serializable

enum class OperationType {
    Create,Index
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
data class Shards(val total: Int, val successful: Int, val failed: Int)
