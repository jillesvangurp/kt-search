# Searching 

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
client.search(indexName, rawJson = """
  {
    "query": {
      "term": {
        "tags": {
          "value":"legumes"
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

## Using the SearchDSL

Of course it is much nicer to query using the Kotlin Search DSL. Here is the same query using the SearchDSL

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
      this[TestDoc::tags.name] = JsonDsl().apply {
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
- some aggregation queries

Adding more queries to the DSL is easy and we welcome pull requests for this.

```kotlin
client.search(indexName) {
  from=0
  // size is of course also a thing in Map
  resultSize=100
  // more relevant if you have more than 10k hits
  trackTotalHits = "true" // not always a boolean in the DSL
  // a more complex query
  query = bool {
    filter(
      term(TestDoc::tags,"fruit")
    )
    should(
      matchPhrasePrefix(TestDoc::name, "ban")
    )
  }
}.parseHits<TestDoc>().map { it.name }
```

->

```
[Banana, Apple]
```

Note how we are parsing the hits back to TestDoc here

