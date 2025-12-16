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
  val timestamp: Instant? = null,
  val durationMs: Double? = null,
  val value: Double? = null,
)

val indexName = "docs-aggs-demo"
client.createIndex(indexName) {
  mappings {
    text(MockDoc::name)
    keyword(MockDoc::color)
    keyword(MockDoc::tags)
    date(MockDoc::timestamp)
    number<Double>(MockDoc::durationMs)
    number<Double>(MockDoc::value)
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
      timestamp = now,
      durationMs = 125.0,
      value = 3.0,
    )
  )
  index(
    MockDoc(
      name = "2",
      tags = listOf("foo"),
      color = "red",
      timestamp = now - 1.days,
      durationMs = 85.0,
      value = 12.5,
    )
  )
  index(
    MockDoc(
      name = "3",
      tags = listOf("foo", "bar"),
      color = "red",
      timestamp = now - 5.days,
      durationMs = 240.0,
      value = 21.0,
    )
  )
  index(
    MockDoc(
      name = "4",
      tags = listOf("foobar"),
      color = "green",
      timestamp = now - 10.days,
      durationMs = 60.0,
      value = null,
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
```

## Picking apart aggregation results

Probably the most used aggregation is the `terms` aggregation:

```kotlin
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
```

This prints:

```text
{
  "took": 36,
  "_shards": {
    "total": 1,
    "successful": 1,
    "failed": 0,
    "skipped": 0
  },
  "timed_out": false,
  "hits": {
    "total": {
      "value": 5,
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
        },
        {
          "key": "missing",
          "doc_count": 1,
          "BY_COLOR": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": []
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
```

This prints:

```text
Number of buckets: 4
bar: 2
  green: 1
  red: 1
foo: 2
  red: 2
foobar: 1
  green: 1
missing: 1
```

With some more extension function magic we can make this a bit nicer.

```kotlin
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
missing: 1
```

## Geo Aggregations

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

In the examples below, we'll reuse the same geo points we used in the https://jillesvangurp.github.io/kt-search/manual/GeoQueries.html documentation:

First, let's create an index with some documents with a geospatial information for a TestGeoDoc class

```kotlin
@Serializable
data class TestGeoDoc(val id: String, val name: String, val point: List<Double>)
```

```kotlin
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
```

The main bucket aggregation for geo spatial information is the `geotile_grid` aggregation:

```kotlin
val response = client.search(indexName) {
  resultSize = 0
  agg("grid", GeoTileGridAgg(TestGeoDoc::point.name, 13)) {
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
```

This prints:

```text
{
  "took": 16,
  "_shards": {
    "total": 1,
    "successful": 1,
    "failed": 0,
    "skipped": 0
  },
  "timed_out": false,
  "hits": {
    "total": {
      "value": 5,
      "relation": "eq"
    },
    "hits": []
  },
  "aggregations": {
    "grid": {
      "buckets": [
        {
          "key": "13/4400/2686",
          "doc_count": 2,
          "centroid": {
            "location": {
              "lat": 52.52330794930458,
              "lon": 13.38908314704895
            },
            "count": 2
          }
        },
        {
          "key": "13/4403/2692",
          "doc_count": 1,
          "centroid": {
            "location": {
              "lat": 52.36703671049327,
              "lon": 13.517282847315073
            },
            "count": 1
          }
        },
        {
          "key": "13/4401/2686",
          "doc_count": 1,
          "centroid": {
            "location": {
              "lat": 52.52082384657115,
              "lon": 13.409421667456627
            },
            "count": 1
          }
        },
        {
          "key": "13/4398/2685",
          "doc_count": 1,
          "centroid": {
            "location": {
              "lat": 52.55955611821264,
              "lon": 13.292043171823025
            },
            "count": 1
          }
        }
      ]
    }
  }
}
13/4400/2686: Point(lat=52.52330794930458, lon=13.38908314704895) - 2 
13/4403/2692: Point(lat=52.36703671049327, lon=13.517282847315073) - 1 
13/4401/2686: Point(lat=52.52082384657115, lon=13.409421667456627) - 1 
13/4398/2685: Point(lat=52.55955611821264, lon=13.292043171823025) - 1 
```

## Composite aggregations

Composite aggregations let you page through bucket combinations, which is ideal for
building scrollable analytics without blowing up response size. They take multiple
`sources`, each defining how a key is built, and return an `after_key` you can feed
into the next request.

```kotlin
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
```

This prints:

```text
null => 1
green => 1
red => 2
Colors seen: [null, green, red] (missing bucket: true)
```

You can combine multiple sources to group on several dimensions at once. Below we
combine a `terms` source with a numeric `histogram` source.

```kotlin
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
```

This prints:

```text
color=green valueBucket=10.0 count=1
color=red valueBucket=0.0 count=1
color=red valueBucket=20.0 count=1
```

Date-based composites work the same way. Use `date_histogram` with `calendar_interval`
or `fixed_interval` to scroll over time buckets:

```kotlin
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
```

This prints:

```text
1765843200000 => 1
1765756800000 => 1
1765670400000 => 1
1765584000000 => 1
```

## Other aggregations

Here is a more complicated example where we use various other aggregations.

Note, we do not support all aggregations currently but it's easy to add
support for more as needed. Pull requests for this are welcome.

The DSL surfaces the same options you would use in Elasticsearch or OpenSearch, including
explicit ordering, `missing` bucket handling, and `hard_bounds`/`extended_bounds` for
date histograms.

Numeric histograms bucket numeric fields into fixed ranges. Use `offset` to shift the
bucket boundaries, `missing` to control how null values are counted, and `extended_bounds`
or `hard_bounds` to force buckets at the edges of the range.

```kotlin
val response = repo.search {
  resultSize = 0 // we only care about the aggs
  agg("by_value", HistogramAgg(MockDoc::value, interval = 10) {
    offset = 2
    minDocCount = 0
    missing = 0
    extendedBounds(min = 0, max = 30)
    hardBounds(min = 0, max = 30)
  })
}

println(DEFAULT_PRETTY_JSON.encodeToString(response))
```

This prints:

```text
{
  "took": 24,
  "_shards": {
    "total": 1,
    "successful": 1,
    "failed": 0,
    "skipped": 0
  },
  "timed_out": false,
  "hits": {
    "total": {
      "value": 5,
      "relation": "eq"
    },
    "hits": []
  },
  "aggregations": {
    "by_value": {
      "buckets": [
        {
          "key": -8.0,
          "doc_count": 0
        },
        {
          "key": 2.0,
          "doc_count": 0
        },
        {
          "key": 12.0,
          "doc_count": 0
        },
        {
          "key": 22.0,
          "doc_count": 0
        }
      ]
    }
  }
}
```

```kotlin
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

```

This prints:

```text
2025-11-16T00:00:00.000Z: 0
2025-11-17T00:00:00.000Z: 0
2025-11-18T00:00:00.000Z: 0
2025-11-19T00:00:00.000Z: 0
2025-11-20T00:00:00.000Z: 0
2025-11-21T00:00:00.000Z: 0
2025-11-22T00:00:00.000Z: 0
2025-11-23T00:00:00.000Z: 0
2025-11-24T00:00:00.000Z: 0
2025-11-25T00:00:00.000Z: 0
2025-11-26T00:00:00.000Z: 0
2025-11-27T00:00:00.000Z: 0
2025-11-28T00:00:00.000Z: 0
2025-11-29T00:00:00.000Z: 0
2025-11-30T00:00:00.000Z: 0
2025-12-01T00:00:00.000Z: 0
2025-12-02T00:00:00.000Z: 0
2025-12-03T00:00:00.000Z: 0
2025-12-04T00:00:00.000Z: 0
2025-12-05T00:00:00.000Z: 0
2025-12-06T00:00:00.000Z: 0
2025-12-07T00:00:00.000Z: 0
2025-12-08T00:00:00.000Z: 0
2025-12-09T00:00:00.000Z: 0
2025-12-10T00:00:00.000Z: 0
2025-12-11T00:00:00.000Z: 0
2025-12-12T00:00:00.000Z: 0
2025-12-13T00:00:00.000Z: 0
2025-12-14T00:00:00.000Z: 0
2025-12-15T00:00:00.000Z: 0
2025-12-16T00:00:00.000Z: 0
2025-12-17T00:00:00.000Z: 0
2025-12-18T00:00:00.000Z: 0
2025-12-19T00:00:00.000Z: 0
2025-12-20T00:00:00.000Z: 0
2025-12-21T00:00:00.000Z: 0
2025-12-22T00:00:00.000Z: 0
2025-12-23T00:00:00.000Z: 0
(missing): 5
```

## Percentiles and percentile ranks

Percentiles are useful to spot the shape of your numeric distributions, for example when you
are tracking request latency or other performance metrics. The DSL exposes the `keyed`
option by default and allows switching between HDR histograms and TDigest implementations.

```kotlin
val response = repo.search {
  resultSize = 0
  agg("load_time_percentiles", PercentilesAgg(MockDoc::durationMs) {
    percentileValues = listOf(50.0, 90.0, 99.0)
  })
}

val percentiles = response.aggregations
  ?.get("load_time_percentiles")?.jsonObject
  ?.get("values")?.jsonObject

percentiles?.forEach { (percentile, value) ->
  println("$percentile => ${value.jsonPrimitive.doubleOrNull}")
}
```

This prints:

```text

```

Percentile ranks work the other way around by showing the percentile for explicit target values.
You can also tweak the TDigest compression to balance accuracy and memory use.

```kotlin
val response = repo.search {
  resultSize = 0
  agg("load_time_ranks", PercentileRanksAgg(MockDoc::durationMs) {
    rankValues = listOf(50.0, 100.0, 250.0)
    tdigest(compression = 110.0)
  })
}

val percentileRanks = response.aggregations
  ?.get("load_time_ranks")?.jsonObject
  ?.get("values")?.jsonObject

percentileRanks?.forEach { (value, percentile) ->
  println("$value => ${percentile.jsonPrimitive.doubleOrNull}")
}
```

This prints:

```text
50.0 => null
100.0 => null
250.0 => null
```

## Metric aggregations

We also provide typed helpers for the metric aggregations that operate on a single field. They
support specifying a `missing` value as well as using scripts instead of raw fields when you
want to pre-process values.

```kotlin
val metricsDsl = SearchDSL().apply {
  agg("average_duration", AvgAgg(field = "duration"))
  agg("duration_count", ValueCountAgg(field = "duration", missing = 0))
  agg("duration_stats", StatsAgg(field = "duration"))
  agg(
    "duration_extended_stats",
    ExtendedStatsAgg(
      script = Script.create { source = "doc['duration'].value" },
      missing = 0
    )
  )
}

println(metricsDsl.json(true))
```

This prints:

```text
{
  "aggs": {
    "average_duration": {
      "avg": {
        "field": "duration"
      }
    },
    "duration_count": {
      "value_count": {
        "field": "duration",
        "missing": 0
      }
    },
    "duration_stats": {
      "stats": {
        "field": "duration"
      }
    },
    "duration_extended_stats": {
      "extended_stats": {
        "missing": 0,
        "script": {
          "source": "doc['duration'].value"
        }
      }
    }
  }
}
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
filtered: 0
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
      println(
        fb.bucket.termsResult("colors")
          .parsedBuckets.joinToString(", ") { b ->
            b.parsed.key + ": " + b.parsed.docCount
          })
    }
}
```

## Pipeline aggregations

Pipeline aggregations allow you to reuse metrics from other aggregations and perform additional calculations.
You can chain multiple pipelines together, mixing them with bucket aggregations to keep queries compact.

```kotlin
val pipelineAggQuery = SearchDSL().apply {
  agg(
    "events_per_day",
    DateHistogramAgg(
      field = MockDoc::timestamp
    ) {
      calendarInterval = "1d"
    }) {
    agg("per_color", TermsAgg(MockDoc::color))
    agg("first_event", MinAgg(MockDoc::timestamp))
    agg("per_day_change", DerivativeAgg {
      bucketsPath = "first_event"
      gapPolicy = "skip"
    })
    agg("rolling_events", CumulativeSumAgg {
      bucketsPath = "first_event"
    })
    agg("recent_activity", BucketSelectorAgg {
      bucketsPath = BucketsPath {
        this["first"] = "first_event"
        this["count"] = "_count"
      }
      script = "params.count > 0 && params.first != null"
      gapPolicy = "insert_zeros"
    })
  }
}

println(DEFAULT_PRETTY_JSON.encodeToString(pipelineAggQuery))
```

This prints:

```text

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

  agg(BY_TAG, TermsAgg("tags"))
}
// the termsResult function we used before is short for this:
val tags = response.aggregations
  .getAggResult<TermsAggregationResult>(BY_TAG)
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