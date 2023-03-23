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

class GeoDistanceQuery private constructor(val field: String, distance: String, point: Any): ESQuery("geo_distance") {
    constructor(field: String, distance: String, latitude: Double, longitude: Double) : this(
        field, distance,
        mapOf("lon" to longitude, "lat" to latitude)
    )
    constructor(field: KProperty<*>,  distance: String, point: String) : this(field.name,distance,point)
    constructor(field: KProperty<*>,  distance: String, point: List<Double>) : this(field.name,distance,point)
    constructor(field: KProperty<*>,  distance: String, point: Array<Double>) : this(field.name,distance,point)
    constructor(field: KProperty<*>,  distance: String, latitude: Double, longitude: Double) : this(field.name,distance,
        mapOf("lon" to longitude, "lat" to latitude)
    )

    var distance by property<String>()
    init {
        this[field] = point
        this.distance = distance
    }
}

class GeoGridQueryConfig : JsonDsl() {
    var geohash by property<String>()
    var geotile by property<String>("geotile")
    var geohex by property<String>()

}
class GeoGridQuery(val field: String, block: GeoGridQueryConfig.()-> Unit): ESQuery("geo_grid") {
    constructor(field: KProperty<*>, block: GeoGridQueryConfig.()-> Unit) : this(field.name,block)

    init {
        this[field] = GeoGridQueryConfig().apply(block)
    }

}

// geo polygon -> deprecated, not implementing this


sealed class Shape : JsonDsl() {
    class Envelope(envelope: List<List<Double>>): Shape() {
        init {
            // elasticsearch specific geometry not part of the geojson spec
            // basically a bounding box with top left, bottom right
            this["type"] = "envelope"
            this["coordinates"] = envelope
        }
    }
    class Point(point: List<Double>): Shape() {
        init {
            this["type"] = "point"
            this["coordinates"] = point
        }
    }
    class LineString(points: List<List<Double>>): Shape() {
        init {
            this["type"] = "linestring"
            this["coordinates"] = points
        }
    }
    class MultiLineString(points: List<List<List<Double>>>): Shape() {
        init {
            this["type"] = "multilinestring"
            this["coordinates"] = points
        }
    }
    class Polygon(points: List<List<List<Double>>>): Shape() {
        init {
            this["type"] = "polygon"
            this["coordinates"] = points
        }
    }
    class MultiPolygon(points: List<List<List<List<Double>>>>): Shape() {
        init {
            this["type"] = "multipolygon"
            this["coordinates"] = points
        }
    }
}

class GeoShapeQueryConfig : JsonDsl() {
    class IndexedShape : JsonDsl() {
        var index by property<String>()
        var id by property<String>()
        var path by property<String>()
    }

    var shape by property<JsonDsl>()

    fun indexedShape(block: IndexedShape.()->Unit) {
        this["indexed_shape"] = IndexedShape().apply(block)
    }

    var relation by property<GeoShapeQuery.Relation>(defaultValue = GeoShapeQuery.Relation.intersects)
}
class GeoShapeQuery(val field: String, block: GeoShapeQueryConfig.()-> Unit): ESQuery("geo_shape") {
    @Suppress("EnumEntryName")
    enum class Relation {
        intersects,
        disjoint,
        within,
        contains
    }
    constructor(field: KProperty<*>, block: GeoShapeQueryConfig.()-> Unit) : this(field.name,block)

    init {
        this[field] = GeoShapeQueryConfig().apply(block)
    }

}