package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.withJsonDsl
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

class RankFeature(val field: String, block: (RankFeature.() -> Unit)? = null) : ESQuery("rank_feature") {
    constructor(field: KProperty<*>, block: (RankFeature.() -> Unit)? = null) : this(field.name, block)

    var boost by property<Double>()

    init {
        this["field"] = field
        block?.invoke(this)
    }

    /**
     * Configure the saturation function with an optional [pivot]. If omitted,
     * it uses the mean value of the feature that you are ranking on.
     */
    fun saturation(pivot: Double? = null) {
        this["saturation"] = withJsonDsl {
            if (pivot != null) {
                this["pivot"] = pivot
            }
        }
    }

    /**
     * Configure the logarithmic function.
     */
    fun log(scalingFactor: Double) {
        this["log"] = withJsonDsl {
            this["scaling_factor"] = scalingFactor
        }
    }

    /**
     * Variant of [saturation] with a configurable [exponent].
     * The [exponent] should generally be between 0.5 and 1.0
     * and is typically calculated via some training method.
     * Use saturation if you are unable to do this.
     */
    fun sigmoid(pivot: Double, exponent: Double) {
        this["sigmoid"] = withJsonDsl {
            this["pivot"] = pivot
            this["exponent"] = exponent
        }
    }

    /**
     * Configure linear function.
     */
    fun linear() {
        this["linear"] = withJsonDsl { }
    }
}

fun SearchDSL.rankFeature(field: String, block: (RankFeature.() -> Unit)? = null) = RankFeature(field, block)
fun SearchDSL.rankFeature(field: KProperty<*>, block: (RankFeature.() -> Unit)? = null) = RankFeature(field, block)
