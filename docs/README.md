# Manual Index 

KT Search is a kotlin multi-platform library that provides client functionality for Elasticsearch and Opensearch.

It builds on other multi platform libraries such as ktor and kotlinx-serialization.

This project is currently under development. Tasks:

- [x] Extract the kotlin DSLs to a multi platform module.
- [x] Rename the project to kt-search and give it its own repository
- [ ] Implement a new client using ktor and kotlinx-serialization (in progress)
- [ ] Port the IndexRepository to the new client
- [ ] Port the bulk indexing functionality to the new client
- [ ] Update documentation for the new client
- [ ] Delete the legacy client module from this project once all functionality has been ported
- [ ] Extract an interface for the new client and provide alternate implementations. By extracting the dsl, I've created the possibility to use different JSON serialization and parsing strategies. So potentially we could support alternate http transport and parsing without too much trouble.
  - OkHttp / jackson?
  - Es rest client / jackson? - useful if you are interested in using this library and the Elastic client (either their new Java client or their deprecated RestHighLevelClient )
  - Os rest client / jackson? - useful if you are interested in using the Opensearch forks of the Elastic clients.

Non Goals:

- Full coverage of what the RestHighLevel client does. If you need it, just add it as a dependency.
- Cover the entire search DSL. The provided Kotlin DSL is easily extensible, so if it doesn't directly support what you need, you can simply manipulate the underlying Map to add what you need. Alternatively, you can create a pull request to add support for the feature that you want. Currently, most commonly used things in the DSL are already supported.

## Relation to Es-kotlin-client

This project replaces my [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client). Unlike that project, kotlin-search has no dependency on Elastic's RestHighLevel client.

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                                                 |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `json-dsl`      | A generalized abstraction for building your own Kotlin DSLs for JSON dialects; like for example the Elasticsearch query DSL.                |
| `search-dsls`   | DSLs for search and mappings.                                                                                                               |
| `search-client` | Multiplatform REST client for Elasticsearch and Opensearch.                                                                                 |
| `legacy-client` | The legacy client module provides essentially all the functionality of the old client. It utilizes the newly extracted `search-dsl` module. |


## Development status

KT Search is currently under heavy development. The legacy client module provides essentially all the functionality of the old client. It utilizes the newly extracted `search-dsl` module.

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. 

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist. I am not planning to do any further maintenance on that, however. People are welcome to fork the project of course.

## Usage

```kotlin
@Serializable
data class TestDocument(
  val name: String,
  val tags: List<String>? = null
) {
  fun json(pretty: Boolean = false): String {
    return if (pretty)
      DEFAULT_PRETTY_JSON.encodeToString(serializer(), this)
    else
      DEFAULT_JSON.encodeToString(serializer(), this)
  }
}

val client = SearchClient(
  KtorRestClient(
    nodes = arrayOf(
      Node("localhost", 9999)
    )
  )
)

val indexName = "index-${Clock.System.now().toEpochMilliseconds()}"

// most client functions are suspending
runBlocking {
  // create an index and use our mappings dsl
  client.createIndex(indexName) {
    settings {
      replicas = 0
      shards = 3
    }
    mappings(dynamicEnabled = false) {
      text(TestDocument::name)
      keyword(TestDocument::tags)
    }
  }

  // bulk index some documents
  client.bulk(refresh = Refresh.WaitFor) {
    index(
      source = TestDocument(
        name = "apple",
        tags = listOf("fruit")
      ).json(false),
      index = indexName
    )
    index(
      source = TestDocument(
        name = "orange",
        tags = listOf("fruit", "citrus")
      ).json(false),
      index = indexName,
    )
    index(
      source = TestDocument(
        name = "banana",
        tags = listOf("fruit", "tropical")
      ).json(false),
      index = indexName
    )
  }
  client.search(indexName) {
    query = bool {
      must(
        term(TestDocument::tags, "fruit"),
        matchPhrasePrefix(TestDocument::name, "app")
      )
    }
  }.let { results ->
    println("Hits: ${results.total}")
    println(results.hits?.hits?.first()?.source)
  }
}
```

Captured Output:

```
Hits: 1
{"name":"apple","tags":["fruit"]}

```

## Development status

KT Search is currently under heavy development. The legacy client module provides essentially all the functionality of the old client. It utilizes the newly extracted `search-dsl` module.

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. 

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist. I am not planning to do any further maintenance on that, however. People are welcome to fork the project of course.

