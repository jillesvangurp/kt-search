@file:Suppress("MemberVisibilityCanBePrivate")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.SearchEngineVariant
import io.ktor.utils.io.core.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject


fun <T> Result<RestResponse>.parse(deserializationStrategy: DeserializationStrategy<T>, json: Json = DEFAULT_JSON): T =
    json.decodeFromString(deserializationStrategy, this.getOrThrow().text)

fun Result<RestResponse>.parseJsonObject() = parse(JsonObject.serializer())
/**
 * Search client that you can use to talk to Elasticsearch or Opensearch.
 *
 * Most client api functions are implemented as extension functions.
 *
 * @param restClient rest client configured to talk to your search engine.
 * Defaults to [KtorRestClient] configured to talk to localhost:9200.
 *
 * @param json kotlinx.serialization Json used to deserialize responses.
 * Defaults to [DEFAULT_JSON] which is configured with some sane defaults.
 */
class SearchClient(val restClient: RestClient=KtorRestClient(), val json: Json = DEFAULT_JSON): Closeable {
    private lateinit var info: SearchEngineInformation

    /**
     * Cheap way to access the version information returned by [root]
     *
     * caches the response in a lateinit var so [root] is called only once
     */
    suspend fun engineInfo(): SearchEngineInformation {
        if(!this::info.isInitialized) {
            info = root()
        }
        return info
    }

    suspend fun validateEngine(message: String, vararg supportedVariants: SearchEngineVariant) {
        val variant = engineInfo().variantInfo.variant
        if(!supportedVariants.contains(variant)) {
            throw UnsupportedOperationException("$variant is not supported; requires one of ${supportedVariants.joinToString(", ")}. $message")
        }
    }

    override fun close() {
        restClient.close()
    }
}
