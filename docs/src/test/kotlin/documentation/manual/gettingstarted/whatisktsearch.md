Kt-search is a Kotlin library that allows users to use Opensearch or Elasticsearch from Kotlin. It is a Kotlin multiplatform library that can be used on any platform that Kotlin can compile to. Currently, the jvm and js platforms are supported. 

## Why yet another client?

Kt-search is the successor to my popular [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) project. A few changes in the wider ecosystem of Elasticsearch necessitated a complete rewrite:

- After Elasticsearch decided to change their license, Amazon decided to fork Elasticsearch as Opensearch. More than a year after this happened, there are now a growing number of users that would want to use that.
- The es-kotlin-client depends on Elastic's RestHighLevel client for Java. This client is now deprecated and has been replaced by a new Java client. This client does not work with Opensearch. Nor do later versions of the RestHighLevelClient work with Opensearch. The Opensearch version of the RestHighLevelClient was forked as well and uses different package names and is only suitable for usage with Opensearch.
- Elasticsearch 8 was released. And while mostly backwards compatible, it of course has some new features and compatibility issues with version 7.
- Opensearch also released a 2.0 release recently.

In short, there are four variants of a search engine that used to be just Elasticsearch. All of those are supported in kt-search. And I plan to add support for future versions too while not breaking support for older versions if I can avoid it. 

## Other clients

Kt-search is of course not the only client you can use but the reason we built it is that I wanted a better development experience than other clients
provide. There currently is no official Elastic client for Kotlin. People use the Java one of course. However, in terms of developer experience,
that is a lot less nice that a native Kotlin client. In addition that only works for Elasticsearch and not for Opensearch. And while their 
new Java client is nice, Opensearch users are stuck with the Opensearch fork of the now deprecated RestHighLevel client. Not a good situation. 
Kt-search is nicer to use and supports both ecosystems. And if you need to, you can still use those clients next to it of course.

There are also a few other Kotlin clients that you can try. To the best of my knowledge, mine is the most feature rich of these. 
But do let me know if I'm wrong about this or if you think there is something that kt-search can or should do better or differently.

The whole point of kt-search is to provide a best in class developer experience for working with Elasticsearch or Opensearch.

## Kt Search 2.0 - What's new and what is different

 A few years of development on the es-kotlin-client has produced quite a few learnings. Additionally, I added a lot of features over time and gradually introduced things like asynchronous IO using Kotlin coroutines, Query DSL support, and a few other things. These are all things that ended up being useful and preserved in the new client.

 - Kotlin multiplatform and no more Java dependencies, such as the RestHighLevelClient. You can use kt-search in browsers using kotlin-js and on the jvm.
- You can still plug in custom HTTP and Json parsers and still use your favorite libraries for that on the JVM. However, the default setup uses `ktor-client` for http communication and `kotlinx-serialization` for JSON parsing.
- With Kt-Search, the Kotlin Query DSL stays mostly the same (but with some additions of course) and has moved to its own module. Aside from some package renames and minor feature work, the DSL should be backwards compatible with the one bundled with the es-kotlin client.
- More DSLs. Since building Json DSLs seems like a nice feature to have, I extracted the classes that I used as a basis for the query, mapping, and other DSLs to a separate module called `json-dsl`. So, in addition to querying there are now also DSLs for mappings and settings, index life cycle management, templates, and more.
 - All IO is now done using `suspend` functions and coroutines. I've found that I did not ever use the synchronous calls in the old es-kotlin-client and doing synchronous IO in Kotlin does not make a lot of sense. Especially not in a multiplatform library as the IO support for multiplatform Kotlin is of course asynchronous only.
- Functionality similar to the Repository class in the es-kotlin-client is also supported in Kt-Search. However, there were some API changes as we no longer can rely on Elastic's Java response model classes. Where needed, similar classes are provided for Kotlin and those are parsed with `kotlinx-serialization`.
- Kt-search is now asynchronous only. Blocking IO in Kotlin just doesn't make a lot of sense and it's easy to make everything suspending. If you really need blocking behavior, just surround it with a `runBlocking`.

## History of the project

Kt-search 2.0 is the 2.0 release and fork of a project that started out being a full rewrite of es-kotlin-client. Development of that started in July 2018. Work on the 2.0 branch started in December 2021. The [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) project still exists but I no longer maintain it. As of April 2023, The 2.0 release is stable and the only thing I will support going forward.

Before I created kt-search, I built various Java http clients for older versions of Elasticsearch dating back all the way to 2012, which is when I started using Elasticsearch while building my now defunct startup, localstre.am. This project builds on 10 years of using and working with Elasticsearch. At Inbot, we used our in house client for several years with Elasticsearch 1.x. I actually built an open source client for version 2.0, but we never upgraded to that version as version 5 was released soon after and broke compatibility. Later, I wrote another client on a customer project for version 5.x. This was before the Elastic's RestHighLevel client was finalized.

The rewrite in `kt-search` 2.0 was necessitated by the deprecation of Elastic's RestHighLevelClient and the Opensearch fork of Elasticsearch created by Amazon. One of the things they forked is this deprecated `RestHighLevelClient` client. Except of course they changed all the package names, which makes supporting both very tedious.

However, Elasticsearch and Opensearch still share the same REST API with only very minor variations mostly related to advanced features. For most common uses they are identical products.

Kt-search, removes the dependency on the Java client entirely. This in turn makes it possible to use all the wonderful new libraries in the Kotlin ecosystem. Therefore, it also is a **Kotlin multiplatform library**. This is a feature we get for free simply by using what is there. Kotlin multiplatform makes it possible to use Elasticsearch or Opensearch on any platform where you can compile this library.

Currently, that includes the **jvm** and **kotlin-js** compilers. However, it should be straightforward to compile this for e.g. IOS or linux as well using the **kotlin-native** compiler and the new **wasm** compiler. I just lack a project to test all this properly.

You can use kt-search in Spring servers, Ktor servers, AWS lambda functions, node-js servers, web applications running in a browser, or native applications running on IOS and Android. I expect, people will mostly stick to using servers on the JVM, at least short term. But I have some uses in mind for building small dashboard UIs as web applications as well. Let me know what you do with this!