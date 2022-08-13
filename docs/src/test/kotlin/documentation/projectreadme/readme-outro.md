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
failing on unsupported engines For example this is added to the test for `search_after`:

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

Es-kotlin-client, aka. kt-search 1.0 lives on as the legacy-client module in this library and shares several of the other modules (e.g. `search-dsls`). So, you may use this as a migration path to the multi-platform client. But in terms of how you use the client, the transition from the legacy client to this should be pretty straight-forward.

## Development status

Currently, the client is feature complete, useful, and already better in many ways than the es-kotlin-client 1.x ever was. 

The 1.99.x series can be seen as a series of increasingly better release candidates/betas. If you do run into issues, please create an issue. Likewise, if there is important functionality that you need. I have so far not found any show stopping issues.

Before tagging a 2.0 release, I intend to port some of our internal code at FORMATION to use kt-search and dog food this extensively. I will likely discover new/missing features, add things I need, etc.

Until then, I reserve the right to refactor, rename, etc. things as needed. A 2.0 release will mark a commitment to API stability.

Non Goals:

- Full coverage of what the RestHighLevel client used to do. If you need it, just add that as a dependency or create your own extension function for `SearchClient`; it's not that hard. And of course consider creating a pull request if you do.
- Cover the entire search DSL. The provided Kotlin DSL is very easy to extend by design. If it doesn't directly support what you need, you can simply manipulate the underlying Map to add what you need. See the manual for instructions on how to do this. Alternatively, you can create a pull request to add proper type safe support for the feature that you want. Currently, most commonly used things in the DSL are already supported. We'll likely expand the features over time of course. Note, you can also create JsonDsls for other APIs. E.g. the snapshot API, the reindex API, etc. each have their own custom Json requests and responses. 

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
