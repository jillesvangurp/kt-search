package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.SearchEngineVariant
import com.jillesvangurp.searchdsls.querydsl.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class GeoSpatialQueriesTest : SearchTestBase() {

    @Test
    fun shouldDoBoundingBox() = coRun {
        geoTestFixture { index ->
            client.search(index) {
                query = GeoBoundingBoxQuery(TestDocument::point) {
                    topLeft(arrayOf(12.0, 53.0))
                    bottomRight(arrayOf(14.0, 51.0))
                }
            }.total shouldBe 1

            client.search(index) {
                query = GeoBoundingBoxQuery(TestDocument::point) {
                    topLeft(arrayOf(14.0, 53.0))
                    bottomRight(arrayOf(16.0, 51.0))
                }
            }.total shouldBe 0
        }
    }

    @Test
    fun shouldDoDistanceSearch() = coRun {
        geoTestFixture { index ->
            client.search(index) {
                query = GeoDistanceQuery(TestDocument::point, "1000km", "POINT (13.0 51.0)")
            }.total shouldBe 1
            client.search(index) {
                query = GeoDistanceQuery(TestDocument::point, "1km", "POINT (13.0 51.0)")
            }.total shouldBe 0
        }
    }

    @Test
    fun shouldDoGridQuery() = coRun {
        onlyOn(
            "elasticsearch only feature that was introduced in recent 8.x releases",
            SearchEngineVariant.ES8,
            SearchEngineVariant.ES9,
        ) {
            geoTestFixture { index ->
                client.search(index) {
                    query = GeoGridQuery(TestDocument::point) {
                        geohash = "u33d"
                    }
                }.total shouldBe 1
                client.search(index) {
                    query = GeoGridQuery(TestDocument::point) {
                        geohash = "sr3n"
                    }
                }.total shouldBe 0
            }
        }
    }

    @Test
    fun shouldDoGeoShapeQuery() = coRun {
        geoTestFixture { index ->
            client.search(index) {
                query = GeoShapeQuery(TestDocument::point) {
                    shape = Shape.Envelope(listOf(listOf(12.0, 53.0), listOf(14.0, 51.0)))
                }
            }.total shouldBe 1

            client.search(index) {
                query = GeoShapeQuery(TestDocument::point) {
                    shape = Shape.Envelope(listOf(listOf(2.0, 53.0), listOf(4.0, 51.0)))
                }
            }.total shouldBe 0
        }
    }

    suspend fun geoTestFixture(block: suspend (String)->Unit) {
        testDocumentIndex { index ->

            client.bulk(target = index, refresh = Refresh.WaitFor) {
                // Fun fact, I contributed documentation
                // to Elasticsearch 1.x for geojson based searches back in 2013. The POI
                // used here refers to a coffee bar / vegan restaurant / and cocktail bar
                // that no longer exist that we used as our office while writing that documentation.
                // Somehow, the modern day Elastic documentation still uses this point. ;-)
                create(TestDocument("Wind und Wetter, Berlin, Germany", point = listOf(13.400544, 52.530286)))
            }
            block.invoke(index)
        }
    }
}