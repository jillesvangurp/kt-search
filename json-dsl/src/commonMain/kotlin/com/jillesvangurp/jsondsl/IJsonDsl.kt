package com.jillesvangurp.jsondsl

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Base interface for IMapBackedProperties; this allows using interface delegation on classes that
 * can't extend the JsonDsl class
 */
interface IJsonDsl : MutableMap<String, Any> {
    val defaultNamingConvention: PropertyNamingConvention
    fun put(key: String, value: Any, namingConvention: PropertyNamingConvention=defaultNamingConvention)

    fun put(
        key: KProperty<*>,
        value: Any,
        namingConvention: PropertyNamingConvention = PropertyNamingConvention.ConvertToSnakeCase
    )

    /**
     * Property delegate that stores the value in the MapBackedProperties. Use this to create type safe
     * properties.
     */
    fun <T : Any?> property(defaultValue: T?=null): ReadWriteProperty<Any, T>

    /**
     * Property delegate that stores the value in the MapBackedProperties; uses the customPropertyName instead of the
     * kotlin property name. Use this to create type safe properties in case the property name you need overlaps clashes
     * with a kotlin keyword or super class property or method. For example, "size" is also a method on
     * MapBackedProperties and thus cannot be used as a kotlin property name in a Kotlin class implementing Map.
     */
    fun <T : Any?> property(customPropertyName: String, defaultValue: T?=null): ReadWriteProperty<JsonDsl, T>

    /**
     * Helper to manipulate list value objects.
     */
    fun getOrCreateMutableList(key: String): MutableList<Any>
}

/**
 * Helper function to construct a MapBackedProperties with some content.
 */
fun IJsonDsl.dslObject(namingConvention: PropertyNamingConvention = defaultNamingConvention, block: JsonDsl.() -> Unit): JsonDsl {
    val jsonDsl = JsonDsl(namingConvention = namingConvention)
    block.invoke(jsonDsl)
    return jsonDsl
}

