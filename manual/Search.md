# Search and Queries 

Searching is of course the main reason for using Opensearch and Elasticsearch. Kt-search supports this
with a rich Kotlin DSL. However, you can also use string literals to search.

## Some test documents

Let's quickly create some documents to search through.

```kotlin
@Serializable
data class TestDoc(val name: String, val tags: List<String> = listOf())

val indexName = "docs-search-demo"
// re-create the index
client.deleteIndex(indexName)
client.createIndex(indexName) {
  mappings { text(TestDoc::name) }
  mappings { keyword(TestDoc::tags) }
}

val docs = listOf(
  TestDoc(
    id = "1",
    name = "Apple",
    tags = listOf("fruit")
  ),
  TestDoc(
    id = "2",
    name = "Banana",
    tags = listOf("fruit")
  ),
  TestDoc(
    id = "3",
    name = "Beans",
    tags = listOf("legumes")
  )
)
docs.forEach { d ->
  client.indexDocument(
    target=indexName,
    document = d,
    id = d.id,
    refresh = Refresh.WaitFor
  )
}
```

This creates a simple index with a custom mapping and adds some documents using our API.

You can learn more about creating indices with customized mappings here: [Indices, settings, mappings, and aliases](IndexManagement.md)

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

Of course normally, you'd specify some kind of query. One way is to simply pass that as a string.

```kotlin
val term="legumes"
client.search(indexName, rawJson = """
  {
    "query": {
      "term": {
        "tags": {
          "value":"$term"
        }
      }
    }
  }
""".trimIndent()).ids
```

->

```
[3]
```

Note how we are using templated strings here. With some hand crafted queries, this style of querying may be very useful.

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
  this["query"] =  mapOf(
    // of course JsonDsl is just a map
    "term" to withJsonDsl {
      // and withJsonDsl is just short for this:
      this[TestDoc::tags.name] = JsonDsl(
        namingConvention = ConvertToSnakeCase
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

## Supported queries

Currently the following queries are supported:

- all term level queries (term, terms, regex, etc.)
- all full text queries (match, match_phrase_prefix, multi-match, etc.)
- all compound queries (bool, boosting, dismax, etc.)
- nested queries
- some aggregation queries (`terms`)

Adding more queries to the DSL is easy and we welcome pull requests for this.

## Parsing the results

Of course a key reason for querying is to get the documents you indexed back and deserialized.

Here is a more complex query that returns fruit with `ban` as the name prefix.

```kotlin
client.search(indexName) {
  from = 0
  // size is of course also a thing in Map
  resultSize = 100
  // more relevant if you have more than 10k hits
  trackTotalHits = "true" // not always a boolean in the DSL
  // a more complex query
  query = bool {
    filter(
      term(TestDoc::tags, "fruit")
    )
    should(
      matchPhrasePrefix(TestDoc::name, "ban")
    )
  }
}.parseHits<TestDoc>().map { it?.name }
```

->

```
[Banana, Apple]
```

Note how we are parsing the hits back to TestDoc here. By default, the source
gets deserialized as a `JsonObject`. However, with `kotlinx.serialization`, you can
use that as the input for `decodeFromJsonElement<T>(object)` to deserialize to some custom
data structure. This is something we use in multiple places.

It uses some sane defaults for parsing that you can learn more about here: [Getting Started](GettingStarted.md)

