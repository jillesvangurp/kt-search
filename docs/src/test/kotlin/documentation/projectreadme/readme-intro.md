[![CI-gradle-build](https://github.com/jillesvangurp/kt-search/actions/workflows/gradle.yml/badge.svg)](https://github.com/jillesvangurp/kt-search/actions/workflows/gradle.yml)

KT Search is a kotlin multi-platform library that provides client functionality for Elasticsearch and Opensearch.

It builds on other multi platform libraries such as ktor and kotlinx-serialization. Once finished, this will be the most convenient way to use opensearch and elasticsearch from Kotlin on any platform where Kotlin compiles.

## Gradle

Add the tryformation maven repository:

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
implementation("com.jillesvangurp:search-client:1.99.0")
```

Note, we may at some point try to push this to maven-central. For now, please use the maven repository above.

## License

This project is [licensed](LICENSE) under the MIT license.

## Contributing

Pull requests are welcome but please communicate your intentions in advance to avoid conflicts, or redundant work.

Some suggestions:

- Extend the mapping or query DSLs. Our goal is to have coverage of most common things. The extensibility of `JsonDsl` always gives you the option to add whatever is not directly supported.
- Add more API support for things in Opensearch/Elasticsearch that are not yet supported
- Work on one of the issues

## Documentation

Currently, documentation is still work in progress. I hope to add to this before releasing 2.0.

- [Manual](https://jillesvangurp.github.io/kt-search/manual)
- [API Documentation](https://jillesvangurp.github.io/kt-search/api/)
- You can learn a lot by looking at the integration tests in the `search-client` module.
- The code sample below should help you figure out the basics.

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                   |
|-----------------|---------------------------------------------------------------------------------------------------------------|
| `json-dsl`      | Kotlin DSL for creating json requests                                                                         |
| `search-dsls`   | DSLs for search and mappings based on `json-dsl`.                                                             |
| `search-client` | Multiplatform REST client for Elasticsearch 7 & 8 and Opensearch.                                             |
| `docs`          | Contains the code that generates the [manual](https://jillesvangurp.github.io/kt-search/manual/) and readmes. |
| `legacy-client` | The old v1 client with some changes to integrate the `search-dsls`                                            |

## History of the project

This project started as a full rewrite of my [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) project necessitated by the deprecation of Elastic's RestHighLevelClient and the Opensearch fork created by Amazon. Both Elasticsearch and Opensearch share the same REST API; however their Java clients are increasingly incompatible and in any case awkward to use from Kotlin. Kt-search, is a complete rewrite that removes the dependency on the Java clients entirely. It is also a Kotlin multi-platform library which makes it possible to use Elasticsearch or Opensearch from any platform. Es-kotlin-client lives on as the legacy-client module in this library and shares several of the other modules (e.g. the search dsls). So, you may use this as a migration path to the multi-platform client.





