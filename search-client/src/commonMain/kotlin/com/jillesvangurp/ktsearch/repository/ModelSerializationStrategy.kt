package com.jillesvangurp.ktsearch.repository

import com.jillesvangurp.serializationext.DEFAULT_JSON
import kotlinx.serialization.json.JsonObject

/**
 * Used by [IndexRepository] to serialize/deserialize models. The default implementation uses kotlinx.serialization.
 *
 * Note, even if you use Jackson, the search client still requires kotlinx.serialization for parsing responses.
 */
interface ModelSerializationStrategy<T:Any> {

    fun serialize(value: T): String

    /**
     * Deserialize a kotlinx.serialization JsonObject.
     *
     * The [value] parameter is a JsonObject because the Elasticsearch/Opensearch response has already
     * been parsed using kotlinx.serialization. Passing the JsonObject avoids converting back and forth
     * between string and JSON, while still allowing frameworks like Jackson to re-parse it if needed.
     *
     * For this, you can use `value.serializedToString` to serialize [value] back or call
     * `Json.encodeToString(value)` directly.
     */
    fun deSerialize(value: JsonObject): T
}

/**
 * Convenience function to get to the serialized String.
 */
val JsonObject.serializedToString get() = DEFAULT_JSON.encodeToString(this)