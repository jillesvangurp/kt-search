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

The integration tests on GitHub Actions use a **matrix build** that tests everything against Elasticsearch 7 & 8 and Opensearch 1 & 2.

It may work fine with earlier Elasticsearch versions as well. But we don't actively test this and the tests are known to not pass with Elasticsearch 6 due to some changes in the mapping dsl. You may be able to work around some of this, however.

There is an annotation that is used to restrict APIs when needed. E.g. `search-after` only works with Elasticsearch and and has the following annotation to indicate that:

```kotlin
@VariantRestriction(SearchEngineVariant.ES7,SearchEngineVariant.ES8)
suspend fun SearchClient.searchAfter(target: String, keepAlive: Duration, query: SearchDSL): Pair<SearchResponse,Flow<SearchResponse.Hit>> {
    validateEngine("search_after does not work on OS1",
        SearchEngineVariant.ES7, 
        SearchEngineVariant.ES8)

    // ...
}
```

The annotation is informational only for now. In our tests, we use `onlyon` to prevent tests from
failing on unsupported engines For example, this is added to the test for `search_after`:

```kotlin
onlyOn("opensearch has search_after but it works a bit different",
    SearchEngineVariant.ES7,
    SearchEngineVariant.ES8)
```

New releases of this library generally update dependencies to their current versions. There currently is no LTS release of Kotlin. Generally this library should work with recent stable releases of Kotlin. At this point, that means Kotlin 2.0 or higher.

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                              |
|-----------------|--------------------------------------------------------------------------------------------------------------------------|
| `json-dsl`      | Kotlin framework for creating kotlin DSLs for JSON dialects. Such as those in Elasticsearch.                             |
| `search-dsls`   | DSLs for search and mappings based on `json-dsl`.                                                                        |
| `search-client` | Multiplatform REST client for Elasticsearch 7 & 8 and Opensearch 1. This is what you would want to use in your projects. |
| `docs`          | Contains the code that generates the [manual](https://jillesvangurp.github.io/kt-search/manual/) and this readme..       |

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


