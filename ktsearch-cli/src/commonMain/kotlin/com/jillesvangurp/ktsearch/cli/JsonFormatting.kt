package com.jillesvangurp.ktsearch.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

fun prettyJson(rawJson: String): String {
    return try {
        val element = Json.Default.decodeFromString(
            JsonElement.serializer(),
            rawJson,
        )
        Json { prettyPrint = true }
            .encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
        rawJson
    }
}
