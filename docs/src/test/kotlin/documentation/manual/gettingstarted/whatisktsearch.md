Kt-search is a Kotlin library that allows users to interact with Opensearch and Elasticsearch. It is a Kotlin multiplatform library that can be used on any platform that Kotlin can compile to. Currently, the jvm and js platforms are supported. 

## Why a new client?

Kt-search is the successor to my popular [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) project.

A few changes in the wider ecosystem of Elasticsearch necessitated a complete rewrite:

- After Elasticsearch decided to change their license, Amazon decided to fork Elasticsearch as Opensearch. More than a year after this happened, there are now a growing number of users that would want to use that.
- The es-kotlin-client depends on Elastic's RestHighLevel client for Java. This client is now deprecated and has been replaced by a new Java client. This client does not work with Opensearch. Nor do later versions of the RestHighLevelClient work with Opensearch. The Opensearch version of the RestHighLevelClient was forked as well and uses different package names and is only suitable for usage with Opensearch.
- Elasticsearch 8 was released. And while mostly backwards compatible, it of course has some new features and compatibility issues with version 7.
- Opensearch is likewise planning a 2.0 release at some point.

In short, there are three variants of search engine that used to be just Elasticsearch which may soon turn into four variants when Opensearch adds their release.

## Kt Search - What's new and what is different

A few years of development on the es-kotlin-client has produced quite a few learnings. Additionally, I added a lot of features over time and gradually introduced things like asynchronous IO using Kotlin co-routines, DSL support, and a few other things.

- Multi-platform and no more Java dependencies.
- But you can plug in custom HTTP and Json parsers and still use your favorite libraries for that on the JVM. However, the default setup uses `ktor-client` for http communication and `kotlinx-serialization` for JSON parsing.
- With Kt-Search, the Kotlin Query DSL stays the same and has moved to its own module. Aside from some package renames and minor feature work, the DSL should be backwards compatible with the one bundled with the es-kotlin client.
- Since building Json DSLs seems like a nice feature to have, I extracted the classes that I used as a basis for the query, mapping, and other DSLs to a separate module.
- All IO is now done using `suspend` functions and co-routines. I've found that I don't use the synchronous calls and doing synchronous IO in Kotlin does not make a lot of sense. Especially not in a multi-platform library as the IO support for multi-platform kotlin is of course asynchronous only.
- A selection of APIs is supported via suspend functions, and you can easily call the rest client to implement calls to APIs not directly supported.
- Functionality similar to the Repository class in the es-kotlin-client is also supported in Kt-Search. However, there were some API changes as we no longer can rely on Elastic's Java response model classes. Where needed, similar classes are provided for Kotlin and those are parsed with `kotlinx-serialization`.