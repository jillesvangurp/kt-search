# Search and Queries 

| [KT Search Manual](README.md) | Previous: [Indices, Settings, Mappings, and Aliases](IndexManagement.md) | Next: [Text Queries](TextQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

Searching is of course the main reason for using Opensearch or Elasticsearch. Kt-search supports this
with a rich Kotlin DSL.

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
deleteIndex(target = indexName, ignoreUnavailable = true)
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
client.search(indexName).ids

```

This returns: `[1, 2, 3]`

The `ids` extension property, extracts a list of ids from the hits in the response.

Of course normally, you'd specify some kind of query. One valid way is to simply pass that as a string.
Kotlin of course has multiline strings that can be templated as well. So, this may be all you need.

With some hand crafted queries, this style of querying may be useful. Another advantage is that 
you can paste queries straight from the Kibana development console.

## Using the SearchDSL

Of course it is nicer to query using a Kotlin Search DSL (Domain Specific Language). 
Here is the same query using the `SearchDSL`.

```kotlin
client.search(indexName) {
  query = term(TestDoc::tags, "legumes")
}.ids
```

This returns: `[3]`



The `client.search` function takes a block that has a `SearchDSL` object as its receiver. You can use this to customize
your query and add e.g. sorting, aggregations, queries, paging, etc.  

The following sections describe (most) of the query dsl:

- [Text Queries](TextQueries.md)
- [Term Level Queries](TermLevelQueries.md)
- [Compound Queries](CompoundQueries.md)
- [Aggregations](Aggregations.md)
- [Deep Paging Using search_after and scroll](DeepPaging.md)

Most commonly used query types are supported
and anything that isn't supported, you can still add by using the map functionality.          
For example, this is how you would construct the term query using the underlying map:

```kotlin
client.search(indexName) {
  // you can assign maps, lists, primitives, etc.
  this["query"] = mapOf(
    // JsonDsl is what the SearchDsl uses as its basis.
    "term" to withJsonDsl {
      // and withJsonDsl is just short for this:
      this[TestDoc::tags.name] = JsonDsl().apply {
        this["value"] = "legumes"
      }
      // the advantage over a map is more flexible put functions
    }
  )
}.ids
```

This returns: `[3]`

For more information on how to extend the DSL or how to create your own DSL see [Extending the Json DSLs](ExtendingTheDSL.md)

## Picking apart the results

Of course a key reason for querying is to get the documents you indexed back and 
deserializing those back to your model classes so that you can do things with them.

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
// deserializes all the hits and extract the name
val names = resp.parseHits<TestDoc>().map { it.name }

println(names.joinToString("\n"))

// you can also parse individual hits:
resp.hits?.hits?.forEach { hit ->
  val doc = hit.parseHit<TestDoc>()
  println("${hit.id} - ${hit.score}: ${doc.name} (${doc.price})")
}
```

This prints:

```text
Banana
Apple
2 - 1.0925692: Banana (0.8)
1 - 0.0: Apple (0.5)
```

By default, the source gets deserialized as a `JsonObject`. However, with `kotlinx.serialization`, you can
use that as the input for `decodeFromJsonElement<T>(object)` to deserialize to some custom
data structure. This is something we use in multiple places and it gives us the flexibility to
be schema less when we need to and use a rich model when want to.

## Searching without the DSL

It's not required to use the DSL and you can also use Kotlin's raw String literals to 
compose a query and use Kotlin's String templating. This is convenient if you are prototyping
your queries in Kibana's dev console as you can simply copy paste the query into your code and
have a working query. 

```kotlin
val term = "legumes"
client.search(
  indexName, rawJson = """
  {
    "query": {
      "term": {
        "tags": {               
          "value":"$term"
        }
      }
    }
  }
""".trimIndent()
).ids
```

This returns: `[3]`

## Count API

Elasticsearch also has a more limited _count API dedicated to simply counting results.

```kotlin
// count all documents
println("Number of docs" + client.count(indexName).count)
// or with a query
println("Number of docs" + client.count(indexName) {
  query = term(TestDoc::tags, "fruit")
}.count)
```

This prints:

```text
Number of docs3
Number of docs2
```

## Multi Search

Kt-search also includes DSL support for doing multi searches. The msearch API has a similar API as the 
bulk API in the sense that it takes a body that has headers and search requests interleaved. Each line
of the body is a json object. Likewise, the response is in ndjson form and contains a search response
for each of the search requests.

The msearch DSL in kt-search makes this very easy to do:

```kotlin
client.msearch(indexName) {
  // the header is optional, this will simply add
  // {} as the header
  add {
    // the full search dsl is supported here
    // will query indexName for everything
    query = matchAll()
  }
  add(msearchHeader {
    // overrides the indexName and queries
    // everything in the cluster
    allowNoIndices = true
    index = "*"
  }) {
    from = 0
    resultSize = 100
    query = matchAll()
  }
}.responses.forEach { searchResponse ->
  // will print document count for both searches
  println("document count ${searchResponse.total}")
}
```

This prints:

```text
document count 3
document count 153
```

Similar to the normal search, you can also construct your body manually. The format is ndjson

```kotlin
val resp = client.msearch(
  body = """
  {"index":"$indexName"}
  {"query":{"match_all":{}}}
  
  """.trimIndent() // the extra new line is required by ES
)
println("Doc counts: ${
  resp.responses.joinToString { it.total.toString() }
}")
```

This prints:

```text
Doc counts: 3
```



---

| [KT Search Manual](README.md) | Previous: [Indices, Settings, Mappings, and Aliases](IndexManagement.md) | Next: [Text Queries](TextQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |