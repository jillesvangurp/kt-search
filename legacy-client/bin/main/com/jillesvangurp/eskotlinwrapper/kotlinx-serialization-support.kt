package com.jillesvangurp.eskotlinwrapper

import com.jillesvangurp.jsondsl.JsonDsl
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.reflect.KProperty

fun JsonDsl.asJsonObject(): JsonObject {
    val map = this
    return buildJsonObject {
        map.entries.forEach { (field, value) ->
            put(field, toJsonElement(value))
        }
    }
}

fun toJsonElement(e: Any?): JsonElement {
    return when (e) {
        e == null -> {
            JsonNull
        }
        is JsonElement -> {
            e
        }
        is Number -> {
            JsonPrimitive(e)
        }
        is String -> {
            JsonPrimitive(e)
        }
        is Boolean -> {
            JsonPrimitive(e)
        }
        is Instant -> {
            toJsonElement(e.toString())
        }
        is Map<*, *> -> {
            buildJsonObject {
                e.entries.forEach { (field, value) ->
                    put(field.toString(), toJsonElement(value))
                }
            }
        }
        is Array<*> -> {
            buildJsonArray {
                e.forEach {
                    add(toJsonElement(it))
                }
            }
        }
        is Iterable<*> -> {
            buildJsonArray {
                e.forEach {
                    add(toJsonElement(it))
                }
            }
        }
        is Enum<*> -> {
            toJsonElement(e.name)
        }
        is KProperty<*> -> {
            toJsonElement(e.name)
        }
        else -> {
            throw IllegalArgumentException("unhandled element type ${e!!::class.simpleName}")
        }
    }
}