package com.jillesvangurp.ktsearch.cli.output

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object JsonTableAdapter {
    fun fromJson(
        rawJson: String,
        preferredColumns: List<String>? = null,
    ): TableData? {
        val element = try {
            Json.Default.decodeFromString(JsonElement.serializer(), rawJson)
        } catch (_: Exception) {
            return null
        }
        if (element !is JsonArray) {
            return null
        }
        if (element.any { it !is JsonObject }) {
            return null
        }
        val objects = element.map { it as JsonObject }
        val columns = preferredColumns
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: collectColumns(objects)

        val rows = objects.map { obj ->
            columns.map { col -> valueToString(obj[col]) }
        }
        return TableData(columns = columns, rows = rows)
    }

    fun fromJsonObject(rawJson: String): TableData? {
        val element = try {
            Json.Default.decodeFromString(JsonElement.serializer(), rawJson)
        } catch (_: Exception) {
            return null
        }
        if (element !is JsonObject) {
            return null
        }
        val rows = mutableListOf<List<String>>()
        element.keys.sorted().forEach { key ->
            flattenObjectValue(prefix = key, value = element.getValue(key), rows = rows)
        }
        return TableData(
            columns = listOf("field", "value"),
            rows = rows,
        )
    }

    private fun collectColumns(objects: List<JsonObject>): List<String> {
        val columns = linkedSetOf<String>()
        objects.forEach { obj ->
            obj.keys.forEach { key ->
                columns.add(key)
            }
        }
        return columns.toList()
    }

    private fun valueToString(value: JsonElement?): String {
        return when (value) {
            null -> ""
            JsonNull -> ""
            is JsonPrimitive -> {
                if (value.isString) {
                    value.content
                } else {
                    value.toString()
                }
            }
            else -> value.toString()
        }
    }

    private fun flattenObjectValue(
        prefix: String,
        value: JsonElement,
        rows: MutableList<List<String>>,
    ) {
        when (value) {
            is JsonObject -> {
                if (value.isEmpty()) {
                    rows.add(listOf(prefix, "{}"))
                } else {
                    value.keys.sorted().forEach { key ->
                        flattenObjectValue(
                            prefix = "$prefix.$key",
                            value = value.getValue(key),
                            rows = rows,
                        )
                    }
                }
            }
            is JsonArray -> {
                if (value.isEmpty()) {
                    rows.add(listOf(prefix, "[]"))
                } else {
                    value.forEachIndexed { index, element ->
                        flattenObjectValue(
                            prefix = "$prefix[$index]",
                            value = element,
                            rows = rows,
                        )
                    }
                }
            }
            else -> rows.add(listOf(prefix, valueToString(value)))
        }
    }
}
