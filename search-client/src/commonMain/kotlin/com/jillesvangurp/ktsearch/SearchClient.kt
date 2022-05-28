@file:Suppress("MemberVisibilityCanBePrivate")

package com.jillesvangurp.ktsearch

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


fun <T> Result<RestResponse>.parse(deserializationStrategy: DeserializationStrategy<T>, json: Json = DEFAULT_JSON): T =
    json.decodeFromString(deserializationStrategy, this.getOrThrow().text)


/**
 * Search client. Actual client api functions are implemented as extension functions
 */
class SearchClient(val restClient: RestClient, val json: Json = DEFAULT_JSON) {

}
