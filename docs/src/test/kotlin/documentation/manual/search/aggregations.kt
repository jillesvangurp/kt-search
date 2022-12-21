@file:Suppress("NAME_SHADOWING")

package documentation.manual.search

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.days

val aggregationsMd = sourceGitRepository.md {
    val client = SearchClient(KtorRestClient(Node("localhost", 9999), logging = true))

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
            client.deleteIndex(indexName)
        } catch (e: Exception) {
        }
    }

    +"""
        The aggregations DSL in Elasticsearch is both very complicated and vast. This is an area where
        using Kotlin can vastly simplify things for programmers.
        
        First lets create some sample documents:
        
    """.trimIndent()
    suspendingBlock() {
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
        }
    }

    section("Picking apart aggregation results") {
        +"""
            Probably the most used aggregation is the terms aggregation. 
        """.trimIndent()
        suspendingBlock {
            val response = client.search(indexName) {
                // we don't care about the results here
                resultSize = 0

                agg("by_tag", TermsAgg("tags")) {
                    // aggregations can be nested
                    agg("by_color", TermsAgg("tags") {
                        minDocCount = 1
                        aggSize = 3
                    })
                }
            }
            // a pretty printing Json configuration that comes with kt-search
            println(DEFAULT_PRETTY_JSON.encodeToString(response))
        }

        +"""
            As you can see from the captured output, parsing this to a type safe structure is a bit of a challenge 
            because the response mixes aggregation names that we specified with aggregation query specific objects.
            
            The solution for this has to be a bit more complicated than for regular searches. 
            However, `kotlinx.serialization` gives us a way out in the form of the schema less `JsonObject` and the 
            ability to deserialize those into custom model classes. In this case we have `TermsAggregationResult` and
            `TermsBucket` classes that we can use for picking apart a terms aggregation.
        """.trimIndent()

        val response = runBlocking {
            client.search(indexName) {
                // we don't care about the results here
                resultSize = 0

                agg("by_tag", TermsAgg("tags")) {
                    // this optinoal block is where we can specify additional
                    // sub aggregations
                    agg("by_color", TermsAgg("tags") {
                        minDocCount = 1
                        aggSize = 3
                    })
                }
            }
        }

        suspendingBlock {
            // this is how you look up a TermsAggregationResult
            val tags = response.aggregations.termsResult("by_tag")

            // since buckets can contain sub aggregations, those too are JsonObjects
            println("Number of buckets" + tags.buckets.size)
            tags.buckets.forEach { b ->
                // of course we can parse that to a TermsBucket
                val tb = b.parse<TermsBucket>()
                println("${tb.key}: ${tb.docCount}")
                val colors = b.termsResult("by_color")
                // you can also use decodeBuckets to get a type safe TermsBucket
                colors.decodeBuckets().forEach { cb ->
                    println("  ${cb.key}: ${cb.docCount}")
                }
            }
        }
    }
    section("Other aggregations") {
        +"""
            Here is a more complicated example where we use scripting to calculate a timespan and 
            then do a stats aggregation on that.
        """.trimIndent()

        suspendingBlock {
            val response = repo.search {
                resultSize = 0 // we only care about the aggs
                // allows us to use the aggSpec after the query runs
                agg("by_date", DateHistogramAgg(MockDoc::timestamp) {
                    calendarInterval = "1d"
                })
                agg("by_color", TermsAgg(MockDoc::color)) {
                    agg("min_time", MinAgg(MockDoc::timestamp))
                    agg("max_time", MaxAgg(MockDoc::timestamp))
                    agg("time_span", BucketScriptAgg {
                        script = "params.max - params.min"
                        bucketsPath = BucketsPath {
                            this["min"] = "min_time"
                            this["max"] = "max_time"
                        }
                    })
                }
                agg("span_stats", ExtendedStatsBucketAgg {
                    bucketsPath = "by_color>time_span"
                })
            }


            response.aggregations.termsResult("by_color").buckets.forEach { b ->
                val tb = b.parse<TermsBucket>()
                println("${tb.key}: ${tb.docCount}")
                println("  Min: ${b.minResult("max_time").value}")
                println("  Max: ${b.minResult("max_time").value}")
                println("  Time span: ${b.bucketScriptResult("time_span").value}")
            }
            // date_histogram works very similar to the terms aggregation
            response.aggregations.dateHistogramResult("by_date")
                .decodeBuckets().forEach { b ->
                    println("${b.keyAsString}: ${b.docCount}")
                }
            println("Avg time span: ${
                response.aggregations
                        .extendedStatsBucketResult("span_stats").avg
            }")

        }
    }
    section("Extending the Aggregation support") {
        +"""
            Like with the Search DSL, we do not strive to provide full coverage of all the features and instead provide
            implementations for commonly used aggregations only and make it really easy to extend this. 
            
            You can easily add your own classes to deal with aggregation results. We'll illustrate that by showing
            how the Terms aggregation works:
        """.trimIndent()
        suspendingBlock {
            // let's do a simple terms aggregation
            val response = client.search(indexName) {
                // we don't care about the results here
                resultSize = 0

                agg("by_tag", TermsAgg("tags"))
            }
            // the termsResult function we used before is short for this:
            val tags = response.aggregations
                .getAggResult<TermsAggregationResult>("by_tag")
        }

        +"""
            We need two classes for picking apart terms aggregation results:
        """.trimIndent()

        block {

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
             bucket `JsonObject` instances easily with either `parse` or `decodeBuckets`, which is an extension function
             on `BucketAggregationResult`.
        """.trimIndent()
    }
}