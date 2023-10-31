package com.jillesvangurp.searchdsls.querydsl

import kotlin.reflect.KProperty

class DistanceFeature() : ESQuery("distance_feature") {
    var field by property<String>()
    var pivot by property<String>()
    var origin by property<Any>()
    var boost by property<Double>()
}

fun SearchDSL.distanceFeature(
    field: KProperty<*>,
    pivot: String,
    origin: String,
    block: (DistanceFeature.() -> Unit)? = null
) = DistanceFeature().let {
    it.field = field.name
    it.pivot = pivot
    it.origin = origin
    block?.invoke(it)
    it
}

fun SearchDSL.distanceFeature(
    field: String,
    pivot: String,
    origin: String,
    block: (DistanceFeature.() -> Unit)? = null
) = DistanceFeature().let {
    it.field = field
    it.pivot = pivot
    it.origin = origin
    block?.invoke(it)
    it
}

fun SearchDSL.distanceFeature(
    field: KProperty<*>,
    pivot: String,
    origin: List<Double>,
    block: (DistanceFeature.() -> Unit)? = null
) = DistanceFeature().let {
    it.field = field.name
    it.pivot = pivot
    it.origin = origin
    block?.invoke(it)
    it
}

fun SearchDSL.distanceFeature(
    field: String,
    pivot: String,
    origin: List<Double>,
    block: (DistanceFeature.() -> Unit)? = null
) = DistanceFeature().let {
    it.field = field
    it.pivot = pivot
    it.origin = origin
    block?.invoke(it)
    it
}

fun SearchDSL.distanceFeature(
    field: KProperty<*>,
    pivot: String,
    origin: Array<Double>,
    block: (DistanceFeature.() -> Unit)? = null
) = DistanceFeature().let {
    it.field = field.name
    it.pivot = pivot
    it.origin = origin
    block?.invoke(it)
    it
}

fun SearchDSL.distanceFeature(
    field: String,
    pivot: String,
    origin: Array<Double>,
    block: (DistanceFeature.() -> Unit)? = null
) = DistanceFeature().let {
    it.field = field
    it.pivot = pivot
    it.origin = origin
    block?.invoke(it)
    it
}

fun SearchDSL.distanceFeature(
    field: KProperty<*>,
    pivot: String,
    origin: DoubleArray,
    block: (DistanceFeature.() -> Unit)? = null
) = DistanceFeature().let {
    it.field = field.name
    it.pivot = pivot
    it.origin = origin
    block?.invoke(it)
    it
}

fun SearchDSL.distanceFeature(
    field: String,
    pivot: String,
    origin: DoubleArray,
    block: (DistanceFeature.() -> Unit)? = null
) = DistanceFeature().let {
    it.field = field
    it.pivot = pivot
    it.origin = origin
    block?.invoke(it)
    it
}

