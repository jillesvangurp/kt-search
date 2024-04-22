# Aggregations 

| [KT Search Manual](README.md) | Previous: [Specialized Queries](SpecializedQueries.md) | Next: [Deep Paging Using search_after and scroll](DeepPaging.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

The aggregations DSL in Elasticsearch is both very complicated and vast. This is an area where
using Kotlin can vastly simplify things for programmers.

The search dsl provides several levels of convenience here:

- we can use enum values to name the aggregations and class properties to name fields
- our dsl support allows us to easily nest aggregations 
in a more compact way than with json
- we can pick apart nested bucket aggregation results with extension functions.

First lets create some sample documents to aggregate on:

```kotlin
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
```

## Picking apart aggregation results

Probably the most used aggregation is the `terms` aggregation:

```kotlin
val response = client.search(indexName) {
  // we don't care about the results here
  resultSize = 0

  agg(MyAggNames.BY_TAG, TermsAgg(MockDoc::tags)) {
    // aggregations can be nested
    agg(MyAggNames.BY_COLOR, TermsAgg(MockDoc::color) {
      minDocCount = 1
      aggSize = 3
    })
  }
}
// a pretty printing Json configuration that comes with kt-search
println(DEFAULT_PRETTY_JSON.encodeToString(response))
```

This prints:

```text
{
  "took": 7,
  "_shards": {
    "total": 1,
    "successful": 1,
    "failed": 0,
    "skipped": 0
  },
  "timed_out": false,
  "hits": {
    "total": {
      "value": 4,
      "relation": "eq"
    },
    "hits": []
  },
  "aggregations": {
    "BY_TAG": {
      "doc_count_error_upper_bound": 0,
      "sum_other_doc_count": 0,
      "buckets": [
        {
          "key": "bar",
          "doc_count": 2,
          "BY_COLOR": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "green",
                "doc_count": 1
              },
              {
                "key": "red",
                "doc_count": 1
              }
            ]
          }
        },
        {
          "key": "foo",
          "doc_count": 2,
          "BY_COLOR": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "red",
                "doc_count": 2
              }
            ]
          }
        },
        {
          "key": "foobar",
          "doc_count": 1,
          "BY_COLOR": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "green",
                "doc_count": 1
              }
            ]
          }
        }
      ]
    }
  }
}
```

Note that we are using enum values for the aggregation names. Here is the enum we are using:

```kotlin
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
```

You can also use string literals of course.

As you can see from the captured output, parsing this to a type safe structure is a bit of a challenge 
because the response mixes aggregation names that we specified with aggregation query specific objects.

So, coming up with a model class that captures that is a challenge. The solution for this has to 
be a bit more complicated than that. 
However, `kotlinx.serialization` gives us a way out in the form of the schema less `JsonObject` and the 
ability to deserialize those into custom model classes. In this case we have `TermsAggregationResult` and
`TermsBucket` classes that we can use for picking apart a terms aggregation using extension functions.

```kotlin
// response.aggregations is a JsonObject?
// termsResult(name) extracts a TermsAggregationResult from there
val tags = response.aggregations.termsResult(MyAggNames.BY_TAG)

println("Number of buckets: " + tags.buckets.size)
// since buckets can contain sub aggregations, those too are JsonObjects
// buckets is a List<JsonObject>
tags.buckets.forEach { jsonObject ->
  // but we can parse those to a TermsBucket
  // with another extension function
  val tb = jsonObject.parse<TermsBucket>()
  println("${tb.key}: ${tb.docCount}")
  // and we can get to the named sub aggregations from jsonObject
  val colors = jsonObject.termsResult(MyAggNames.BY_COLOR)
  // you can also use parsedBuckets to the type safe TermsBucket
  colors.buckets.forEach { colorBucketObject ->
    val tb = colorBucketObject.parse<TermsBucket>()
    println("  ${tb.key}: ${tb.docCount}")
  }
}
```

This prints:

```text
Number of buckets: 3
bar: 2
  green: 1
  red: 1
foo: 2
  red: 2
foobar: 1
  green: 1
```

With some more extension function magic we can make this a bit nicer.

```kotlin
val tags = response.aggregations.termsResult(MyAggNames.BY_TAG)
// use parsedBucket to get a Bucket<TermsBucket>
// this allows us to get to the TermsBucket and the aggregations
tags.parsedBuckets.forEach { tagBucket ->
  println("${tagBucket.parsed.key}: ${tagBucket.parsed.docCount}")
  tagBucket.aggregations
    .termsResult(MyAggNames.BY_COLOR)
    .parsedBuckets.forEach { colorBucket ->
      println("  ${colorBucket.parsed.key}: ${colorBucket.parsed.docCount}")
    }
}
```

This prints:

```text
bar: 2
  green: 1
  red: 1
foo: 2
  red: 2
foobar: 1
  green: 1
```

## Other aggregations

Here is a more complicated example where we use various other aggregations.

Note, we do not support all aggregations currently but it's easy to add
support for more as needed. Pull requests for this are welcome.

```kotlin
val response = repo.search {
  resultSize = 0 // we only care about the aggs
  agg(MyAggNames.BY_DATE, DateHistogramAgg(MockDoc::timestamp) {
    calendarInterval = "1d"
  })
  agg(MyAggNames.BY_COLOR, TermsAgg(MockDoc::color)) {
    agg(MyAggNames.MIN_TIME, MinAgg(MockDoc::timestamp))
    agg(MyAggNames.MAX_TIME, MaxAgg(MockDoc::timestamp))
    // this is a cool way to calculate duration
    agg(MyAggNames.TIME_SPAN, BucketScriptAgg {
      script = "params.max - params.min"
      bucketsPath = BucketsPath {
        this["min"] = MyAggNames.MIN_TIME
        this["max"] = MyAggNames.MAX_TIME
      }
    })
    // throw in a top_hits aggregation as well
    agg(MyAggNames.TOP_RESULTS, TopHitsAgg())

  }
  // we can do some stats on the calculated duration!
  agg(MyAggNames.SPAN_STATS, ExtendedStatsBucketAgg {
    bucketsPath = "${MyAggNames.BY_COLOR}>${MyAggNames.TIME_SPAN}"
  })
  agg(MyAggNames.TAG_CARDINALITY, CardinalityAgg(MockDoc::tags))
}

// date_histogram works very similar to the terms aggregation
response.aggregations.dateHistogramResult(MyAggNames.BY_DATE)
  .parsedBuckets.map { it.parsed }.forEach { db ->
    println("${db.keyAsString}: ${db.docCount}")
  }

// We have extension functions for picking apart
// each of the aggregation results.
response.aggregations.termsResult(MyAggNames.BY_COLOR).buckets.forEach { b ->
  val tb = b.parse<TermsBucket>()
  println("${tb.key}: ${tb.docCount}")
  println("  Min: ${b.minResult(MyAggNames.MIN_TIME).value}")
  println("  Max: ${b.maxResult(MyAggNames.MAX_TIME).value}")
  println("  Time span: ${b.bucketScriptResult(MyAggNames.TIME_SPAN).value}")
  // top_hits returns the hits part of a normal search response
  println(
    "  Top: [${
      b.topHitResult(MyAggNames.TOP_RESULTS)
        .hits.hits.map {
          it.source?.parse<MockDoc>()?.name
        }.joinToString(",")
    }]"
  )
}

println(
  "Avg time span: ${
    response.aggregations
      .extendedStatsBucketResult(MyAggNames.SPAN_STATS).avg
  }"
)
println(
  "Tag cardinality: ${
    response.aggregations.cardinalityResult(MyAggNames.TAG_CARDINALITY).value
  }"
)

```

This prints:

```text
2024-04-12T00:00:00.000Z: 1
2024-04-13T00:00:00.000Z: 0
2024-04-14T00:00:00.000Z: 0
2024-04-15T00:00:00.000Z: 0
2024-04-16T00:00:00.000Z: 0
2024-04-17T00:00:00.000Z: 1
2024-04-18T00:00:00.000Z: 0
2024-04-19T00:00:00.000Z: 0
2024-04-20T00:00:00.000Z: 0
2024-04-21T00:00:00.000Z: 1
2024-04-22T00:00:00.000Z: 1
green: 2
  Min: 1.71292695185E12
  Max: 1.71379095185E12
  Time span: 8.64E8
  Top: [1,4]
red: 2
  Min: 1.71335895185E12
  Max: 1.71370455185E12
  Time span: 3.456E8
  Top: [2,3]
Avg time span: 6.048E8
Tag cardinality: 3
```

## Filter aggregations

You can use the filter aggregation to narrow down the results and do sub 
aggregations on the filtered results. 

```kotlin
repo.search {
  resultSize = 0
  agg("filtered", FilterAgg(this@search.term(MockDoc::tags, "foo"))) {
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
```

This prints:

```text
filtered: 2
red: 2
```

You can also use the filters aggregation to use multiple named filter aggregations at the same time

```kotlin
repo.search {
  resultSize = 0
  agg("filtered", FiltersAgg {
    namedFilter("foo", this@search.term(MockDoc::tags, "foo"))
    namedFilter("bat", this@search.term(MockDoc::tags, "bar"))
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
```

## Extending the Aggregation support

Like with the Search DSL, we do not provide exhaustive coverage of all the features and instead provide
implementations for commonly used aggregations and make it really easy to extend this. 

So, if you get stuck without support for something you need, you should be able to fix it easily yourself.

You can add your own classes and extension functions to deal with aggregation results. We'll illustrate that by showing
how the Terms aggregation works:

```kotlin
// let's do a simple terms aggregation
val response = client.search(indexName) {
  // we don't care about the results here
  resultSize = 0

  agg(MyAggNames.BY_TAG, TermsAgg("tags"))
}
// the termsResult function we used before is short for this:
val tags = response.aggregations
  .getAggResult<TermsAggregationResult>(MyAggNames.BY_TAG)
```

We need two classes for picking apart terms aggregation results:

```kotlin

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

```

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

```kotlin
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
```

For more examples for other aggregations, refer to the source code.



---

| [KT Search Manual](README.md) | Previous: [Specialized Queries](SpecializedQueries.md) | Next: [Deep Paging Using search_after and scroll](DeepPaging.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |