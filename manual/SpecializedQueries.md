# Specialized Queries 

| [KT Search Manual](README.md) | Previous: [Geo Spatial Queries](GeoQueries.md) | Next: [Aggregations](Aggregations.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

## Distance Feature

You can use distance_feature queries to rank to the distance between your documents
and an origin. This works for both dates and points.

Lets create some documents with a time and point field for a few historical buildings.

```kotlin
@Serializable
data class TestDoc(
  val name: String,
  val buildingTime: Instant,
  val point: List<Double>,
)

client.createIndex(indexName) {
  mappings {
    date(TestDoc::buildingTime)
    geoPoint(TestDoc::point)
  }
}

client.bulk(target = indexName) {
  create(TestDoc(
    name = "Tv Tower",
    buildingTime = Instant.parse("1969-01-01T00:00Z"),
    point = listOf(13.40942173843226, 52.52082388531597)
  ))
  create(TestDoc(
    name = "Brandenburger Tor",
    buildingTime = Instant.parse("1791-01-01T00:00Z"),
    point = listOf(13.377622382132417, 52.51632993824314)
  ))
}
```

Now we can score documents on physical distance as follows:

```kotlin
val first = client.search(indexName) {
  query = distanceFeature(
    field = TestDoc::point,
    pivot = "20km",
    origin = listOf(13.377622382132417, 52.51632993824314)
  )
}.hits?.hits?.first()?.parseHit<TestDoc>()!!

println(first.name)
```

This prints:
 
```
Brandenburger Tor

```

The Brandenburger Tor is closer to itself than the TV tower so it comes out on top.

And on the building year like this:

```kotlin
val first = client.search(indexName) {
  query = distanceFeature(
    field = TestDoc::buildingTime,
    pivot = "200m", // 200 months
    origin = "2020-01-01T00:00"
  )
}.hits?.hits?.first()?.parseHit<TestDoc>()!!

print(first.name)
```

This prints:
 
```
Tv Tower
```

The building year of the TV tower is closer to 2020 than the Brandenburger Gate

## Rank Feature

You can use the `rank_feature` query to rank on custom scores (e.g. page rank) 
or other numeric values in a document. For this to work you need to use the `rank_feature` 
or `rank_features` mapping. For example for these documents:           

```kotlin
@Serializable
data class TestDoc(
  val name: String,
  val ktSearchRank: Int,
)

client.createIndex(indexName) {
  mappings {
    rankFeature(TestDoc::ktSearchRank)
  }
}

client.bulk(target = indexName) {
  create(TestDoc(
    name = "low",
    ktSearchRank = 45
  ))
  create(TestDoc(
    name = "medium",
    ktSearchRank = 75
  ))
  create(TestDoc(
    name = "high",
    ktSearchRank = 96
  ))
}
```

We add a few documents with a `ktSearchRank` field that is mapped as a rank_feature.
 
You can use different functions:

- saturation (default). Uses a default pivot based on the mean value of the rank_feature field. Or you can provide custom one
- sigmoid. Similar to saturation but uses an exponential that you (typically) learn from your data.
- log. Logarithmic function with a scaling factory that you can specify.
- linear. Simple linear score based on te numeric value.

```kotlin
client.search(indexName) {
  // saturation query with default pivot
  query = rankFeature(TestDoc::ktSearchRank)
  // saturation with custom pivot
  query = rankFeature(TestDoc::ktSearchRank) {
    saturation(pivot= 50.0)
  }
  // sigmoid
  query = rankFeature(TestDoc::ktSearchRank) {
    sigmoid(pivot= 50.0, exponent = 0.8)
  }
  // log function
  query = rankFeature(TestDoc::ktSearchRank) {
    log(scalingFactor = 0.1)
  }
  // linear function
  query = rankFeature(TestDoc::ktSearchRank) {
    linear()
  }
}
```



---

| [KT Search Manual](README.md) | Previous: [Geo Spatial Queries](GeoQueries.md) | Next: [Aggregations](Aggregations.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |