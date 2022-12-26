# KT Search Manual 

Kt-search is a Kotlin Multi Platform library to search across the Opensearch and Elasticsearch ecosystem on any platform that kotlin can compile to. It provides Kotlin DSLs for querying, defining mappings, bulk indexing, index templates, index life cycle management, index aliases, etc. 

## Related Links

- [jillesvangurp/kt-search](https://github.com/jillesvangurp/kt-search) - the main Github project for kt-search.
- [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts) - Kotlin scripting support for `kt-search`. Uses `kotlinx-cli` to make scripting really easy and enables you to write your own `.main.kts` files. Combined with the out of the box support for managing snapshots, creating template mappings, bulk indexing, data-streams, etc. this is the perfect companion to script all your index operations. Additionally, it's a great tool to e.g. query your data, or build some health checks against your production indices.
- [kt-search-logback-appender](https://github.com/jillesvangurp/kt-search-logback-appender) - this is a logback appender that bulk indexes log events straight to elasticsearch. Nice if you are using a jvm based project and want to aggregate your logs to elasticsearch/kibana.

## Table of contents

- [What is Kt-Search](WhatIsKtSearch.md)
- [Getting Started](GettingStarted.md)
- [Indices, Settings, Mappings, and Aliases](IndexManagement.md)
- [Search and Queries](Search.md)
- [Text Queries](TextQueries.md)
- [Term Level Queries](TermLevelQueries.md)
- [Compound Queries](CompoundQueries.md)
- [Aggregations](Aggregations.md)
- [Deep Paging Using search_after and scroll](DeepPaging.md)
- [Document Manipulation](DocumentManipulation.md)
- [Index Repository](IndexRepository.md)
- [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md)
- [Creating Data Streams](DataStreams.md)
- [Migrating from the old Es Kotlin Client](Migrating.md)
- [Extending the Json DSLs](ExtendingTheDSL.md)
- [Using Kotlin Scripting](Scripting.md)
- [Jupyter Notebooks](Jupyter.md)

## About this Manual

This manual documents how to use the kotlin search client and all of its features. As a manual like this contains a lot of code samples, I ended up writing a mini framework to allow me to generate markdown from Kotlin.

The project for this is called [Kotlin4Example](https://github.com/jillesvangurp/kotlin4example). Most of the documentation you will find here has correct code samples that get tested and compiled whenever this project is built and whenever something gets refactored that would affect one of the code samples.


