# Search and Queries 

                | [KT Search Manual](README.md) | Previous: [Indices, Settings, Mappings, and Aliases](IndexManagement.md) | Next: [Text Queries](TextQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |
                ---                
                Searching is of course the main reason for using Opensearch and Elasticsearch. Kt-search supports this
with a rich Kotlin DSL. However, you can also use string literals to search.

The advantage of using a Kotlin DSL for writing your queries is that you can rely on Kotlin's type safety
and also use things like refactoring, property references to fields in your model classes, functional programming,
etc.

## Some test documents

Let's quickly create some documents to search through.

```kotlin
@Serializable
data class TestDoc(val name: String, val tags: List<String> = listOf())
```

```kotlin
// re-create the index
deleteIndex(indexName)
createIndex(indexName) {
  mappings {
    text(TestDoc::name)
    keyword(TestDoc::tags) {
      fields {
        text("txt")
      }
    }
    number<Double>(TestDoc::price)
  }
}

val docs = listOf(
  TestDoc(
    id = "1",
    name = "Apple",
    tags = listOf("fruit"),
    price = 0.50
  ),
  TestDoc(
    id = "2",
    name = "Banana",
    tags = listOf("fruit"),
    price = 0.80
  ),
  TestDoc(
    id = "3",
    name = "Green Beans",
    tags = listOf("legumes"),
    price = 1.20
  )
)
docs.forEach { d ->
  client.indexDocument(
    target = indexName,
    document = d,
    id = d.id,
    refresh = Refresh.WaitFor
  )
}
```

This creates a simple index with a custom mapping and adds some documents using our API.

You can learn more about creating indices with customized mappings here: [Indices, Settings, Mappings, and Aliases](IndexManagement.md)

## Searching without the Kotlin DSL

The simplest is to search for everything: 

```kotlin
// will print the ids of the documents that were found
client.search(indexName).ids

```

->

```
[1, 2, 3]
```

Of course normally, you'd specify some kind of query. One valid way is to simply pass that as a string.
Kotlin of course has multiline strings that can be templated as well. So, this may be all you need.

```kotlin
val term = "legumes"
client.search(
  indexName, rawJson = """
  {
    "query": {
      "term": {
        // using property references is a good idea
        "tags": {               
          "value":"$term"
        }
      }
    }
  }
""".trimIndent()
).ids
```

->

```
[3]
```

Note how we are using templated strings here. With some hand crafted queries, this style of querying may be very useful.

Another advantage is that you can paste queries straight from the Kibana development console.

## Using the SearchDSL

Of course it is much nicer to query using a Kotlin Search DSL (Domain Specific Language). 
Here is the same query using the `SearchDSL`.

```kotlin
client.search(indexName) {
  query = term(TestDoc::tags, "legumes")
}.ids
```

->

```
[3]
```

`client.search` takes a block that has a `SearchDSL` object as its receiver. You can use this to customize
your query and add e.g. sorting, aggregations, queries, paging, etc. Most commonly used features are supported
and anything that isn't supported, you can still add by using the map functionality. For example, this is how
you would do the term query that way:

```kotlin
client.search(indexName) {
  // you can assign maps, lists, primitives, etc.
  this["query"] = mapOf(
    // of course JsonDsl is just a map
    "term" to withJsonDsl {
      // and withJsonDsl is just short for this:
      this[TestDoc::tags.name] = JsonDsl(
        
      ).apply {
        this["value"] = "legumes"
      }
    }
  )
}.ids
```

->

```
[3]
```

## Picking apart the results

Of course a key reason for querying is to get the documents you indexed back and 
deserializing those back to your model classes.

Here is a more complex query that returns fruit with `ban` as the name prefix.

```kotlin
val resp = client.search(indexName) {
  from = 0
  // size is of course also a thing in Map
  resultSize = 100
  // more relevant if you have more than 10k hits
  trackTotalHits = "true" // not always a boolean in the DSL
  // a more complex bool query
  query = bool {
    filter(
      term(TestDoc::tags, "fruit")
    )
    should(
      matchPhrasePrefix(TestDoc::name, "ban")
    )
  }
}
// deserializes all the hits
val hits=resp.parseHits<TestDoc>().map { it.name }

println(hits.joinToString("\n"))

// you can also do something like this:
println(resp.total)
resp.hits?.hits?.forEach { hit ->
  val doc = hit.parseHit<TestDoc>()
  println("${hit.id} - ${hit.score}: ${doc.name} (${doc.price})")
}
```

Captured Output:

```
Banana
Apple
2
2 - 1.0925692: Banana (0.8)
1 - 0.0: Apple (0.5)

```

Note how we are parsing the hits back to TestDoc here. By default, the source
gets deserialized as a `JsonObject`. However, with `kotlinx.serialization`, you can
use that as the input for `decodeFromJsonElement<T>(object)` to deserialize to some custom
data structure. This is something we use in multiple places.


                ---
                | [KT Search Manual](README.md) | Previous: [Indices, Settings, Mappings, and Aliases](IndexManagement.md) | Next: [Text Queries](TextQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |