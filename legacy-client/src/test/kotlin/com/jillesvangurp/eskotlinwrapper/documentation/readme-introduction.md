[![](https://jitpack.io/v/jillesvangurp/es-kotlin-client.svg)](https://jitpack.io/#jillesvangurp/es-kotlin-client)
[![Actions Status](https://github.com/jillesvangurp/es-kotlin-wrapper-client/workflows/CI-gradle-build/badge.svg)](https://github.com/jillesvangurp/es-kotlin-wrapper-client/actions)

The Kotlin Search Client is a Kotlin Multi Platform library for using Elasticsearch and Opensearch.

## Changes in version 2.0

Version 2.0 of the Kotlin Search Client is a major revision of what used to be the Elasticsearch Kotlin Client. As the rename indicates, this client is no longer just about supporting Elasticsearch. It now also supports Opensearch. Opensearch is the fork that Amazon created of Elasticsearch in response to license changes that Elastic made to their product.

At this point, the products are similar but there are some notable differences between them and the projects are diverging:

- Elasticsearch 8.0 is a major release that happened after the fork. While mostly backwards compatible, there are some changes to the API.
- Elastic provided client libraries such as the Rest High Level Java client that was used in previous versions of this client have been modified by Elastic to only work with their product. Older versions from before the fork still work with Opensearch.
- The Rest High Level Client has been deprecated by Elastic in favor of their new Java client.

As can be imagined, this left me with a bit of a challenge on how to move forward with this kotlin client. Over the years, I've added many useful features that in my view are still valid and nice to have. The new Java client by Elasticsearch is nice for Javamulti platform users and probably a big improvement. But it lacks the convenience provided by our library for Kotlin. 

Version 2.0 therefore is a major refactoring and re-write of version 1.x that has several big changes:

- This is now a pure Kotlin library.
- It is by design a kotlin multi platform library. Initially we will support the kotlin-js and kotlin-jvm ecosystems but there should be very little preventing kotlin native support or kotlin wasm support when that compiler is released by Jetbrains
- It provides users choice and flexibility for several things: 
  - Multiple Json serializers/deserializers are supported. Both for objects stored in indices as well as for the query, mapping, and other DSLs. The main focuse of this will initially be on supporting kotlinx-serialization. However, it should also be possible to use e.g. Jackson; or if you use the Rest High Level client, the XContent framework that comes with that.
  - Multiple http transports are supported. Initial focus will be on ktor client. But I'm also considering support for both the Elastic and Opensearch Low Level clients. Doing so will make it easy for users to combine using this client with features from the Rest High Level Client from Elastic or Opensearch.
- Key components have been extracted to separate modules that may eventually become separate projects. The project is structured such that I should be able to create clients for other JSON REST APIs as well. I have a few REST APIs in mind for this.
- The two hardest things in computer science are cache coherence, naming things, and off by 1 errors. A new version is a good opportunity to reconsider some of the unfortunate names I chose for things in version 1.0. So, expect new names for things. New packages where things live.
- Version 2.0 will no longer rely on code generation for the Rest High Level Client to add suspend functions that wrap the java asynchronous methods.
- Instead Version 2.0 will be asynchronous by default and only provide suspending variants of client functionality. Doing otherwise is simply not appropriate for modern Kotlin code in my view.

## Roadmap

A major version of a project like this is a big undertaking. At [FORMATION](https://tryformation.com) we have been early adopters of this client. I have the following milestones in mind for releasing version 2.0

- Q1 2022: Alpha releases. This will be a "good enough" for us release that will allow FORMATION to switch over to the new client.
- Q2 2022: Beta releases. At this stage I will expand the scope of the tests to test current versions of Elasticsearch and Opensearch. Additionally, the documentation will need a major revision. And I expect there may be bugs and issues that emerge.
- H2 2022: Once I'm happy the functionality is stable and good enough, I'll release 2.0 properly. 

Until the release, everything on the version 2.0 branch should be considered experimental and there is no commitment to stability. Expect major renames, refactorings, and API rework. That being said, the end result should feel very familiar to users of version 1.0.

This roadmap is an intention and depending on my work at FORMATION, some of this work may have to wait.

## Contributing to 2.0 and 1.0 maintenance releases

I am the CTO of FORMATION and as such my time is limited. I mostly work on internal feature work. So, you should expect the work on this to happen in bursts. Probably pull requests for 2.0 are not really helpful to me short term. I hope to converge on a proof of concept release soon and a usable alpha soon after.

For version 1.0, unless something is broken, I'm going to be conservative with new releases. As of now, that code base is effectively deprecated. As ever, please communicate before undertaking major work on a pull request.


-----

The Es Kotlin Client provides a friendly Kotlin API on top of the official Elastic Java client.
Elastic's [`HighLevelRestClient`](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high.html) is written in Java and provides access to essentially everything in the REST API that Elasticsearch exposes. This API provides access to the oss and x-pack features. Unfortunately, the Java client is not the easiest thing to work with directly, and it also poorly matches idiomatic Kotlin.

**The Es Kotlin Client takes away none of those powers but adds a lot of power and convenience.**

The underlying java functionality is always there should you need it. But, for most commonly used things, this client provides more Kotlin appropriate ways to access the functionality.

## The new Java API Client introduced in version 7.15

In Elasticsearch 7.15, a new Java API client was added to replace the Java REST High Level Client on which the kotlin library is based. Given that and the recent fork with Amazon's Opensearch, and the coming Elasticsearch 8.0 release.  

For now, the kotlin client will continue to use the deprecated java RestHighLevel client. I'm currently considering several options for the future of this Kotlin client. I was in any case considering to start working on a major release of this library to rebuild it on top of ktor client and kotlinx serialization and gradually cut loose from the Java client.

With Opensearch and Elasticsearch 8 clearly diverging in terms of API compatibility, features, and indeed compatibility with the Java client, compatibility breaking changes are inevitable. So, cutting loose from the Java client seems like it could be the right strategy and would also enable using this kotlin client on kotlin native, kotlin js, or soon kotlin WASM.

Obviously, this is going to be a bit of work and I need to find time to be able to commit to this.

## Features

- Extensible **Kotlin DSLs for Search, MultiSearch, Mappings, Bulk Indexing, and Object CRUD**. These Kotlin Domain Specific Languages (DSL) provide type safe support for commonly used things such as `match` and `bool` queries, easy boilerplate free bulk indexing with error handling, and creating mappings and index settings. At this point, we support most commonly used queries; including all full-text queries, compound queries, and term-level queries.
  - Things that are not explicitly supported are easy to configure by modifying the underlying data model directly using Kotlin's syntactic sugar for working with `Map`.
  - You can also extend the DSL via the `MapBackedProperties` class that backs normal type safe kotlin properties with a `Map` in our DSL. So, anything that's not supported, you can just add yourself. Pull requests are welcome! To get started with this, look at the source code for the existing DSL.  
- Kotlin Extension functions, default argument values, delegate properties, and many other **kotlin features** add lots convenience and gets rid of all the Java specific boilerplate.
- A **repository** abstraction that allows you to represent an Index with a data class: 
  - Manage indices with a flexible DSL for mappings.
  - Serialize/deserialize JSON objects using your favorite serialization framework. A Jackson implementation comes with the client but you can trivially add support for other frameworks. Deserialization support is also available on search results and the bulk API.
  - Do CRUD on json documents with safe updates that retry in case of a version conflict.
  - Bulk indexing DSL to do bulk operations without boiler plate and with fine-grained error handling (via callbacks)
  - Search & count objects in the index using a Kotlin Query DSL or simply use raw json from either a file or a templated multiline kotlin string. Or if you really want, you can use the Java builders that come with the RestHighLevelClient.
  - Much more
- **Co-routine friendly** & ready for reactive usage. We use generated extension functions that we add with a source generation plugin to add cancellable suspend functions for almost all client functions. Additionally, the before mentioned `IndexRepository` has an `AsyncIndexRepository` variant with suspending variants of the same functionality. Where appropriate, we use Kotlin's `Flow` API.
- This means this Kotlin library is currently the most convenient way to use Elasticsearch from e.g. Ktor, Quarkus or Spring Boot if you want to use asynchronous IO. Using the Java client like this library does is of course possible but will end up being very boiler-plate heavy. Additionally, you'll be dealing with e.g. Spring's flux way of doing asynchronous computing.

## Get It

[![](https://jitpack.io/v/jillesvangurp/es-kotlin-client.svg)](https://jitpack.io/#jillesvangurp/es-kotlin-client)

As JFrog is shutting down JCenter, the latest releases are once more available via Jitpack. Add this to your `build.gradke.kts`:

```kotlin
implementation("com.github.jillesvangurp:es-kotlin-client:<version>")
```

You will also need to add the Jitpack repository:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

See [release notes](https://github.com/jillesvangurp/es-kotlin-wrapper-client/releases) with each github release tag for an overview what changed.

## Elasticsearch Java client version

This library is always built with and tested against specific versions of the Elasticsearch Java client (see release notes). Since they sometimes change their Java internal APIs, even between minor versions, it is important to match the version you are using with what the es-kotlin-client was built against. Especially with frameworks like Spring, you may end up with older versions of the java client on your classpath. 

If you see class not found or method not found related exceptions, that is probably what is happening. If, so, double check your dependencies and transitive dependencies and add excludes. Also, be careful using e.g. the spring-dependency-management plugin for this reason.

## Documentation

- [manual](https://www.jillesvangurp.com/es-kotlin-manual/) The manual for this client. I created this using my [kotlin4example](https://github.com/jillesvangurp/kotlin4example) library. So, all the examples in the manual (and the README) are working and correct. The manual covers everything from getting started, doing bulk indexing, working with co-routines, and of course doing searches.
- The same manual as an **[epub](https://www.jillesvangurp.com/es-kotlin-manual/book.epub)**. Very much a work in progress. Please give me feedback on this. I may end up self publishing this at some point.
- [dokka api docs](https://www.jillesvangurp.com/es-kotlin-manual/docs/es-kotlin-wrapper-client/index.html) - API documentation - this gets regenerated for each release and should usually be up to date. But you can always `gradle dokka` yourself.
- Some stand alone examples are included in the examples source folder. This currently includes a small ktor project that implements a recipe search engine.
- The unit and integrations tests cover most of the important features and should be fairly readable and provide a good overview of how to use things.
- [Elasticsearch java client documentation](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high.html) - All of the functionality provided by the java client is supported. All this kotlin wrapper does is add stuff. Elasticsearch has awesome documentation for their client.