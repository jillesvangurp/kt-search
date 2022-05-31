[![CI-gradle-build](https://github.com/jillesvangurp/kt-search/actions/workflows/gradle.yml/badge.svg)](https://github.com/jillesvangurp/kt-search/actions/workflows/gradle.yml)

KT Search is a kotlin multi-platform library that provides client functionality for Elasticsearch and Opensearch.

It builds on other multi platform libraries such as ktor and kotlinx-serialization. Once finished, this will be the most convenient way to use opensearch and elasticsearch from Kotlin on any platform where Kotlin compiles.

This project started as a full rewrite of my [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) project necessitated by the deprecation of Elastic's RestHighLevelClient and the Opensearch fork created by Amazon. Both Elasticsearch and Opensearch share the same REST API; however their Java clients are increasingly incompatible and in any case awkward to use from Kotlin. Kt-search, is a complete rewrite that removes the dependency on the Java clients entirely. It is also a Kotlin multi-platform library which makes it possible to use Elasticsearch or Opensearch from any platform. Es-kotlin-client lives on as the legacy-client module in this library and shares several of the other modules (e.g. the search dsls). So, you may use this as a migration path to the multi-platform client.

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                                                 |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `json-dsl`      | A generalized abstraction for building your own Kotlin DSLs for JSON dialects; like for example the Elasticsearch query DSL.                |
| `search-dsls`   | DSLs for search and mappings.                                                                                                               |
| `search-client` | Multiplatform REST client for Elasticsearch and Opensearch.                                                                                 |
| `legacy-client` | The legacy client module provides essentially all the functionality of the old client. It utilizes the newly extracted `search-dsl` module. |
