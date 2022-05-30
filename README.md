# KT Search Client 

KT Search is a kotlin multi-platform library that provides client functionality for Elasticsearch and Opensearch.

It builds on other multi platform libraries such as ktor and kotlinx-serialization. Once finished, this will be the most convenient way to use opensearch and elasticsearch from Kotlin on any platform where Kotlin compiles.

This project is currently under development. 

Goals:

- [x] Extract the kotlin DSLs to a multi platform module.
- [x] Rename the project to kt-search and give it its own repository
- [x] Implement a new client using ktor and kotlinx-serialization
- [ ] Port the IndexRepository to the new client
- [x] Port the bulk indexing functionality to the new client
- [ ] Update documentation for the new client
- [ ] Delete the legacy client module from this project once all functionality has been ported
- [ ] Extract an interface for the new client and provide alternate implementations. By extracting the dsl, I've created the possibility to use different JSON serialization and parsing strategies. So potentially we could support alternate http transport and parsing without too much trouble.
  - OkHttp / jackson?
  - Es rest client / jackson? - useful if you are interested in using this library and the Elastic client (either their new Java client or their deprecated RestHighLevelClient )
  - Os rest client / jackson? - useful if you are interested in using the Opensearch forks of the Elastic clients.

Once all these boxes are checked, I'm going to release a beta.

Non Goals:

- Full coverage of what the RestHighLevel client does. If you need it, just add that as a dependency or create your own extension function for `SearchClient`; it's not that hard.
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

KT Search is currently under heavy development. The legacy client module provides essentially all the functionality of the old client. It utilizes the newly extracted `search-dsl` module. However, the core functionality is already quite usable if you feel adventurous.

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. 

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist. I am not planning to do any further maintenance on that, however. People are welcome to fork the project of course.

## Usage

```kotlin
// we'll use a data class with kotlinx.serialization
// you can use whatever json framework for your documents
// of course
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
  // for now ktor client is the only supported client
  // but it's easy to provide alternate transports
  KtorRestClient(
    // our test server runs on port 9999
    nodes = arrayOf(
      Node("localhost", 9999)
    )
  )
  // both SearchClient and KtorRestClient use sensible
  // but overridable defaults for lots of things
)

// we'll generate a random index name
val indexName = "index-${Clock.System.now().toEpochMilliseconds()}"

// most client functions are suspending, so lets use runBlocking
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
  // now let's search using the search DSL
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

For more details, check the tests. A full manual will follow soon.

## Development status

KT Search is currently under heavy development. The legacy client module provides essentially all the functionality of the old client. It utilizes the newly extracted `search-dsl` module. Once all relevant functionality has been implemented, I'll release a 2.0 release (1.x being the legacy client).

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. After that, you may use it as a migration path. 

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist for now. I am not planning to do any further maintenance on that, however. People are welcome to fork that project of course.

The future is going to be about using the pure kotlin multi-platform client. Of course, you may combine that with other clients; including the old RestHighLevel client or even the legacy client.

## Contributing

I'm tracking a few open issues and would appreciate any help of course. But until this stabilizes, reach out to me before doing a lot of work to create a pull request. Check the [here](CONTRIBUTING.md) for mote details.

## About this README

It's generated using the same `kotlin4example` library that I also used for the old client documentation. It's great for including little code snippets that are actually executing as part of the tests and therefore know  correct. Also, I can refactor and have the documentation change as well without breaking.

