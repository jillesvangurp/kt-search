# KT Search Client 

[![matrix-test-and-deploy-docs](https://github.com/jillesvangurp/kt-search/actions/workflows/deploy-docs-and-test.yml/badge.svg?branch=master)](https://github.com/jillesvangurp/kt-search/actions/workflows/deploy-docs-and-test.yml)

Kt-search is a Kotlin Multi Platform library to search across the Opensearch and Elasticsearch ecosystem on any platform that kotlin can compile to. It provides Kotlin DSLs for querying, defining mappings, bulk indexing, index templates, index life cycle management, index aliases, etc. 

Integrate **advanced search** capabilities in your Kotlin applications. Whether you want to build a web based dashboard, an advanced ETL pipeline or simply exposing a search endpoint in as a microservice, this library has you covered. You can also integrate kt-search into your `kts` scripts. For this we have a little companion library to get you started: [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts/). Also, see the scripting section in the manual.

Because it is a multi platform library you can embed it in your server (Ktor, Spring Boot, Quarkus), use it in a browser using kotlin-js, or embed it in your Android/IOS apps. For this, it relies on all the latest and greatest multi-platform Kotlin features that you love: co-routines, kotlinx.serialization, ktor-client 2.x., etc., which work across all these platforms.

The goal for kt-search is to be the **most convenient way to use opensearch and elasticsearch from Kotlin** on any platform where Kotlin compiles.  
      
Kt-search is extensible and modular. You can easily add your own custom DSLs for e.g. things not covered by this library or custom plugins you use. And while it is opinionated about using e.g. kotlinx.serialization, you can also choose to use alternative serialization frameworks, or even use your own http client and just use the search-dsl.

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
implementation("com.jillesvangurp:search-client:1.99.x")
```

Check the [releases](https://github.com/jillesvangurp/kt-search/releases) page for the latest release tag.

Note, we may at some point try to push this to maven-central. For now, please use the maven repository above. All the pre-releases will have the `1.99.x` prefix. Despite this, the project can at this point be considered stable, feature complete, and usable. I'm holding off on labeling this as a 2.0 until I've had a chance to use it in anger on my own projects. Until then, some API refinement may happen once in a while. I will try to minimize breakage between releases.

## Use cases

There are many ways you can use kt-search

- Add search functionality to your servers. Kt-search works great with Spring Boot, Kto, Quarkus, and other popular JVM based servers. Simply create your client as a singleton object and inject it wherever you need search.
- Use Kt-search in a Kotlin-js based web application to create dashboards, or web applications that don't need a separate server. See our [Full Stack at FORMATION](https://github.com/formation-res/kt-fullstack-demo) demo project for an example.
- Use Kotlin Scripting to operate and introspect your cluster. See the companion project [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts/) for more on this as well as the scripting section in the manual. The companion library combines `kt-search` with `kotlinx-cli` for command line argument parsing and provides some example scripts; all with the minimum of boiler plate.

## Learn more

- [Release Notes](https://github.com/jillesvangurp/kt-search/releases)
- [Manual](https://jillesvangurp.github.io/kt-search/manual) - this is generated from the `docs` module. Just like this README.md file. The manual covers most of the extensive feature set of this library. Please provide feedback via the issue tracker if something is not clear to you.
- [API Documentation](https://jillesvangurp.github.io/kt-search/api/). Dokka documentation.
- You can also learn a lot by looking at the integration tests in the `search-client` module.
- The code sample below should help you figure out the basics.






## Usage

```kotlin
val client = SearchClient(
  KtorRestClient()
)
```

First create a client. Kotlin has default values for parameters. So, we use sensible defaults for the 
`host` and `port` variables to connect to `localhohst` and `9200`. You can also configure multiple hosts, 
or add ssl and basic authentication to connect to managed Opensearch or Elasticsearch clusters. If you use
multiple hosts, you can also configure a strategy for selecting the host to connect to.

```kotlin
@Serializable
data class TestDocument(
  val name: String,
  val tags: List<String>? = null
)
```

In the example below we will use this `TestDocument`, which we can serialize using the kotlinx.serialization 
framework. You can also pass in your own serialized json in requests, so if you want to use e.g. jackson or gson,
you can do so easily.

```kotlin
//
val indexName = "index-${Clock.System.now().toEpochMilliseconds()}"

// create a co-routine context, kt-search uses `suspend` functions
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
  // using the bulk DSL and a BulkSession
  // WaitFor ensures we can query for the documents
  client.bulk(refresh = Refresh.WaitFor) {
    index(
      doc = TestDocument(
        name = "apple",
        tags = listOf("fruit")
      ),
      index = indexName
    )
    index(
      doc = TestDocument(
        name = "orange",
        tags = listOf("fruit", "citrus")
      ),
      index = indexName,
    )
    index(
      // you can also provide raw json
      source = DEFAULT_JSON.encodeToString(
        TestDocument.serializer(),
        TestDocument(
          name = "banana",
          tags = listOf("fruit", "tropical")
        )),
      index = indexName
    )
  }

  // search
  val results = client.search(indexName) {
    query = bool {
      must(
        // note how we use property references here
        term(TestDocument::tags, "fruit"),
        matchPhrasePrefix(TestDocument::name, "app")
      )
    }
  }

  println("found ${results.total} hits")
  results
    // extension function that deserializes
    // uses kotlinx.serialization
    .parseHits<TestDocument>()
    .first()
    // hits don't always include source
    // in that case it will be a null document
    ?.let {
      println("doc ${it.name}")
    }
  // you can also get the JsonObject if you don't
  // have a model class
  println(results.hits?.hits?.first()?.source)
}
```

Captured Output:

```
found 1 hits
doc apple
{"name":"apple","tags":["fruit"]}

```

This example shows off a few nice features of this library:

- There is a convenient mapping and settings DSL (Domain Specific Language) that you can use to create indices.
- In te mappings and in your queries, you can use kotlin property references instead of
field names.
- Bulk indexing does not require any bookkeeping with kt-search because we have bulk DSL. The `bulk` block
creates a `BulkSession` for you and it deals with sending bulk requests and picking
the responses apart for error handling. BulkSession has a lot of optional featured that you can use: 
it has item callbacks, you can specify the refresh parameter, you can make it 
fail on the first item failure, etc. Alternatively, you can make it robust against
failures, implement error handling and retries, etc.
- You can use kotlinx.serialization for your documents but you don't have to. When using `kt-search` on the
jvm you might want to use alternative json frameworks.

For more details, refer to the manual.

## Related projects

There are several libraries that build on kt-search that may be of interest to you.

- [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts) - this library combines `kt-search` with `kotlinx-cli` to make scripting really easy. Combined with the out of the box support for managing snapshots, creating template mappings, bulk indexing, data-streams, etc. this is the perfect companion to script all your index operations. Additionally, it's a great tool to e.g. query your data, or build some health checks against your production indices.
- [kt-search-logback-appender](https://github.com/jillesvangurp/kt-search-logback-appender) - this is a logback appender that bulk indexes log events straight to elasticsearch.

## License

This project is [licensed](LICENSE) under the MIT license.

## Compatibility

The integration tests on GitHub Actions use a **matrix build** that tests everything against Elasticsearch 7 & 8 and Opensearch 1 & 2.

It may work fine with earlier Elasticsearch versions as well. But we don't actively test this and are tests are known to not pass with Elasticsearch 6 due to some changes in the mapping dsl. You may be able to work around this, however.

Some features like e.g. `search-after` for deep paging have vendor specific behavior and will throw an error if used with an unsupported search engine (Opensearch 1.x). You can use the client for introspecting on the API version of course.

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

The annotation is informational only for now. In our tests, we use `onlyon` to prevent tests from
failing on unsupported engines For example, this is added to the test for `search_after`:

```kotlin
onlyOn("opensearch implemented search_after with v2",
    SearchEngineVariant.OS2,
    SearchEngineVariant.ES7,
    SearchEngineVariant.ES8)
```

Should you want to test a specific version of opensearch or elasticsearch, all you need to do is run it on port 9999
and then run `./gradlew :search-client:build` to run the tests.

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                                               |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `json-dsl`      | Kotlin DSL for creating json requests                                                                                                     |
| `search-dsls`   | DSLs for search and mappings based on `json-dsl`.                                                                                         |
| `search-client` | Multiplatform REST client for Elasticsearch 7 & 8 and Opensearch 1.                                                                       |
| `docs`          | Contains the code that generates the [manual](https://jillesvangurp.github.io/kt-search/manual/) and readmes.                             |
| `legacy-client` | The old v1 client with some changes to integrate the `search-dsls`. If you were using that, you may use this to migrate to the new client |

The search client module is the main module of this library. I extracted the json-dsl module and `search-dsls` module with the intention of eventually moving these to separate libraries. Json-dsl is actually useful for pretty much any kind of json dialect and I have a few APIs in mind where I might like to use it.

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. After that, you may use it as a migration path.

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist for now. I am not planning to do any further maintenance on that, however. People are welcome to fork that project of course. But in terms of the way it works it is a dead end.

## History of the project

Before kt-search, I actually built various Java http clients for older versions of Elasticsearch. So, this project builds on 10 years of using and working with Elasticsearch. At Inbot, we used our in house client for several years with Elasticsearch 1.x. I actually built an open source client for version 2.0, but we never upgraded to that version as version 5 was released and broke compatibility. Later, I wrote another client on a customer project for version 5.0. This was before the Elastic's RestHighLevel client was finalized. Finally, Kt-search is  a full rewrite of my [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) project, which I have maintained and used in various projects for the last three years. It has a modestly large group of users.

The rewrite in `kt-search` 2.0 was necessitated by the deprecation of Elastic's RestHighLevelClient and the Opensearch fork of Elasticsearch created by Amazon. One of the things they forked is this deprecated client. Except of course they changed all the package names, which makes supporting both impossible.

However, Elasticsearch and Opensearch still share the same REST API with only very minor variations mostly related to advanced features. For most common uses they are identical products.

So, Kt-search, removes the dependency on the Java client entirely. This in turn makes it possible to use all the wonderful new libraries in the Kotlin ecosystem. Therefore, it is also a Kotlin multi-platform library. This is a feature we get for free simply by using what is there. Kotlin-multi platform makes it possible to use Elasticsearch or Opensearch on any platform where you can compile this library.

Currently, that includes the **jvm** and **kotlin-js** compilers. However, it should be straightforward to compile this for e.g. IOS or linux as well using the **kotlin-native** compiler. I just lack a project to test this properly. And, I'm looking forward to also supporting the Kotlin WASM compiler, which is currently being developed and available as an experimental part of Kotlin 1.7.x.

Whether it is practical or not, you can use this client in Spring servers, Ktor servers, AWS lambda functions, node-js servers, web applications running in a browser, or native applications running on IOS and Android. I expect, people will mostly stick to using servers on the JVM, at least short term. But I have some uses in mind for building small dashboard UIs as web applications as well. Let me know what you do with this!

Es-kotlin-client, aka. kt-search 1.0 lives on as the legacy-client module in this library and shares several of the other modules (e.g. `search-dsls`). So, you may use this as a migration path to the multi-platform client. But in terms of how you use the client, the transition from the legacy client to this should be pretty straight-forward. Currently, you have to build this yourself. For more tips on migrating to the new version see the manual's migration section.

## Contributing

Pull requests are very welcome! Please communicate your intentions in advance to avoid conflicts, or redundant work.

Some suggestions:

- Extend the mapping or query DSLs. Our goal is to have coverage of all the common things we and other users need. The extensibility of `JsonDsl` always gives you the option to add whatever is not directly supported by manipulating the underlying map. But creating extension functions that do this properly is not har.
- Add more API support for things in Opensearch/Elasticsearch that are not yet supported. The REST api has dozens of end point other than search. Like the DSL, adding extension functions is easy and using the underlying rest client allows you to customize any requests.
- Work on one of the issues or suggest some new ones.

## Help, support, and consulting

Within reason, feel free to reach out via the issue tracker or other channels if you need help with this client. 

For bigger things, you may also want to consider engaging me as a consultant to help you resolve issues with your Elastic / Opensearch setup, optimizing your queries, etc. Please refer to my [website](https://www.jillesvangurp.com)

## About this README

It's generated using the same `kotlin4example` library that I also used for the old client documentation. It's great for including little code snippets that are actually executing as part of the tests and therefore know  correct. Also, I can refactor and have the documentation change as well without breaking.

