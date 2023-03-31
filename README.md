# KT Search Client 

[![matrix-test-and-deploy-docs](https://github.com/jillesvangurp/kt-search/actions/workflows/deploy-docs-and-test.yml/badge.svg?branch=master)](https://github.com/jillesvangurp/kt-search/actions/workflows/deploy-docs-and-test.yml)

Kt-search is a Kotlin Multi Platform library to search across the Opensearch and Elasticsearch ecosystem on any platform that kotlin can compile to. It provides Kotlin DSLs for querying, defining mappings, bulk indexing, index templates, index life cycle management, index aliases, and much more. The key goal for this library is to provide a best in class developer experience for using Elasticsearch and Opensearch.  

## License

This project is [licensed](LICENSE) under the MIT license.

## Learn more

- **[Manual](https://jillesvangurp.github.io/kt-search/manual)** - this is generated from the `docs` module. Just like this README.md file. The manual covers most of the extensive feature set of this library. Please provide feedback via the issue tracker if something is not clear to you.
- [API Documentation](https://jillesvangurp.github.io/kt-search/api/). Dokka documentation.
- [Release Notes](https://github.com/jillesvangurp/kt-search/releases)
- You can also learn a lot by looking at the integration tests in the `search-client` module.
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

Kt-search is extensible and modular. You can easily add your own custom DSLs for e.g. things not covered by this library or custom plugins you use. And while it is opinionated about using e.g. kotlinx.serialization, you can also choose to use alternative serialization frameworks, or even use your own http client and just use the search-dsl.

## Gradle

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

And then the dependency:

```kotlin
    // check the latest release tag for the latest version
    implementation("com.jillesvangurp:search-client:2.x.y")
```

**IMPORTANT** We've switched maven repositories a couple of times now. Recently we switched back from jitpack.io to using our own repository. Jitpack is just too flaky for us to depend on and somehow they keep on having regressions with kotlin multi-platform projects.

**This also means the groupId has changed**. It's now `com.jillesvangurp` instead of `com.github.jillesvangurp.kt-search`.

I of course would like to get this on maven central eventually. However, I've had a really hard time getting that working and am giving up on that for now. The issue seems to be that I always hit some weird and very unspecific error and their documentation + plugins just never seem to quite work as advertised. Multi platform, multi module, and kotlin scripting are three things that tend to make things complicated apparently. If anyone wants to support me with this, please reach out. Otherwise use our private repository for now.

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
      // but it has to be a single line in the bulk request
      source =
      """{"name":"banana","tags":["fruit","tropical"]}""",
      index = indexName
    )
  }

  // search for some fruit
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
    .let {
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

- There is a convenient mapping and settings DSL (Domain Specific Language) that you can use to create 
indices, define analyers, set up mappings, etc..
- In the various DSLs, you can use kotlin property references instead of
field names. This makes it easy to refactor your code without breaking your queries.
- There is a bulk DSL that makes bulk indexing super easy, safe, and fast. The `bulk` block
creates a `BulkSession` for you and it deals with sending bulk requests and picking
the responses apart for error handling.
- You can use kotlinx.serialization for your documents but you don't have to. When using `kt-search` on the
jvm you might want to use alternative json frameworks.
- Everything is extensible. You can use type safe constructs and mix those with schema less json.

There are of course a lot more feature that this library supports. The 
[manual](https://jillesvangurp.github.io/kt-search/manual) covers all of those.

## Related projects

There are several libraries that build on kt-search:

- [jillesvangurp/kt-search](https://github.com/jillesvangurp/kt-search) - the main Github project for kt-search.
- [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts) - this library combines `kt-search` with `kotlinx-cli` to make scripting really easy. Combined with the out of the box support for managing snapshots, creating template mappings, bulk indexing, data-streams, etc. this is the perfect companion to script all your index operations. Additionally, it's a great tool to e.g. query your data, or build some health checks against your production indices.
- [kt-search-logback-appender](https://github.com/jillesvangurp/kt-search-logback-appender) - this is a logback appender that bulk indexes log events straight to elasticsearch.
- [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) - version 1 of this client; now no longer maintained.

## Setting up a development environment

Any recent version of Intellij should be able to import this project as is. 
This project uses docker for testing and to avoid having the tests create a 
mess in your existing elasticsearch cluster, it uses a different port than
the default Elasticsearch port.

If you want to save some time while developing, it helps to start docker manually.

```bash
docker-compose -f docker-compose-es-8.yml up -d
```

For additional details, refer to the build file.

## Compatibility

The integration tests on GitHub Actions use a **matrix build** that tests everything against Elasticsearch 7 & 8 and Opensearch 1 & 2.

It may work fine with earlier Elasticsearch versions as well. But we don't actively test this and the tests are known to not pass with Elasticsearch 6 due to some changes in the mapping dsl. You may be able to work around some of this, however.

There is an annotation that is used to restrict APIs when needed. E.g. `search-after` only works with Elasticsearch and Opensearch 2 and has the following annotation to indicate that:

```kotlin
@VariantRestriction(SearchEngineVariant.ES7,SearchEngineVariant.ES8)
suspend fun SearchClient.searchAfter(target: String, keepAlive: Duration, query: SearchDSL): Pair<SearchResponse,Flow<SearchResponse.Hit>> {
    validateEngine("search_after does not work on OS1",
        SearchEngineVariant.OS2,
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

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                                               |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `json-dsl`      | Kotlin DSL for creating json requests                                                                                                     |
| `search-dsls`   | DSLs for search and mappings based on `json-dsl`.                                                                                         |
| `search-client` | Multiplatform REST client for Elasticsearch 7 & 8 and Opensearch 1.                                                                       |
| `docs`          | Contains the code that generates the [manual](https://jillesvangurp.github.io/kt-search/manual/) and readmes.                             |

The search client module is the main module of this library. I extracted the json-dsl module and `search-dsls` module with the intention of eventually moving these to separate libraries. Json-dsl is actually useful for pretty much any kind of json dialect and I have a few APIs in mind where I might like to use it. The choice to not impose kotlinx.serialization on json dsl also means that both that and the search dsl are really portable and only depend on the Kotlin standard library.

## Contributing

Pull requests are very welcome! Please consider communicating your intentions in advance to avoid conflicts, or redundant work.

Some suggestions of things you could work on:

- Extend the mapping or query DSLs. Our goal is to have coverage of all the common things we and other users need. The extensibility of `JsonDsl` always gives you the option to add whatever is not directly supported by manipulating the underlying map. But creating extension functions that do this properly is not har.
- Add more API support for things in Opensearch/Elasticsearch that are not currently supported. The REST api has dozens of end point other than search. Like the DSL, adding extension functions is easy and using the underlying rest client allows you to customize any requests.
- Work on one of the issues or suggest some new ones.

## Support and Community

Please file issues if you find any or have any reasonable suggestions for changes.

Within reason, I can help with simple issues. Beyond that, I can offer my services as a consultant as well if you need some more help with getting started or just using Elasticsearch/Opensearch in general with just about any tech stack. I can help with discovery projects, trainings, architecture analysis, query and mapping optimizations, or just generally help you get the most out of your search setup and your product roadmap.

You can reach me via the issue tracker and I also lurk in the amazing [Kotlin Slack](https://kotlinlang.org/community/), [Elastic Slack](https://www.elastic.co/blog/join-our-elastic-stack-workspace-on-slack), and [Search Relevancy Slack](https://opensourceconnections.com/blog/2021/07/06/building-the-search-community-with-relevance-slack/) communities. And I have a [website](https://www.jillesvangurp.com) with more contact details.

## About this README

This readme is generated using the `kotlin4example`. I started developing this library a few years ago when 
I realized that I was going to have to write a lot of documentation with code examples for my search library. By now,
both the manual and this readme depend on this.

Kotlin4example is great for including little code snippets that are actually executing as part of the tests and 
therefore known to be correct. Also, I can refactor and have the documentation change as well without breaking. It also has
a few neat features that allow me to capture and show return values of the snippets or to show printed output.

If you have projects of your own that need documentation, you might get some value out of using this. 



