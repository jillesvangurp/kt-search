package com.jillesvangurp.jsondsl

interface JsonDslSerializer {
    fun serialize(properties: JsonDsl, pretty: Boolean = false): String
}

/**
 * Used for serialization of enums with Java name different from serialized value.
 */
interface EnumValue<T> {
    val value: T
}
