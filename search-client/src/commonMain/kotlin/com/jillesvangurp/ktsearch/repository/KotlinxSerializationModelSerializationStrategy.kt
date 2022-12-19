package com.jillesvangurp.ktsearch.repository

import com.jillesvangurp.ktsearch.DEFAULT_JSON
import com.jillesvangurp.ktsearch.SearchClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class KotlinxSerializationModelSerializationStrategy<T : Any>(
    private val serializer: KSerializer<T>,
    private val json: Json = DEFAULT_JSON
) : ModelSerializationStrategy<T> {
    override fun serialize(value: T): String {
        return json.encodeToString(serializer, value)
    }

    override fun deSerialize(value: JsonObject): T {
        return json.decodeFromJsonElement(serializer, value)
    }
}

fun <T : Any> SearchClient.ktorModelSerializer(serializer: KSerializer<T>, customJson: Json? = null) =
    KotlinxSerializationModelSerializationStrategy(serializer, customJson ?: json)
