package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import kotlin.reflect.KProperty


class GeoBoundingBoxQueryConfig : JsonDsl() {
    var wkt by property<String>()

    var ignoreUnmapped by property<Boolean>(customPropertyName = "ignore_unmapped")

    fun topLeft(latitude: Double, longitude: Double) {
        this["top_left"] = mapOf(
            "lat" to latitude,
            "lon" to longitude
        )
    }

    /**
     * GeoJson style point of longitude followed by latitude
     */
    fun topLeft(point: Array<Double>) {
        this["top_left"] = point
    }

    /**
     * Geohash or POINT (lat,Lon)
     */
    fun topLeft(topLeft: String) {
        this["top_left"] = topLeft
    }

    fun bottomRight(latitude: Double, longitude: Double) {
        this["bottom_right"] = mapOf(
            "lat" to latitude,
            "lon" to longitude
        )
    }

    /**
     * GeoJson style point of longitude followed by latitude
     */
    fun bottomRight(point: Array<Double>) {
        this["bottom_right"] = point
    }

    /**
     * Geohash or POINT (lat,Lon)
     */
    fun bottomRight(bottomRight: String) {
        this["bottom_right"] = bottomRight
    }

}
class GeoBoundingBoxQuery(val field: String, block: GeoBoundingBoxQueryConfig.()-> Unit): ESQuery("geo_bounding_box") {
    constructor(field: KProperty<*>, block: GeoBoundingBoxQueryConfig.()-> Unit) : this(field.name,block)

    init {
        this[field] = GeoBoundingBoxQueryConfig().apply(block)
    }

}

// geo distance
// geo grid
// geo polygon
// geo shape