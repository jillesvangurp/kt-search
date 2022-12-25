# Aggregations 

The aggregations DSL in Elasticsearch is both very complicated and vast. This is an area where
using Kotlin can vastly simplify things for programmers.

First lets create some sample documents:

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

Probably the most used aggregation is the terms aggregation. 

```kotlin
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
```

Captured Output:

```
{
  "took": 77,
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
    "hits": [
    ]
  },
  "aggregations": {
    "by_tag": {
      "doc_count_error_upper_bound": 0,
      "sum_other_doc_count": 0,
      "buckets": [
        {
          "key": "bar",
          "doc_count": 2,
          "by_color": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "bar",
                "doc_count": 2
              },
              {
                "key": "foo",
                "doc_count": 1
              }
            ]
          }
        },
        {
          "key": "foo",
          "doc_count": 2,
          "by_color": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "foo",
                "doc_count": 2
              },
              {
                "key": "bar",
                "doc_count": 1
              }
            ]
          }
        },
        {
          "key": "foobar",
          "doc_count": 1,
          "by_color": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "foobar",
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

As you can see from the captured output, parsing this to a type safe structure is a bit of a challenge 
because the response mixes aggregation names that we specified with aggregation query specific objects.

The solution for this has to be a bit more complicated than for regular searches. 
However, `kotlinx.serialization` gives us a way out in the form of the schema less `JsonObject` and the 
ability to deserialize those into custom model classes. In this case we have `TermsAggregationResult` and
`TermsBucket` classes that we can use for picking apart a terms aggregation.

```kotlin
// this is how you look up a TermsAggregationResult
val tags = response.aggregations.termsResult("by_tag")

// since buckets can contain sub aggregations, those too are JsonObjects
println("Number of buckets: " + tags.buckets.size)
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
```

Captured Output:

```
Number of buckets: 3
bar: 2
  bar: 2
  foo: 1
foo: 2
  foo: 2
  bar: 1
foobar: 1
  foobar: 1

```

## Other aggregations

Here is a more complicated example where we use scripting to calculate a timespan and 
then do a stats aggregation on that.

```kotlin
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

// date_histogram works very similar to the terms aggregation
response.aggregations.dateHistogramResult("by_date")
  .decodeBuckets().forEach { b ->
    println("${b.keyAsString}: ${b.docCount}")
  }

response.aggregations.termsResult("by_color").buckets.forEach { b ->
  val tb = b.parse<TermsBucket>()
  println("${tb.key}: ${tb.docCount}")
  println("  Min: ${b.minResult("max_time").value}")
  println("  Max: ${b.minResult("max_time").value}")
  println("  Time span: ${b.bucketScriptResult("time_span").value}")
}

println("Avg time span: ${
  response.aggregations
      .extendedStatsBucketResult("span_stats").avg
}")

```

Captured Output:

```
2022-12-15T00:00:00.000Z: 1
2022-12-16T00:00:00.000Z: 0
2022-12-17T00:00:00.000Z: 0
2022-12-18T00:00:00.000Z: 0
2022-12-19T00:00:00.000Z: 0
2022-12-20T00:00:00.000Z: 1
2022-12-21T00:00:00.000Z: 0
2022-12-22T00:00:00.000Z: 0
2022-12-23T00:00:00.000Z: 0
2022-12-24T00:00:00.000Z: 1
2022-12-25T00:00:00.000Z: 1
green: 2
  Min: 1.671963308592E12
  Max: 1.671963308592E12
  Time span: 8.64E8
red: 2
  Min: 1.671876908592E12
  Max: 1.671876908592E12
  Time span: 3.456E8
Avg time span: 6.048E8

```

## Extending the Aggregation support

Like with the Search DSL, we do not strive to provide full coverage of all the features and instead provide
implementations for commonly used aggregations only and make it really easy to extend this. 

You can easily add your own classes to deal with aggregation results. We'll illustrate that by showing
how the Terms aggregation works:

```kotlin
// let's do a simple terms aggregation
val response = client.search(indexName) {
  // we don't care about the results here
  resultSize = 0

  agg("by_tag", TermsAgg("tags"))
}
// the termsResult function we used before is short for this:
val tags = response.aggregations
  .getAggResult<TermsAggregationResult>("by_tag")
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
 bucket `JsonObject` instances easily with either `parse` or `decodeBuckets`, which is an extension function
 on `BucketAggregationResult`.

---

| [KT Search Manual](README.md) | Previous: [Search and Queries](Search.md) | Next: [Deep paging using search_after and scroll](DeepPaging.md) |