@file:Suppress("NAME_SHADOWING")

package documentation.manual.search

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

val geoQueriesMd = sourceGitRepository.md {
    val client = SearchClient(KtorRestClient(Node("localhost", 9999), logging = true))
    @Serializable
    data class TestDoc(val id: String,val name: String, val point: List<Double>)
    val points = listOf(
        TestDoc(
            id = "bar",
            name = "Kommerzpunk",
            point = listOf(13.400544, 52.530286)
        ),
        TestDoc(
            id = "tower",
            name = "Tv Tower",
            point = listOf(13.40942173843226, 52.52082388531597)
        ),
        TestDoc(
            id = "tor",
            name = "Brandenburger Tor",
            point = listOf(13.377622382132417, 52.51632993824314)
        ),
        TestDoc(
            id = "tegel",
            name = "Tegel Airport",
            point = listOf(13.292043211510515, 52.55955614073912)
        ),
        TestDoc(
            id = "airport",
            name = "Brandenburg Airport",
            point = listOf(13.517282872748005, 52.367036750575814)
        )
    ).associateBy { it.id }

    val indexName = "docs-geo-queries-demo"
    runBlocking {

        runCatching { client.deleteIndex(indexName) }
        // BEGIN GEOPOINTCREATE
        @Serializable
        data class TestDoc(val id: String, val name: String, val point: List<Double>)

        client.createIndex(indexName) {
            mappings {
                keyword(TestDoc::id)
                text(TestDoc::name)
                geoPoint(TestDoc::point)
            }
        }
        val points = listOf(
            TestDoc(
                id = "bar",
                name = "Kommerzpunk",
                point = listOf(13.400544, 52.530286)
            ),
            TestDoc(
                id = "tower",
                name = "Tv Tower",
                point = listOf(13.40942173843226, 52.52082388531597)
            ),
            TestDoc(
                id = "tor",
                name = "Brandenburger Tor",
                point = listOf(13.377622382132417, 52.51632993824314)
            ),
            TestDoc(
                id = "tegel",
                name = "Tegel Airport",
                point = listOf(13.292043211510515, 52.55955614073912)
            ),
            TestDoc(
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

    }

    +"""
        Elasticearch has some nice geo spatial query support that is of course supported in this client.
        
        First, let's create an index with some documents with a geospatial information:
    """.trimIndent()

    this.snippetFromSourceFile("documentation/manual/search/geo-queries.kt", "GEOPOINTCREATE")

    section("Bounding Box Search") {
        +"""
            Bounding box searches return everything inside a bounding box specified by a top left and bottom right point.
        """.trimIndent()

        suspendingBlock {
            client.search(indexName) {
                query = GeoBoundingBoxQuery(TestDoc::point) {
                    topLeft(points["tegel"]!!.point)
                    bottomRight(points["airport"]!!.point)
                }
            }.parseHits(TestDoc.serializer()).map {
                it.id
            }
        }
        +"""
            You can specify points in a variety of ways:
        """.trimIndent()
        block {
            GeoBoundingBoxQuery(TestDoc::point) {
                topLeft(latitude = 52.0, longitude = 13.0)
                // geojson coordinates
                topLeft(arrayOf(13.0,52.0))
                // use lists or arrays
                topLeft(listOf(13.0,52.0))
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
        suspendingBlock {
            client.search(indexName) {
                query = GeoDistanceQuery(TestDoc::point, "3km", points["tower"]!!.point)
            }.parseHits(TestDoc.serializer()).map {
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
        suspendingBlock {
            // you can use the provided Shape sealed class
            // to construct geometries
            val polygon = Shape.Polygon(listOf(listOf(
                points["tegel"]!!.point,
                points["tor"]!!.point,
                points["airport"]!!.point,
                // last point has to be the same as the first
                points["tegel"]!!.point,
            )))

            client.search(indexName) {
                query = GeoShapeQuery(TestDoc::point) {
                    shape = polygon
                    relation = GeoShapeQuery.Relation.contains
                }
            }

            client.search(indexName) {
                query = GeoShapeQuery(TestDoc::point) {
                    // you can also use string literals for the geometry
                    // this is useful if you have some other representation
                    // of geojson that you can serialize to string
                    shape("""
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
                    """.trimIndent())
                    relation = GeoShapeQuery.Relation.intersects
                }
            }.parseHits(TestDoc.serializer()).map {
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
        suspendingBlock {
            client.search(indexName) {
                query = GeoGridQuery(TestDoc::point) {
                    geotile = "6/50/50"
                }
            }
            client.search(indexName) {
                query = GeoGridQuery(TestDoc::point) {
                    geohex = "8a283082a677fff"
                }
            }
            client.search(indexName) {
                query = GeoGridQuery(TestDoc::point) {
                    geohash = "u33d"
                }
            }.parseHits(TestDoc.serializer()).map {
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