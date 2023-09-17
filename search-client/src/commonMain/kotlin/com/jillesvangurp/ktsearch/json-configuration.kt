@file:OptIn(ExperimentalSerializationApi::class)

package com.jillesvangurp.ktsearch

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

// fixes the unusable & insane defaults for kotlinx serialization
/**
 * Default kotlinx.serialization `Json` that does all the right things.
 *
 * Used by e.g. the [SearchClient] to deserialize REST responses.
 *
 */
val DEFAULT_JSON: Json = Json {
    // don't rely on external systems being written in kotlin or even having a language with default values
    // the default of false is insane and dangerous
    encodeDefaults = true
    // save space
    prettyPrint = false
    // people adding shit to the json is OK, we're forward compatible and will just ignore it
    isLenient = true
    // encoding nulls is meaningless and a waste of space.
    explicitNulls = false
    // adding enum values is OK even if older clients won't understand it
    ignoreUnknownKeys=true
    // make sure new enum values don't break deserialization (will be null)
    coerceInputValues=true
    // elasticsearch can return NaN values
    allowSpecialFloatingPointValues=true
    // handle NaN and itinity
    allowSpecialFloatingPointValues=true
}

/**
 * Pretty printing kotlinx.serialization `Json` that you may use for debugging.
 */
val DEFAULT_PRETTY_JSON: Json = Json {
    encodeDefaults = true
    prettyPrint = true
    isLenient = true
    explicitNulls = false
    ignoreUnknownKeys=true
    coerceInputValues=true
    allowSpecialFloatingPointValues=true
}

