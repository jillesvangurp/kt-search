package com.jillesvangurp.jsondsl

interface JsonDslSerializer {
    fun serialize(properties: JsonDsl, pretty: Boolean = false): String
}

/**
 * Used for custom serialization where toString method does not provide the correct value.
 */
interface CustomValue<T> {
    val value: T
}
