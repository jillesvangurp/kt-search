@file:Suppress("unused")

package com.jillesvangurp.searchdsls.querydsl

import kotlin.reflect.KProperty

class SourceBuilder {
    internal val sourceFilter = mutableMapOf<String, Any>()

    fun includes(vararg fields: KProperty<*>) = includes(*fields.map { it.name }.toTypedArray())
    fun includes(vararg fields: String) = sourceFilter.set("includes", arrayOf(*fields))

    fun excludes(vararg fields: KProperty<*>) = excludes(*fields.map { it.name }.toTypedArray())
    fun excludes(vararg fields: String) = sourceFilter.set("excludes", arrayOf(*fields))
}

fun SearchDSL.filterSource(returnSource: Boolean) {
    this["_source"] = returnSource
}

fun SearchDSL.filterSource(vararg fields: KProperty<*>) {
    this["_source"] = arrayOf(*fields.map { it.name }.toTypedArray())
}

fun SearchDSL.filterSource(vararg fields: String) {
    this["_source"] = arrayOf(*fields)
}

fun SearchDSL.filterSource(block: SourceBuilder.() -> Unit) {
    val builder = SourceBuilder()
    block.invoke(builder)
    this["_source"] = builder.sourceFilter
}
