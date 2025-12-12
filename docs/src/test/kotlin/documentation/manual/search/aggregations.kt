@file:Suppress("NAME_SHADOWING", "UNUSED_VARIABLE")

package documentation.manual.search

import com.jillesvangurp.ktsearch.Aggregations
import com.jillesvangurp.ktsearch.BucketAggregationResult
import com.jillesvangurp.ktsearch.TermsAggregationResult
import com.jillesvangurp.ktsearch.TermsBucket
import com.jillesvangurp.ktsearch.bucketScriptResult
import com.jillesvangurp.ktsearch.cardinalityResult
import com.jillesvangurp.ktsearch.compositeResult
import com.jillesvangurp.ktsearch.createIndex
import com.jillesvangurp.ktsearch.dateHistogramResult
import com.jillesvangurp.ktsearch.deleteIndex
import com.jillesvangurp.ktsearch.extendedStatsBucketResult
import com.jillesvangurp.ktsearch.filterResult
import com.jillesvangurp.ktsearch.filtersResult
import com.jillesvangurp.ktsearch.geoCentroid
import com.jillesvangurp.ktsearch.geoTileGridResult
import com.jillesvangurp.ktsearch.getAggResult
import com.jillesvangurp.ktsearch.index
import com.jillesvangurp.ktsearch.maxResult
import com.jillesvangurp.ktsearch.minResult
import com.jillesvangurp.ktsearch.namedBuckets
import com.jillesvangurp.ktsearch.parse
import com.jillesvangurp.ktsearch.parsedBuckets
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.ktsearch.termsResult
import com.jillesvangurp.ktsearch.topHitResult
import com.jillesvangurp.searchdsls.querydsl.BucketScriptAgg
import com.jillesvangurp.searchdsls.querydsl.BucketsPath
import com.jillesvangurp.searchdsls.querydsl.CardinalityAgg
import com.jillesvangurp.searchdsls.querydsl.CompositeAgg
import com.jillesvangurp.searchdsls.querydsl.DateHistogramAgg
import com.jillesvangurp.searchdsls.querydsl.ExtendedStatsBucketAgg
import com.jillesvangurp.searchdsls.querydsl.FilterAgg
import com.jillesvangurp.searchdsls.querydsl.FiltersAgg
import com.jillesvangurp.searchdsls.querydsl.GeoCentroidAgg
import com.jillesvangurp.searchdsls.querydsl.GeoTileGridAgg
import com.jillesvangurp.searchdsls.querydsl.MaxAgg
import com.jillesvangurp.searchdsls.querydsl.MinAgg
import com.jillesvangurp.searchdsls.querydsl.SortOrder
import com.jillesvangurp.searchdsls.querydsl.TermsAgg
import com.jillesvangurp.searchdsls.querydsl.TopHitsAgg
import com.jillesvangurp.searchdsls.querydsl.agg
import com.jillesvangurp.searchdsls.querydsl.term
import com.jillesvangurp.serializationext.DEFAULT_JSON
import com.jillesvangurp.serializationext.DEFAULT_PRETTY_JSON
import documentation.manual.ManualPages
import documentation.manual.search.MyAggNames.BY_COLOR
import documentation.manual.search.MyAggNames.BY_DATE
import documentation.manual.search.MyAggNames.BY_TAG
import documentation.manual.search.MyAggNames.MAX_TIME
import documentation.manual.search.MyAggNames.MIN_TIME
import documentation.manual.search.MyAggNames.SPAN_STATS
import documentation.manual.search.MyAggNames.TAG_CARDINALITY
import documentation.manual.search.MyAggNames.TIME_SPAN
import documentation.manual.search.MyAggNames.TOP_RESULTS
import documentation.printStdOut
import documentation.sourceGitRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

// begin MyAggNamesDef
enum class MyAggNames {
    BY_COLOR,
    BY_TAG,
    BY_DATE,
    MIN_TIME,
    MAX_TIME,
    TIME_SPAN,
    SPAN_STATS,
    TAG_CARDINALITY,
    TOP_RESULTS,
}
// end MyAggNamesDef

val aggregationsMd = sourceGitRepository.md {
    val indexName = "docs-aggs-demo"

    @Serializable
    data class MockDoc(
        val name: String,
        val tags: List<String>? = null,
        val color: String? = null,
        val timestamp: Instant? = null
    )

    val repo = client.repository(indexName, MockDoc.serializer())
    runBlocking {
        try {
            client.deleteIndex(target = indexName, ignoreUnavailable = true)
        } catch (_: Exception) {
        }
    }

    +"""
        The aggregations DSL in Elasticsearch is both very complicated and vast. This is an area where
        using Kotlin can vastly simplify things for programmers.
        
        The search dsl provides several levels of convenience here:
        
        - we can use enum values to name the aggregations and class properties to name fields
        - our dsl support allows us to easily nest aggregations 
        in a more compact way than with json
        - we can pick apart nested bucket aggregation results with extension functions.
        
        First lets create some sample documents to aggregate on:
        
    """.trimIndent()
    example() {
        @Serializable
        data class MockDoc(
            val name: String,
            val tags: List<String>? = null,
            val color: String? = null,
            val timestamp: Instant? = null
        )

        val indexName = "docs-aggs-demo"
        client.createIndex(indexName) {
            mappings {
                text(MockDoc::name)
                keyword(MockDoc::color)
                keyword(MockDoc::tags)
                date(MockDoc::timestamp)
            }
        }

        val repo = client.repository(indexName, MockDoc.serializer())
        repo.bulk {
            val now = Clock.System.now()
            index(
                MockDoc(
                    name = "1",
                    tags = listOf("bar"),
                    color = "green",
                    timestamp = now
                )
            )
            index(
                MockDoc(
                    name = "2",
                    tags = listOf("foo"),
                    color = "red",
                    timestamp = now - 1.days
                )
            )
            index(
                MockDoc(
                    name = "3",
                    tags = listOf("foo", "bar"),
                    color = "red",
                    timestamp = now - 5.days
                )
            )
            index(
                MockDoc(
                    name = "4",
                    tags = listOf("foobar"),
                    color = "green",
                    timestamp = now - 10.days
                )
            )
            index(
                MockDoc(
                    name = "5",
                    tags = listOf("missing"),
                    timestamp = now - 10.days
                )
            )
        }
    }

    section("Picking apart aggregation results") {
        +"""
            Probably the most used aggregation is the `terms` aggregation:
        """.trimIndent()
        example {
            val response = client.search(indexName) {
                // we don't care about the results here
                resultSize = 0

                agg(BY_TAG, TermsAgg(MockDoc::tags)) {
                    // aggregations can be nested
                    agg(BY_COLOR, TermsAgg(MockDoc::color) {
                        minDocCount = 1
                        aggSize = 3
                    })
                }
            }
            // a pretty printing Json configuration that comes with kt-search
            println(DEFAULT_PRETTY_JSON.encodeToString(response))
        }.printStdOut(this)

        +"""
            Note that we are using enum values for the aggregation names. Here is the enum we are using:
        """.trimIndent()

        exampleFromSnippet("/documentation/manual/search/aggregations.kt", "MyAggNamesDef")


        +"""
            You can also use string literals of course.
            
            As you can see from the captured output, parsing this to a type safe structure is a bit of a challenge 
            because the response mixes aggregation names that we specified with aggregation query specific objects.
            
            So, coming up with a model class that captures that is a challenge. The solution for this has to 
            be a bit more complicated than that. 
            However, `kotlinx.serialization` gives us a way out in the form of the schema less `JsonObject` and the 
            ability to deserialize those into custom model classes. In this case we have `TermsAggregationResult` and
            `TermsBucket` classes that we can use for picking apart a terms aggregation using extension functions.
        """.trimIndent()

        val response = runBlocking {
            client.search(indexName) {
                // we don't care about the results here
                resultSize = 0

                agg(BY_TAG, TermsAgg(MockDoc::tags)) {
                    // this optinoal block is where we can specify additional
                    // sub aggregations
                    agg(BY_COLOR, TermsAgg(MockDoc::color) {
                        minDocCount = 1
                        aggSize = 3
                    })
                }
            }
        }

        example {
            // response.aggregations is a JsonObject?
            // termsResult(name) extracts a TermsAggregationResult from there
            val tags = response.aggregations.termsResult(BY_TAG)

            println("Number of buckets: " + tags.buckets.size)
            // since buckets can contain sub aggregations, those too are JsonObjects
            // buckets is a List<JsonObject>
            tags.buckets.forEach { jsonObject ->
                // but we can parse those to a TermsBucket
                // with another extension function
                val tb = jsonObject.parse<TermsBucket>()
                println("${tb.key}: ${tb.docCount}")
                // and we can get to the named sub aggregations from jsonObject
                val colors = jsonObject.termsResult(BY_COLOR)
                // you can also use parsedBuckets to the type safe TermsBucket
                colors.buckets.forEach { colorBucketObject ->
                    val tb = colorBucketObject.parse<TermsBucket>()
                    println("  ${tb.key}: ${tb.docCount}")
                }
            }
        }.printStdOut(this)

        +"""
            With some more extension function magic we can make this a bit nicer.
        """.trimIndent()
        example {
            val tags = response.aggregations.termsResult(BY_TAG)
            // use parsedBucket to get a Bucket<TermsBucket>
            // this allows us to get to the TermsBucket and the aggregations
            tags.parsedBuckets.forEach { tagBucket ->
                println("${tagBucket.parsed.key}: ${tagBucket.parsed.docCount}")
                tagBucket.aggregations
                    .termsResult(BY_COLOR)
                    .parsedBuckets.forEach { colorBucket ->
                        println("  ${colorBucket.parsed.key}: ${colorBucket.parsed.docCount}")
                    }
            }
        }.printStdOut(this)
    }
    section("Geo Aggregations") {
        +"""
            Elasticsearch has great support for geospatial information. And part of 
            its support includes breaking things down by area. The common way for most maps to break 
            down in digital maps is using map tiles. 
            
            Tiles follow a `z/x/y` coordinate system:  
            
            - `z` (zoom level): Controls the scale of the map. Lower values show the whole world in a few tiles, while higher values provide detailed views with many tiles.  
            - `x` and `y` (tile position): Define the column (`x`) and row (`y`) of the tile in a grid at the given zoom level.  
            
            For example, at **zoom level 0**, the entire world fits into a **single tile (0/0/0)**.  
            At **zoom level 1**, the world is divided into **4 tiles (0/0/0, 1/0/0, 0/1/0, 1/1/0)**.  
            At **zoom level 2**, it is further divided into **16 tiles**, and so on, following a `2^z × 2^z` grid structure.  
            
            Each tile typically contains **256×256 pixels** or vector data and is projected using the **Web Mercator (EPSG:3857) projection**, which distorts land areas near the poles but is widely used for interactive maps.
            
            In the examples below, we'll reuse the same geo points we used in the ${ManualPages.GeoQueries.publicLink} documentation:
        """
        val points = runBlocking {
            createGeoPoints(indexName)
        }
        createGeoPointsDoc()
        +"""
            The main bucket aggregation for geo spatial information is the `geotile_grid` aggregation:
            
        """.trimIndent()

        example {
            val response = client.search(indexName) {
                resultSize = 0
                agg("grid", GeoTileGridAgg(TestGeoDoc::point.name,13)) {
                    agg("centroid", GeoCentroidAgg(TestGeoDoc::point.name))
                }
            }

            println(
                DEFAULT_PRETTY_JSON.encodeToString(response)
            )
            val geoTiles = response.aggregations.geoTileGridResult("grid")
            geoTiles.parsedBuckets.forEach { bucket ->
                bucket.aggregations.geoCentroid("centroid").let {
                    val key = bucket.parsed.key
                    println("$key: ${it.location} - ${it.count} ")
                }
            }
        }.printStdOut(this)

    }

    section("Composite aggregations") {
        @Serializable
        data class CompositeDoc(
            val name: String,
            val color: String? = null,
            val value: Long? = null,
            val timestamp: Instant? = null,
        )

        val compositeIndex = "docs-composite-demo"
        val compositeRepo = client.repository(compositeIndex, CompositeDoc.serializer())
        runBlocking {
            try {
                client.deleteIndex(target = compositeIndex, ignoreUnavailable = true)
            } catch (_: Exception) {
            }
            client.createIndex(compositeIndex) {
                mappings {
                    keyword(CompositeDoc::name)
                    keyword(CompositeDoc::color)
                    number<Long>(CompositeDoc::value)
                    date(CompositeDoc::timestamp)
                }
            }
            val now = Clock.System.now()
            compositeRepo.bulk {
                index(
                    CompositeDoc(
                        name = "a",
                        color = "red",
                        value = 5,
                        timestamp = now
                    )
                )
                index(
                    CompositeDoc(
                        name = "b",
                        color = "green",
                        value = 15,
                        timestamp = now - 1.days
                    )
                )
                index(
                    CompositeDoc(
                        name = "c",
                        color = "red",
                        value = 25,
                        timestamp = now - 2.days
                    )
                )
                // missing color/value to exercise missing_bucket
                index(
                    CompositeDoc(
                        name = "d",
                        timestamp = now - 3.days
                    )
                )
            }
        }

        +"""
            Composite aggregations let you page through bucket combinations, which is ideal for
            building scrollable analytics without blowing up response size. They take multiple
            `sources`, each defining how a key is built, and return an `after_key` you can feed
            into the next request.
        """.trimIndent()

        example {
            val colors = mutableSetOf<String>()
            var sawMissing = false
            var afterKey: Map<String, Any?>? = null

            while (true) {
                val response = client.search(compositeIndex) {
                    resultSize = 0
                    agg("by_color", CompositeAgg {
                        aggSize = 2
                        afterKey?.let { afterKey(it) }
                        termsSource(
                            name = "color",
                            field = CompositeDoc::color,
                            missingBucket = true,
                            order = SortOrder.ASC
                        )
                    })
                }
                val composite = response.aggregations.compositeResult("by_color")
                composite.parsedBuckets.forEach { b ->
                    val key = b.parsed.key["color"]
                    val asString = key?.jsonPrimitive?.content ?: "(missing)"
                    colors += asString
                    println("$asString => ${b.parsed.docCount}")
                    if (key == null || key is JsonNull) {
                        sawMissing = true
                    }
                }
                val next = composite.afterKey?.let { after ->
                    mapOf("color" to after["color"]?.jsonPrimitive?.contentOrNull)
                }
                if (next == null || next == afterKey) break
                afterKey = next
            }

            println("Colors seen: $colors (missing bucket: $sawMissing)")
        }.printStdOut(this)

        +"""
            You can combine multiple sources to group on several dimensions at once. Below we
            combine a `terms` source with a numeric `histogram` source.
        """.trimIndent()

        example {
            val response = client.search(compositeIndex) {
                resultSize = 0
                agg("color_value", CompositeAgg {
                    aggSize = 10
                    termsSource("color", CompositeDoc::color)
                    histogramSource("value_bucket", CompositeDoc::value, interval = 10)
                })
            }

            response.aggregations
                .compositeResult("color_value")
                .parsedBuckets
                .forEach { bucket ->
                    val color = bucket.parsed.key["color"]
                        ?.jsonPrimitive
                        ?.content ?: "(missing)"
                    val valueBucket = bucket.parsed.key["value_bucket"]
                        ?.jsonPrimitive
                        ?.doubleOrNull
                    println(
                        "color=$color valueBucket=$valueBucket " +
                            "count=${bucket.parsed.docCount}"
                    )
                }
        }.printStdOut(this)

        +"""
            Date-based composites work the same way. Use `date_histogram` with `calendar_interval`
            or `fixed_interval` to scroll over time buckets:
        """.trimIndent()

        example {
            val response = client.search(compositeIndex) {
                resultSize = 0
                agg("by_day", CompositeAgg {
                    aggSize = 10
                    dateHistogramSource(
                        name = "day",
                        field = CompositeDoc::timestamp,
                        calendarInterval = "1d",
                        order = SortOrder.DESC
                    )
                })
            }
            response.aggregations
                .compositeResult("by_day")
                .parsedBuckets
                .forEach { bucket ->
                    val day = bucket.parsed.key["day"]?.jsonPrimitive?.content
                    println("$day => ${bucket.parsed.docCount}")
                }
        }.printStdOut(this)
    }

    section("Other aggregations") {
        +"""
            Here is a more complicated example where we use various other aggregations.

            Note, we do not support all aggregations currently but it's easy to add
            support for more as needed. Pull requests for this are welcome.

            The DSL surfaces the same options you would use in Elasticsearch or OpenSearch, including
            explicit ordering, `missing` bucket handling, and `hard_bounds`/`extended_bounds` for
            date histograms.
        """.trimIndent()

        example {
            val response = repo.search {
                resultSize = 0 // we only care about the aggs
                agg(BY_DATE, DateHistogramAgg(MockDoc::timestamp) {
                    calendarInterval = "1d"
                    orderByKey(SortOrder.ASC)
                    // Missing timestamps will be treated as if they were at the epoch.
                    missing = 0
                    // hard_bounds keeps buckets within the min/max range
                    // while extended_bounds ensures the boundary buckets
                    // are always included even when empty.
                    extendedBounds(
                        min = "now-30d",
                        max = "now+7d",
                    )
                    hardBounds(
                        min = "now-30d",
                        max = "now+7d",
                    )
                })
                agg(BY_COLOR, TermsAgg(MockDoc::color) {
                    // Explicit ordering and missing bucket
                    // handling mirror Elasticsearch's terms options.
                    missing = "(missing)"
                    orderByField("_count", SortOrder.DESC)
                    executionHint = "map"
                    collectMode = "breadth_first"
                }) {
                    agg(name = MIN_TIME, aggQuery = MinAgg(MockDoc::timestamp))
                    agg(MAX_TIME, MaxAgg(MockDoc::timestamp))
                    // this is a cool way to calculate duration
                    agg(TIME_SPAN, BucketScriptAgg {
                        script = "params.max - params.min"
                        bucketsPath = BucketsPath {
                            this["min"] = MIN_TIME
                            this["max"] = MAX_TIME
                        }
                    })
                    // throw in a top_hits aggregation as well
                    agg(TOP_RESULTS, TopHitsAgg())

                }
                // we can do some stats on the calculated duration!
                agg(SPAN_STATS, ExtendedStatsBucketAgg {
                    bucketsPath = "${BY_COLOR}>${TIME_SPAN}"
                })
                agg(TAG_CARDINALITY, CardinalityAgg(MockDoc::tags))
            }

            // date_histogram works very similar to the terms aggregation
            response.aggregations.dateHistogramResult(BY_DATE)
                .parsedBuckets.map { it.parsed }.forEach { db ->
                    println("${db.keyAsString}: ${db.docCount}")
                }

            // We have extension functions for picking apart
            // each of the aggregation results.
            response.aggregations.termsResult(BY_COLOR).buckets.forEach { b ->
                val tb = b.parse<TermsBucket>()
                println("${tb.key}: ${tb.docCount}")
                println("  Min: ${b.minResult(MIN_TIME).value}")
                println("  Max: ${b.maxResult(MAX_TIME).value}")
                println("  Time span: ${b.bucketScriptResult(TIME_SPAN).value}")
                // top_hits returns the hits part of a normal search response
                println(
                    "  Top: [${
                        b.topHitResult(TOP_RESULTS)
                            .hits.hits.map {
                                it.source?.parse<MockDoc>()?.name
                            }.joinToString(",")
                    }]"
                )
            }

            println(
                "Avg time span: ${
                    response.aggregations
                        .extendedStatsBucketResult(SPAN_STATS).avg
                }"
            )
            println(
                "Tag cardinality: ${
                    response.aggregations.cardinalityResult(TAG_CARDINALITY).value
                }"
            )

        }.printStdOut(this)
    }
    section("Filter aggregations") {
        +"""
            You can use the filter aggregation to narrow down the results and do sub 
            aggregations on the filtered results. 
        """.trimIndent()
        example {
            repo.search {
                resultSize = 0
                agg(
                    name = "filtered",
                    aggQuery = FilterAgg(this@search.term(MockDoc::tags, "foo"))
                ) {
                    agg("colors", TermsAgg(MockDoc::color))
                }
            }.let {
                it.aggregations.filterResult("filtered")?.let { fb ->
                    println("filtered: ${fb.docCount}")
                    fb.bucket.termsResult("colors")
                        .parsedBuckets
                        .forEach { b ->
                            println("${b.parsed.key}: ${b.parsed.docCount}")
                        }
                }
            }
        }.printStdOut(this)

        +"""
            You can also use the filters aggregation to use multiple
             named filter aggregations at the same time
        """.trimIndent()
        example {
            repo.search {
                resultSize = 0
                agg("filtered", FiltersAgg {
                    namedFilter(
                        name = "foo",
                        query = this@search.term(MockDoc::tags, "foo")
                    )
                    namedFilter(
                        name = "bat",
                        query = this@search.term(MockDoc::tags, "bar")
                    )
                }) {
                    agg("colors", TermsAgg(MockDoc::color))
                }
            }.let {
                it.aggregations
                    .filtersResult("filtered")
                    .namedBuckets.forEach { fb ->
                        println("${fb.name}: ${fb.docCount}")
                        println(fb.bucket.termsResult("colors")
                            .parsedBuckets.joinToString(", ") { b ->
                                b.parsed.key + ": " + b.parsed.docCount
                            })
                    }
            }
        }
    }
    section("Extending the Aggregation support") {
        +"""
            Like with the Search DSL, we do not provide exhaustive coverage of all the features and instead provide
            implementations for commonly used aggregations and make it really easy to extend this. 
            
            So, if you get stuck without support for something you need, you should be able to fix it easily yourself.
            
            You can add your own classes and extension functions to deal with aggregation results. We'll illustrate that by showing
            how the Terms aggregation works:
        """.trimIndent()
        example {
            // let's do a simple terms aggregation
            val response = client.search(indexName) {
                // we don't care about the results here
                resultSize = 0

                agg(BY_TAG, TermsAgg("tags"))
            }
            // the termsResult function we used before is short for this:
            val tags = response.aggregations
                .getAggResult<TermsAggregationResult>(BY_TAG)
        }

        +"""
            We need two classes for picking apart terms aggregation results:
        """.trimIndent()

        example {

            @Serializable
            data class TermsBucket(
                val key: String,
                @SerialName("doc_count")
                val docCount: Long,
            )

            @Serializable
            data class TermsAggregationResult(
                @SerialName("doc_count_error_upper_bound")
                val docCountErrorUpperBound: Long,
                @SerialName("sum_other_doc_count")
                val sumOtherDocCount: Long,
                override val buckets: List<JsonObject>
            ) : BucketAggregationResult<TermsBucket>

        }

        +"""
            The generic`BucketAggregationResult` interface that we implement looks like this:
            
            ```kotlin
                interface BucketAggregationResult<T> {
                    val buckets: List<JsonObject>
                }
            ```
            
            This should be implemented on all bucket aggregations. The `T` parameter allows us to deserialize the 
             bucket `JsonObject` instances easily with either `parse` or `parsedBuckets`, which is an extension function
             on `BucketAggregationResult`.
             
             Now all that's left to do is add some extension functions:
             
             ```kotlin
                val TermsAggregationResult.parsedBuckets get() = buckets.map { Bucket(it, TermsBucket.serializer()) }
             ```
             
             `parsedBuckets` is an extension property on TermsAggregationResult that returns a Bucket<TermsBucket>
             
             Using that, we can add some more convenience:
        """.trimIndent()

        example {
            // counts as a map of key to count
            fun List<TermsBucket>.counts() =
                this.associate { it.key to it.docCount }

            // extract terms result by name
            fun Aggregations?.termsResult(
                name: String,
                json: Json = DEFAULT_JSON
            ): TermsAggregationResult =
                getAggResult(name, json)

            // and by enum value
            fun Aggregations?.termsResult(
                name: Enum<*>,
                json: Json = DEFAULT_JSON
            ): TermsAggregationResult =
                getAggResult(name, json)
        }

        +"""
            For more examples for other aggregations, refer to the source code.
        """.trimIndent()

    }
}
