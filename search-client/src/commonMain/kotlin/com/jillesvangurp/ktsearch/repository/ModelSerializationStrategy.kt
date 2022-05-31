package com.jillesvangurp.ktsearch.repository

import kotlinx.serialization.json.JsonObject

interface ModelSerializationStrategy<T:Any> {
    fun serialize(value: T): String
    fun deSerialize(value: JsonObject): T
}