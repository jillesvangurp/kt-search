# KT Search Client 

[![CI-gradle-build](https://github.com/jillesvangurp/kt-search/actions/workflows/gradle.yml/badge.svg)](https://github.com/jillesvangurp/kt-search/actions/workflows/gradle.yml)

Kt-search is a pure Kotlin Multi Platform library to search across the Opensearch and Elasticsearch ecosystem. It includes rich Kotlin DSLs for querying, defining mappings, and more. It relies on all the latest and greatest multi platform Kotlin features that you love: co-routines, kotlinx.serialization, ktor-client 2.x.

Because it is a multi platform library you can embed it in your server (Ktor, Spring Boot, Quarkus), use it in a browser using kotlin-js, or embed it in your Android/IOS apps.

Learn how to integrate advanced search in your Kotlin applications. Whether you are building a web based dashboard, an advanced ETL pipeline, or simply exposing a search endpoint in as a microservice, this library has you covered.


It builds on other multi-platform libraries such as `ktor-client` and `kotlinx-serialization`. The goal for this library is to be the most convenient way to use opensearch and elasticsearch from Kotlin on any platform where Kotlin compiles.

## Gradle

Add the [tryformation](https://tryformation.com) maven repository:

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.tryformation.com/releases") {
        content {
            includeGroup("com.jillesvangurp")
        }
    }
}
```

Then add the latest version:

```kotlin
implementation("com.jillesvangurp:search-client:1.99.2")
```

Note, we may at some point try to push this to maven-central. For now, please use the maven repository above. All the pre-releases will have the `1.99.x` prefix

## License

This project is [licensed](LICENSE) under the MIT license.

## Contributing

Pull requests are very welcome! Please communicate your intentions in advance to avoid conflicts, or redundant work.

Some suggestions:

- Extend the mapping or query DSLs. Our goal is to have coverage of all the common things we and other users need. The extensibility of `JsonDsl` always gives you the option to add whatever is not directly supported by manipulating the underlying map. But creating extension functions that do this properly is not har.
- Add more API support for things in Opensearch/Elasticsearch that are not yet supported. The REST api has dozens of end point other than search. Like the DSL, adding extension functions is easy and using the underlying rest client allows you customize any requests.
- Work on one of the issues or suggest some new ones.

## Documentation

Currently, documentation is still work in progress. I hope to add to finalize this before releasing 2.0.

- [Manual](https://jillesvangurp.github.io/kt-search/manual) - this is generated from the `docs` module. Just like this README.md file.
- [API Documentation](https://jillesvangurp.github.io/kt-search/api/). 
- You can learn a lot by looking at the integration tests in the `search-client` module.
- The code sample below should help you figure out the basics.

## Compatibility

The integration tests on Github Actions use a matrix build that tests everything against Elasticsearch 7 & 8 and Opensearch 1. It should work fine with earlier versions as well. But we don't actively test this. Some features like e.g. `search-after` for deep paging have vendor specific behavior and will throw an error if used with an unsupported search engine. You can use the client for introspecting on the API version of course.

If this matters to you, feel free to create pull requests to address compatibility issues or to make our tests run against e.g. v5 and v6 of Elasticsearch. I suspect most features should just work with some exceptions. 

There is an annotation I use to restrict APIs when needed. E.g. `search-after` only works with Elasticsearch currently and has the following annotation:

```kotlin
@VariantRestriction(SearchEngineVariant.ES7,SearchEngineVariant.ES8)
suspend fun SearchClient.searchAfter(target: String, keepAlive: Duration, query: SearchDSL): Pair<SearchResponse,Flow<SearchResponse.Hit>> {
    validateEngine("search_after works differently on Opensearch.", 
        SearchEngineVariant.ES7, 
        SearchEngineVariant.ES8)

    // ...
}

```

The annotation is informational only for now.

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                                               |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `json-dsl`      | Kotlin DSL for creating json requests                                                                                                     |
| `search-dsls`   | DSLs for search and mappings based on `json-dsl`.                                                                                         |
| `search-client` | Multiplatform REST client for Elasticsearch 7 & 8 and Opensearch 1.                                                                       |
| `docs`          | Contains the code that generates the [manual](https://jillesvangurp.github.io/kt-search/manual/) and readmes.                             |
| `legacy-client` | The old v1 client with some changes to integrate the `search-dsls`. If you were using that, you may use this to migrate to the new client |

The search client module is the main module of this library. I extracted the json-dsl module and search-dsls module with the intention of eventually moving these to separate libraries. Json-dsl is actually useful for pretty much any kind of json dialect and I have a few APIs in mind where I might like to use it. 

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. After that, you may use it as a migration path.

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist for now. I am not planning to do any further maintenance on that, however. People are welcome to fork that project of course. But in terms of the way it works it is a dead end.

## History of the project

Before kt-search, I actually built various Java http clients for older versions of Elasticsearch. So, this project builds on 10 years of using and working with Elasticsearch. At Inbot, we used our in house client for several years with Elasticsearch 1.x. I actually built an open source client for version 2.0 but we never upgraded to that version as version 5 was released and broke compatibility. Later, I wrote another client on a customer project for version 5.0. This was before the Elastic's RestHighLevel client was finalized. Finally, Kt-search is  a full rewrite of my [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) project, which I have maintained and used in various projects for the last three years. It has a modestly large group of users.

The rewrite in `kt-search` 2.0 was necessitated by the deprecation of Elastic's RestHighLevelClient and the Opensearch fork of Elasticsearc created by Amazon. One of the things they forked is this deprecated client. Except of course they changed all the package names, which makes supporting both impossible.

However, Elasticsearch and Opensearch still share the same REST API with only very minor variations mostly related to advanced features. For most common uses they are identical products. 

So, Kt-search, removes the dependency on the Java client entirely. This in turn makes it possible to use all the wonderful new libraries in the Kotlin ecosystem. Therefore, it is also a Kotlin multi-platform library. This is a feature we get for free simply by using what is there. Kotlin-multi platform makes it possible to use Elasticsearch or Opensearch on any platform where you can compile this library. 

Currently, that includes the **jvm** and **kotlin-js** compilers. However, it should be straightforward to compile this for e.g. IOS or linux as well using the **kotlin-native** compiler. I just lack a project to test this properly. And, I'm looking forward to also supporting the Kotlin WASM compiler, which is currently being developed and available as an experimental part of Kotlin 1.7.x. 

Whether it is practical or not, you can use this client in Spring servers, Ktor servers, AWS lambda functions, node-js servers, web applications running in a browser, or native applications running on IOS and Android. I expect, people will mostly stick to using servers on the JVM, at least short term. But I have some uses in mind for building small dashboard UIs as web applications as well. Let me know what you do with this!

Es-kotlin-client, aka. kt-search 1.0 lives on as the legacy-client module in this library and shares several of the other modules (e.g. the search dsls). So, you may use this as a migration path to the multi-platform client. But in terms of how you use the client, the transition from the legacy client to this should be pretty straight-forward.







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

KT Search is currently still under development. I'll release a 2.0 release (1.x being the legacy client) once I'm happy the functionality and documentation are complete enough. Part of this is internally upgrading the FORMATION backend to use my new client. I will likely discover some mistakes, bugs, and missing features as I do this.

Currently, the client is essentially feature complete. I have so far not found any show stopping issues. The 1.99.x series can be seen as a series of increasingly better release candidates/betas. If you do run into issues, please create an issue. Likewise, if there is important functionality that you need.

## Goals/todo's:

For more detail refer to the issue tracker. This is merely my high level plan for getting to a 2.0 release.

- [x] Extract the kotlin DSLs to a multi platform module.
- [x] Rename the project to kt-search and give it its own repository
- [x] Implement a new client using ktor and kotlinx-serialization
- [x] Port the IndexRepository to the new client
- [x] Port the bulk indexing functionality to the new client
- [ ] Update documentation for the new client
been ported
- [x] Extract an interface for the new client and provide alternate implementations. By extracting the dsl, I've created the possibility to use different JSON serialization and parsing strategies. So potentially we could support alternate http transport and parsing without too much trouble.
  - OkHttp / jackson?
  - Es rest client / jackson? - useful if you are interested in using this library and the Elastic client (either their new Java client or their deprecated RestHighLevelClient )
  - Os rest client / jackson? - useful if you are interested in using the Opensearch forks of the Elastic clients.

Currently, the client is kind of feature complete but needs more testing and documentation. The testing will come as part of a project to upgrade a few of my private projects to use this client. Once that is completed, I will tag and release version 2.0. Until then, the 1.99.x series of releases act as beta releases.

Non Goals:

- Full coverage of what the RestHighLevel client used to do. If you need it, just add that as a dependency or create your own extension function for `SearchClient`; it's not that hard. And of course consider creating a pull request if you do.
- Cover the entire search DSL. The provided Kotlin DSL is very easy to extend by design. If it doesn't directly support what you need, you can simply manipulate the underlying Map to add what you need. Alternatively, you can create a pull request to add proper type safe support for the feature that you want. Currently, most commonly used things in the DSL are already supported. We'll likely expand the features over time.

## Contributing

I'm tracking a few open issues and would appreciate any help of course. But until this stabilizes, reach out to me before doing a lot of work to create a pull request. Check the [here](CONTRIBUTING.md) for mote details.

## Help, support, and consulting

Within reason, feel free to reach out via the issue tracker or other channels if you need help with this client. 

For bigger things, you may also want to consider engaging me as a consultant to help you resolve issues with your Elastic / Opensearch setup, optimizing your queries, etc. Please refer to my [website](https://www.jillesvangurp.com)

## About this README

It's generated using the same `kotlin4example` library that I also used for the old client documentation. It's great for including little code snippets that are actually executing as part of the tests and therefore know  correct. Also, I can refactor and have the documentation change as well without breaking.

