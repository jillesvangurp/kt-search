@file:Suppress("NAME_SHADOWING")

package documentation.manual.search

import com.jillesvangurp.kotlin4example.Kotlin4Example
import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class TestGeoDoc(val id: String, val name: String, val point: List<Double>)

suspend fun createGeoPoints(indexName: String): Map<String, TestGeoDoc> {
    runCatching { client.deleteIndex(target = indexName, ignoreUnavailable = true) }
    // BEGIN GEOPOINTCREATE
    client.createIndex(indexName) {
        mappings {
            keyword(TestGeoDoc::id)
            text(TestGeoDoc::name)
            geoPoint(TestGeoDoc::point)
        }
    }
    val points = listOf(
        TestGeoDoc(
            id = "bar",
            name = "Kommerzpunk",
            point = listOf(13.400544, 52.530286)
        ),
        TestGeoDoc(
            id = "tower",
            name = "Tv Tower",
            point = listOf(13.40942173843226, 52.52082388531597)
        ),
        TestGeoDoc(
            id = "tor",
            name = "Brandenburger Tor",
            point = listOf(13.377622382132417, 52.51632993824314)
        ),
        TestGeoDoc(
            id = "tegel",
            name = "Tegel Airport (Closed)",
            point = listOf(13.292043211510515, 52.55955614073912)
        ),
        TestGeoDoc(
            id = "airport",
            name = "Brandenburg Airport",
            point = listOf(13.517282872748005, 52.367036750575814)
        )
    ).associateBy { it.id }

    client.bulk(target = indexName) {
        // note longitude comes before latitude with geojson
        points.values.forEach { create(it) }
    }
    // END GEOPOINTCREATE
    return points
}

/**
 * Call createGeoPoints separately.
 */
fun Kotlin4Example.createGeoPointsDoc() {
    +"""
        First, let's create an index with some documents with a geospatial information for a TestGeoDoc class
    """.trimIndent()

    example {
        @Serializable
        data class TestGeoDoc(val id: String, val name: String, val point: List<Double>)
    }

    this.exampleFromSnippet("documentation/manual/search/geo-queries.kt", "GEOPOINTCREATE")

}

val geoQueriesMd = sourceGitRepository.md {
    val client = SearchClient(KtorRestClient(Node("localhost", 9999), logging = true))
    
    val indexName = "docs-geo-queries-demo"

    val points = runBlocking {
        createGeoPoints(indexName)
    }

    +"""
        Elasticearch has some nice geo spatial query support that is of course supported in this client.
    """
    createGeoPointsDoc()

    section("Bounding Box Search") {
        +"""
            Bounding box searches return everything inside a bounding box specified by a top left and bottom right point.
        """.trimIndent()

        example {
            client.search(indexName) {
                query = GeoBoundingBoxQuery(TestGeoDoc::point) {
                    topLeft(points["tegel"]!!.point)
                    bottomRight(points["airport"]!!.point)
                }
            }.parseHits(TestGeoDoc.serializer()).map {
                it.id
            }
        }
        +"""
            You can specify points in a variety of ways:
        """.trimIndent()
        example {
            GeoBoundingBoxQuery(TestGeoDoc::point) {
                topLeft(latitude = 52.0, longitude = 13.0)
                // geojson coordinates
                topLeft(arrayOf(13.0, 52.0))
                // use lists or arrays
                topLeft(listOf(13.0, 52.0))
                // wkt notation
                topLeft("Point (52 13")
                // geohash prefix
                topLeft("u33")
            }

        }
    }

    section("Distance Search") {
        +"""
            Searching by distance is also possible.
        """.trimIndent()
        example {
            client.search(indexName) {
                query = GeoDistanceQuery(TestGeoDoc::point, "3km", points["tower"]!!.point)
            }.parseHits(TestGeoDoc.serializer()).map {
                it.id
            }
        }
    }

    section("Shape Search") {
        +"""
            Distance and bounding box searches are of course just syntactic sugar for geo shape queries. Using that,
            you can query by any valid geojson geometry. Shape is of course using `JsonDsl`. You can also
            construct shapes using `withJsonDsl`
              
        """.trimIndent()
        example {
            // you can use the provided Shape sealed class
            // to construct geometries
            val polygon = Shape.Polygon(
                listOf(
                    listOf(
                        points["tegel"]!!.point,
                        points["tor"]!!.point,
                        points["airport"]!!.point,
                        // last point has to be the same as the first
                        points["tegel"]!!.point,
                    )
                )
            )

            client.search(indexName) {
                query = GeoShapeQuery(TestGeoDoc::point) {
                    shape = polygon
                    relation = GeoShapeQuery.Relation.contains
                }
            }

            client.search(indexName) {
                query = GeoShapeQuery(TestGeoDoc::point) {
                    // you can also use string literals for the geometry
                    // this is useful if you have some other representation
                    // of geojson that you can serialize to string
                    shape(
                        """
                        {
                            "type":"Polygon",
                            "coordinates":[[
                                [13.0,53.0],
                                [14.0,53.0],
                                [14.0,52.0],
                                [13.0,52.0],
                                [13.0,53.0]
                            ]]
                        }
                    """.trimIndent()
                    )
                    relation = GeoShapeQuery.Relation.intersects
                }
            }.parseHits(TestGeoDoc.serializer()).map {
                it.id
            }
        }
    }

    section("Grid Search") {
        +"""
            A recent addition to Elasticsearch 8 is grid_search. Using grid search,
            you can search by geohash, Uber h3 hexagon ids, or map tiles. Of course 
            this is translated into another geo_shape query. 
            
        """.trimIndent()
        example {
            client.search(indexName) {
                query = GeoGridQuery(TestGeoDoc::point) {
                    geotile = "6/50/50"
                }
            }
            client.search(indexName) {
                query = GeoGridQuery(TestGeoDoc::point) {
                    geohex = "8a283082a677fff"
                }
            }
            client.search(indexName) {
                query = GeoGridQuery(TestGeoDoc::point) {
                    geohash = "u33d"
                }
            }.parseHits(TestGeoDoc.serializer()).map {
                it.id
            }
        }
    }


    +"""
        Fun fact: I contributed documentation to Elasticsearch 1.x for geojson 
        based searches back in 2013. The POI, Wind und Wetter used here refers 
        to a coffee bar / vegan restaurant / and cocktail bar that has been
        renamed and changed owners several times since then. Back in the day
        when I was building Localstre.am, my former startup, I was using that as 
        my office while writing that documentation. Somehow, the modern day Elastic 
        documentation still uses this point as the example for geo_shape queries. ;-)
    """.trimIndent()
}