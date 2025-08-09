---
title: KT Search Client
---

# KT Search Client

[![matrix-test-and-deploy-docs](https://github.com/jillesvangurp/kt-search/actions/workflows/deploy-docs-and-test.yml/badge.svg?branch=master)](https://github.com/jillesvangurp/kt-search/actions/workflows/deploy-docs-and-test.yml)

Kt-search is a Kotlin Multi Platform library to search across the Opensearch and Elasticsearch ecosystem on any platform that kotlin can compile to. It provides Kotlin DSLs for querying, defining mappings, bulk indexing, index templates, index life cycle management, index aliases, and much more. The key goal for this library is to provide a best in class developer experience for using Elasticsearch and Opensearch.  

## Why Kt-search?

If you develop software in Kotlin and would like to use Opensearch or Elasticsearch, you have a few choices to make. There are multiple clients to choose from and not all of them work for each version. And then there is Kotlin multiplatform to consider as well. Maybe you are running spring boot on the jvm. Or maybe you are using ktor compiled to native or WASM and using that to run lambda functions.

Kt-search has you covered for all of those. The official Elastic or Opensearch clients are Java clients. You can use them from Kotlin but only on the JVM. And they are not source compatible with each other. The Opensearch client is based on a fork of the old Java client which after the fork was deprecated. On top of that, it uses opensearch specific package names.

Kt-search solves a few important problems here:

 - It's Kotlin! You don't have to deal with all the Java idiomatic stuff that comes with the three Java libraries. You can write pure Kotlin code, use coroutines, and use Kotlin DSLs for everything. Simpler code, easier to debug, etc.
- It's a multiplatform library. We use it on the jvm and in the browser (javascript). Targets for native, IOS, WASM, etc. are also there. So, your Kotlin code should be extremely portable. So, whether you are doing backend development, doing lambda functions, command line tools, mobile apps, or web apps, you can embed kt-search in each of those.
- It doesn't force you to choose between Elasticsearch or Opensearch. Some features are specific to those products and will only work for those platforms but most of the baseline functionality is exactly the same for both.
- It's future proof. Everything is extensible (DSLs) and modular. Even supporting custom plugins that add new features is pretty easy with the `json-dsl` library that is part of kt-search.

## License

This project is [licensed](LICENSE) under the MIT license and will always be. 

## Learn more

- **[Manual](https://jillesvangurp.github.io/kt-search/manual)** - this is generated from the `docs` module. Just like this README.md file. The manual covers most of the extensive feature set of this library. Please provide feedback via the issue tracker if something is not clear to you. Or create a pull request to improve the manual.
- [API Documentation](https://jillesvangurp.github.io/kt-search/api/). Dokka documentation. You can browse it, or access this in your IDE.
- [Release Notes](https://github.com/jillesvangurp/kt-search/releases).
- You can also learn a lot by looking at the integration tests in the `search-client` module.
- There's a [full stack Kotlin demo project](https://github.com/formation-res/kt-fullstack-demo) that we built to show off this library and a few other things.
- The code sample below should help you figure out the basics.

## Use cases

Integrate **advanced search** capabilities in your Kotlin applications. Whether you want to build a web based dashboard, an advanced ETL pipeline or simply expose a search endpoint as a microservice, this library has you covered. 

- Add search functionality to your server applications. Kt-search works great with **Spring Boot**, Ktor, Quarkus, and other popular JVM based servers. Simply create your client as a singleton object and inject it wherever you need search.
- Build complicated ETL functionality using the Bulk indexing DSL.
- Use Kt-search in a **Kotlin-js** based web application to create **dashboards**, or web applications that don't need a separate server. See our [Full Stack at FORMATION](https://github.com/formation-res/kt-fullstack-demo) demo project for an example.
- For dashboards and advanced querying, aggregation support is key and kt-search provides great support for that and makes it really easy to deal with complex nested aggregations.
- Use **Kotlin Scripting** to operate and introspect your cluster. See the companion project [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts/) for more on this as well as the scripting section in the [Manual](https://jillesvangurp.github.io/kt-search/manual/Scripting.html). The companion library combines `kt-search` with `kotlinx-cli` for command line argument parsing and provides some example scripts; all with the minimum of boiler plate.
- Use kt-search from a **Jupyter Notebook** with the Kotlin kernel. See the `jupyter-example` directory for an example and check the [Manual](https://jillesvangurp.github.io/kt-search/manual/Jupyter.html) for instructions.

The goal for kt-search is to be the **most convenient way to use opensearch and elasticsearch from Kotlin** on any platform where Kotlin is usable.

Kt-search is extensible and modular. You can easily add your own custom DSLs for e.g. things not covered by this library or any custom plugins you use. And while it is opinionated about using e.g. kotlinx.serialization, you can also choose to use alternative serialization frameworks, or even use your own http client and just use the search-dsl.

## Gradle

Kt-search is published to the FORMATION maven repository. 

Add the `maven.tryformation.com` repository:

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
And then add the dependency like this:

```kotlin
    // check the latest release tag for the latest version
    implementation("com.jillesvangurp:search-client:2.x.y")
```
Note, several of the search-client dependencies for ktor client are marked as implementation. This means you have to explicitly add those on your side. This is intentional as some people may want to use their own rest client with the kt-search search client.

If you use the KtorRestClient that comes with kt-search you need to add the relevant ktor dependencies for the latest ktor client 3.x:

```kotlin
implementation("io.ktor:ktor-client-core:3.x.y")
implementation("io.ktor:ktor-client-auth:3.x.y")
implementation("io.ktor:ktor-client-logging:3.x.y")
implementation("io.ktor:ktor-client-serialization:3.x.y")
implementation("io.ktor:ktor-client-json:3.x.y")
```

## Maven

If you have maven based kotlin project targeting jvm and can't use kotlin multiplatform dependency, you will need to **append '-jvm' to the artifacts**.

Add the `maven.tryformation.com` repository:

```xml
<repositories>
    <repository>
        <id>try-formation</id>
        <name>kt search repository</name>
        <url>https://maven.tryformation.com/releases</url>
    </repository>
</repositories>
```

And then add dependencies for jvm targets:

```xml
<dependencies>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>search-client-jvm</artifactId>
        <version>2.x.y</version>
    </dependency>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>search-dsls-jvm</artifactId>
        <version>2.x.y</version>
    </dependency>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>json-dsl-jvm</artifactId>
        <version>3.x.y</version>
    </dependency>
</dependencies>
```
**Note:** The `json-dsl` is moved to separate repository. To find the latest version number, check releases: https://github.com/jillesvangurp/json-dsl/releases

## Usage

### Create a client

We start by creating a client: 

```kotlin
val client = SearchClient()
```

Kotlin has default values for parameters. So, we use sensible defaults for the 
`host` and `port` parameters to connect to `localhost` and `9200`. But of 
course you can modify those:

```kotlin
val client = SearchClient(
  KtorRestClient(host="localhost", port=9200)
)
```

If you need to, you can also configure multiple hosts, 
add ssl and basic authentication to connect to managed Opensearch or Elasticsearch clusters. If you use
multiple hosts, you can also configure a strategy for selecting the host to connect to. And of course 
you can completely customize how the client connects. 

For more on 
this, read the [manual](https://jillesvangurp.github.io/kt-search/manual/GettingStarted.html). 

### Documents and data classes

In Kotlin, the preferred way to deal with data would be a data class. This is a simple data class
that we will use as an example below.

```kotlin
@Serializable
data class TestDocument(
  val name: String,
  val tags: List<String>? = null
)
```

In the example below we will use this `TestDocument`, which we can serialize using the 
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) 
framework. 

**Note**, the client provides a lot of flexibility including to your choice of JSON serialization. The only
part of the code dependent on kotlinx serialization is the client module. But for example the Search DSL 
 and the other DSLs are not actually dependent on this.
 
Additionally, if you choose to use the `IndexRepository`, it comes with a ModelSerialization strategy
that abstracts how to parse/serialize your model classes. A kotlinx serialization implementation is included
but that's easily swapped out for something else. So, if you need to use e.g. 
jackson or gson instead, you can do so easily. However, kotlinx serialization is of course the only thing
that works on multiplatform.

### Creating an index
           
Before we can query for `TestDocument` documents, we need to create an index and store some objects:

```kotlin
val indexName = "readme-index"

// create an index and use our mappings dsl
client.createIndex(indexName) {
  settings {
    replicas = 0
    shards = 3
    refreshInterval = 10.seconds
  }
  mappings(dynamicEnabled = false) {
    text(TestDocument::name)
    keyword(TestDocument::tags)
  }
}
```

This creates the index and uses the **mappings and settings DSL**. With this DSL, you can map fields, 
configure analyzers, etc. This is optional of course; you can just call it without the block 
and use the defaults and rely on dynamic mapping. 
You can read more about that [here](https://jillesvangurp.github.io/kt-search/manual/IndexManagement.html) 

### Adding documents

To fill the index with some documents, we need to use bulk indexing operations.

In kt-search this is made very easy with a **Bulk Indexing DSL** that completely abstracts away the book keeping
that you need to do for this in other clients. 

The bulk block below creates a `BulkSession`, which does this for you and flushes
operations to Elasticsearch. You can configure and tailor how this works via parameters
that have sensible defaults. For example the number of operations that is flushed is something
that you'd want to probably configure and error handling is something you can customize as well.

The optional `refresh` parameter uses WaitFor as the default. This means that after the block exits, the documents
will have been indexed and are available for searching. 

```kotlin
client.bulk(
  refresh = Refresh.WaitFor,
  // send operations every 2 ops
  // default and more sensible would be 100
  bulkSize = 2,
) {
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
    // but it has to be a single line in the bulk request
    source =
    """{"name":"banana","tags":["fruit","tropical"]}""",
    index = indexName
  )
}
```

You can read more about 
[bulk operations](https://jillesvangurp.github.io/kt-search/manual/BulkIndexing.html) and how to customize it in the manual.

### Search

Now that we have some documents in an index, we can do some queries:

```kotlin
// search for some fruit
val results = client.search(indexName) {
  // `this` is a SearchDSL instance in the block
  query = bool {
    must(
      term("tags", "fruit"),
      // note how we can also use property references here
      matchPhrasePrefix(TestDocument::name, "app")
    )
  }
}

println("found ${results.total} hits")
results
  // extension function that deserializes
  // the hits using kotlinx.serialization
  .parseHits<TestDocument>()
  .first()
  .let {
    // we feel lucky
    println("doc ${it.name}")
  }
// you can also get the JsonObject if you don't
// have a model class
println(results.hits?.hits?.first()?.source)
```

This prints:

```text
found 1 hits
doc apple
{"name":"apple","tags":["fruit"]}
```

You can also construct complex aggregations with the query DSL.
Aggregation queries are one of the more complex topics in Elasticsearch
and we worked hard to make constructing these programmatically from
Kotlin as easy as possible.

```kotlin
val resp = client.search(indexName) {
  // we don't care about retrieving hits
  resultSize = 0
  agg("by-tag", TermsAgg(TestDocument::tags) {
    // simple terms agg on the tags field
    aggSize = 50
    minDocCount = 1
  })
}
// Despite aggregations JSON being very complicated,
// kt-search makes picking the results apart easy.
resp.aggregations
  .termsResult("by-tag")
  .parsedBuckets.forEach { bucket ->
    println("${bucket.parsed.key}: ${bucket.parsed.docCount}")
  }
```

This prints:

```text
fruit: 3
citrus: 1
tropical: 1
```

These examples show off a few nice features of this library:

- Kotlin DSLs are nice, type safe, and easier to read and write than pure Json. And of course
you get auto completion too. The client includes more DSLs for searching, creating indices and mappings, datastreams, 
index life cycle management, bulk operations, aggregations, and more. All this builds on 
[JsonDSL](https://github.com/jillesvangurp/json-dsl), which is a library we created for easily creating
Kotlin DSLs for existing JSON dialects.
- Where in JSON, you use a lot of String literals, kt-search actually allows you to use
 property references or enum values as well. If you use those, refactoring your data model doesn't 
 break your mappings and queries.
- Kt-search makes complicated features like bulk operations, aggregations, etc. really easy 
to use and accessible. And there is also the `IndexRepository`, which makes it extremely easy
to work with and query documents in a given index or data stream.
- While a Kotlin DSL is nice to have, sometimes it just doesn't have the feature you 
need or maybe you want to work with raw json and use Kotlin's multiline string literals. 
Kt-search allows you to do both and mix schema less with type safe kotlin. You can easily add custom 
properties to the DSL via a simple `put`. All `JsonDsl` are actually mutable maps.  
- Kt-search is designed to be [extensible](https://jillesvangurp.github.io/kt-search/manual/ExtendingTheDSL.html). 
It's easy to use the built in features. And you can easily add your own features. This also
works for plugins or new features that Elasticsearch or Opensearch add.

## Manual

There are of course a lot more features that this library supports. The 
[manual](https://jillesvangurp.github.io/kt-search/manual) covers all of those.

### Introduction

- [What is Kt-Search](https://jillesvangurp.github.io/kt-search/manual/WhatIsKtSearch.html)

- [Getting Started](https://jillesvangurp.github.io/kt-search/manual/GettingStarted.html)

- [Client Configuration](https://jillesvangurp.github.io/kt-search/manual/ClientConfiguration.html)

- [Indices, Settings, Mappings, and Aliases](https://jillesvangurp.github.io/kt-search/manual/IndexManagement.html)

### Search

- [Search and Queries](https://jillesvangurp.github.io/kt-search/manual/Search.html)

- [Text Queries](https://jillesvangurp.github.io/kt-search/manual/TextQueries.html)

- [Term Level Queries](https://jillesvangurp.github.io/kt-search/manual/TermLevelQueries.html)

- [Compound Queries](https://jillesvangurp.github.io/kt-search/manual/CompoundQueries.html)

- [Geo Spatial Queries](https://jillesvangurp.github.io/kt-search/manual/GeoQueries.html)

- [Specialized Queries](https://jillesvangurp.github.io/kt-search/manual/SpecializedQueries.html)

- [Aggregations](https://jillesvangurp.github.io/kt-search/manual/Aggregations.html)

- [Deep Paging Using search_after and scroll](https://jillesvangurp.github.io/kt-search/manual/DeepPaging.html)

- [Join Queries](https://jillesvangurp.github.io/kt-search/manual/JoinQueries.html)

- [Highlighting](https://jillesvangurp.github.io/kt-search/manual/Highlighting.html)

- [Reusing your Query logic](https://jillesvangurp.github.io/kt-search/manual/ReusableSearchQueries.html)

### Indices and Documents

- [Deleting by query](https://jillesvangurp.github.io/kt-search/manual/DeleteByQuery.html)

- [Document Manipulation](https://jillesvangurp.github.io/kt-search/manual/DocumentManipulation.html)

- [Index Repository](https://jillesvangurp.github.io/kt-search/manual/IndexRepository.html)

- [Efficiently Ingest Content Using Bulk Indexing](https://jillesvangurp.github.io/kt-search/manual/BulkIndexing.html)

- [Creating Data Streams](https://jillesvangurp.github.io/kt-search/manual/DataStreams.html)

### Advanced Topics

- [KNN Search](https://jillesvangurp.github.io/kt-search/manual/KnnSearch.html)

- [Extending the Json DSLs](https://jillesvangurp.github.io/kt-search/manual/ExtendingTheDSL.html)

- [Using Kotlin Scripting](https://jillesvangurp.github.io/kt-search/manual/Scripting.html)

- [Jupyter Notebooks](https://jillesvangurp.github.io/kt-search/manual/Jupyter.html)

- [Migrating from the old Es Kotlin Client](https://jillesvangurp.github.io/kt-search/manual/Migrating.html)

## Related projects

There are several libraries that build on kt-search:

- [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts) - this library combines `kt-search` with `kotlinx-cli` to make scripting really easy. Combined with the out of the box support for managing snapshots, creating template mappings, bulk indexing, data-streams, etc. this is the perfect companion to script all your index operations. Additionally, it's a great tool to e.g. query your data, or build some health checks against your production indices.
- [kt-search-logback-appender](https://github.com/jillesvangurp/kt-search-logback-appender) - this is a logback appender that bulk indexes log events straight to elasticsearch. We use this at FORMATION.
- [full stack Kotlin demo project](https://github.com/formation-res/kt-fullstack-demo) A demo project that uses kt-search.
- [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) - version 1 of this client; now no longer maintained.

Additionally, I also maintain a few other search related projects that you might find interesting.

- [Rankquest Studio](https://rankquest.jillesvangurp.com) - A user friendly tool that requires no installation process that helps you build and run test cases to measure search relevance for your search products. Rankquest Studio of course uses kt-search but it is also able to talk directly to your search API and is designed to work with any kind of search api or product that is able to return lists of results.
- [querylight](https://github.com/jillesvangurp/querylight) - Sometimes Elasticsearch/Opensearch is just overkill. Query light is a tiny but capable in memory search engine that you can embed in your kotlin browser, server, or mobile applications. We use it at FORMATION to support e.g. in app icon search. Querylight comes with its own analyzers and query language. 

## Setting up a development environment

Any recent version of Intellij should be able to import this project as is. 
This project uses docker for testing and to avoid having the tests create a 
mess in your existing elasticsearch cluster, it uses a different port than
the default Elasticsearch port.

If you want to save some time while developing, it helps to start docker manually. Otherwise you have to wait for the container to stop and start every time you run a test.

```bash
docker-compose -f docker-compose-es-8.yml up -d
```

For additional details, refer to the build file.

## Compatibility

The integration tests on GitHub Actions use a **matrix build** that tests everything against Elasticsearch 7.x, 8.x & 9.x and Opensearch 1.x, 2.x & 3.x.

It may work fine with earlier Elasticsearch versions as well. But we don't actively test this and the tests are known to not pass with Elasticsearch 6 due to some changes in the mapping dsl. You may be able to work around some of this, however.

There is an annotation that is used to restrict APIs when needed. E.g. `search-after` support was added in Opensearch 2.x but is missing in 1.x:

```kotlin
@VariantRestriction(SearchEngineVariant.ES7,SearchEngineVariant.ES8)
suspend fun SearchClient.searchAfter(target: String, keepAlive: Duration, query: SearchDSL): Pair<SearchResponse,Flow<SearchResponse.Hit>> {
    validateEngine("search_after does not work on OS1",
        SearchEngineVariant.ES7, 
        SearchEngineVariant.ES8,
        SearchEngineVariant.ES9,
        SearchEngineVariant.OS2,
        SearchEngineVariant.OS3,
        )

    // ...
}
```

The annotation is informational only for now. In our tests, we use `onlyon` to prevent tests from
failing on unsupported engines:

```kotlin
onlyOn("doesn't work on Opensearch",
    SearchEngineVariant.ES7,
    SearchEngineVariant.ES8,
    SearchEngineVariant.ES9,
)
```

## Kotlin & Multiplatform compatibility

New releases of this library generally update dependencies to their current versions. There currently is no LTS release of Kotlin. Generally this library should work with recent stable releases of Kotlin. At this point, that means Kotlin 2.0 or higher.

Also, because this is a multiplatform project, you should be aware that several of the platforms are experimental. Because of this, we try to track the latest stable releases of Kotlin. IOS, Linux, Wasm, etc. are expected to generally work but are not something that we actively use ourselves and something that at this point is not stable on the Kotlin side yet. Also, there are some issues with WASM and Karma not playing nice. I've disabled tests for that. The IOS simulator tests are disabled for the same reason.

If you try and find issues, use the issue tracker please.

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                                                    |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `search-dsls`   | DSLs for search and mappings based on `json-dsl`.                                                                                              |
| `search-client` | Multiplatform REST client for Elasticsearch 7.x, 8.x & 9.x and Opensearch 1.x, 2.x & 3.x. This is what you would want to use in your projects. |
| `docs`          | Contains the code that generates the [manual](https://jillesvangurp.github.io/kt-search/manual/) and this readme..                             |

The search client module is the main module of this library. I extracted the json-dsl module and `search-dsls` module with the intention of eventually moving these to separate libraries. Json-dsl is actually useful for pretty much any kind of json dialect and I have a few APIs in mind where I might like to use it. The choice to not impose kotlinx.serialization on json dsl also means that both that and the search dsl are very portable and only depend on the Kotlin standard library.

## Contributing

Pull requests are very welcome! This project runs on community contributions. If you don't like something, suggest changes. Is a feature you need missing from the DSL? Add it. To avoid conflicts or double work, please reach out via the issue tracker for bigger things. I try to be as responsive as I can

Some suggestions of things you could work on:

- Extend the mapping or query DSLs. Our goal is to have coverage of all the common things we and other users need. The extensibility of `JsonDsl` always gives you the option to add whatever is not directly supported by manipulating the underlying map. But creating extension functions that do this properly is not har.
- Add more API support for things in Opensearch/Elasticsearch that are not currently supported. The REST api has dozens of end point other than search. Like the DSL, adding extension functions is easy and using the underlying rest client allows you to customize any requests.
- Work on one of the issues or suggest some new ones.
- Refine the documentation. Add examples. Document missing things.

## Support and Community

Please file issues if you find any or have any suggestions for changes.

Within reason, I can help with simple issues. Beyond that, I offer my services as a consultant as well if you need some more help with getting started or just using Elasticsearch/Opensearch in general with just about any tech stack. I can help with discovery projects, training, architecture analysis, query and mapping optimizations, or just generally help you get the most out of your Elasticsearch/Opensearch setup and your product roadmap.

The best way to reach me is via email if you wish to use my services professionally. Please refer to my [website](https://www.jillesvangurp.com) for that.

I also try to respond quickly to issues. And I also lurk in the amazing [Kotlin](https://kotlinlang.org/community/), [Elastic](https://www.elastic.co/blog/join-our-elastic-stack-workspace-on-slack), and [Search Relevancy](https://opensourceconnections.com/blog/2021/07/06/building-the-search-community-with-relevance-slack/) Slack communities. 

## About this README

This readme is generated using my [kotlin4example](https://github.com/jillesvangurp/kotlin4example) library. I started developing that a few years ago when 
I realized that I was going to have to write a lot of documentation with code examples for kt-search. By now,
both the manual and this readme heavily depend on this and it makes maintaining and adding documentation super easy. 

The way it works is that it provides a dsl for writing markdown that you use to write documentation. It allows you to include runnable code blocks and when it builds the documentation it figures out how to extract those from the kotlin source files and adds them as markdown code snippets. It can also intercept printed output and the return values of the blocks.

If you have projects of your own that need documentation, you might get some value out of using this as well. 



