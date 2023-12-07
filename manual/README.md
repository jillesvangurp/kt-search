# KT Search Manual 

Kt-search is a Kotlin Multi Platform library to search across the Opensearch and Elasticsearch ecosystem on any platform that kotlin can compile to. It provides Kotlin DSLs for querying, defining mappings, bulk indexing, index templates, index life cycle management, index aliases, and much more. The key goal for this library is to provide a best in class developer experience for using Elasticsearch and Opensearch.  

## Table of contents

### Introduction

[What is Kt-Search](WhatIsKtSearch.md)

[Getting Started](GettingStarted.md)

[Client Configuration](ClientConfiguration.md)

[Indices, Settings, Mappings, and Aliases](IndexManagement.md)

### Search

[Search and Queries](Search.md)

[Text Queries](TextQueries.md)

[Term Level Queries](TermLevelQueries.md)

[Compound Queries](CompoundQueries.md)

[Geo Spatial Queries](GeoQueries.md)

[Specialized Queries](SpecializedQueries.md)

[Aggregations](Aggregations.md)

[Deep Paging Using search_after and scroll](DeepPaging.md)

### Indices and Documents

[Deleting by query](DeleteByQuery.md)

[Document Manipulation](DocumentManipulation.md)

[Index Repository](IndexRepository.md)

[Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md)

[Creating Data Streams](DataStreams.md)

### Advanced Topics

[KNN Search](KnnSearch.md)

[Extending the Json DSLs](ExtendingTheDSL.md)

[Using Kotlin Scripting](Scripting.md)

[Jupyter Notebooks](Jupyter.md)

[Migrating from the old Es Kotlin Client](Migrating.md)

## About this Manual

This manual documents how to use the kotlin search client and all of its features. As a manual like this contains a lot of code samples, I ended up writing a mini framework to allow me to generate markdown from Kotlin.

The project for this is called [Kotlin4Example](https://github.com/jillesvangurp/kotlin4example). Most of the documentation you will find here has correct code samples that get tested and compiled whenever this project is built and whenever something gets refactored that would affect one of the code samples.


## Related projects

There are several libraries that build on kt-search:

- [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts) - this library combines `kt-search` with `kotlinx-cli` to make scripting really easy. Combined with the out of the box support for managing snapshots, creating template mappings, bulk indexing, data-streams, etc. this is the perfect companion to script all your index operations. Additionally, it's a great tool to e.g. query your data, or build some health checks against your production indices.
- [kt-search-logback-appender](https://github.com/jillesvangurp/kt-search-logback-appender) - this is a logback appender that bulk indexes log events straight to elasticsearch. We use this at FORMATION.
- [full stack Kotlin demo project](https://github.com/formation-res/kt-fullstack-demo) A demo project that uses kt-search.
- [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) - version 1 of this client; now no longer maintained.

Additionally, I also maintain a few other search related projects that you might find interesting.

- [Rankquest Studio](https://rankquest.jillesvangurp.com) - A user friendly tool that requires no installation process that helps you build and run test cases to measure search relevance for your search products. Rankquest Studio of course uses kt-search but it is also able to talk directly to your API and is designed to work with any kind of search api or product that is able to return lists of results.
- [querylight](https://github.com/jillesvangurp/querylight) - Sometimes Elasticsearch is just overkill. Query light is a tiny in memory search engine that you can embed in your kotlin browser, server, or mobile applications. We use it at FORMATION to support e.g. in app icon search. Querylight comes with its own analyzers and query language. 

