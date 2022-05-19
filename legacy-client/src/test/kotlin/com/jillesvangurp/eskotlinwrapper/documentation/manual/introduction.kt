package com.jillesvangurp.eskotlinwrapper.documentation.manual

val introductionMd = """
This is the manual for the [Kotlin Client for Elasticsearch](https://github.com/jillesvangurp/es-kotlin-client). 
It is intended for server side Kotlin development and provides a friendly Kotlin API on top of the Java client provided 
by Elastic.

Elastic's [`HighLevelRestClient`](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high.html) 
is written in Java and provides access to essentially everything in the REST API that Elasticsearch exposes. This Java API 
provides access to all of the oss and x-pack features. However, it is not the easiest thing to work with directly. 

**The Es Kotlin Client takes away none of that power but adds a lot of power and convenience.** 

The Kotlin client extends the Java client in several ways: it adds functionality through extension functions,
it uses generated code to adapt the built in asynchronous functions to Kotlin co-routines, and it adds Kotlin Domain
Specific Languages (DSLs) for things like queries, mappings, etc.

The underlying Java functionality is always there ready to be used if you need it. However, for most commonly used 
things, this client provides more Kotlin appropriate ways to access the functionality.
    
## History of the project
 
I wrote my first client for Elasticsearch in Java before Elasticsearch 1.0 was released. 
This was an internal project for a startup that I founded in 2012. I planned to open source it but it never happened. 
When Elasticsearch 2.0 was released, I attempted to fix this and I created an OSS version of the code base. 

However, I never got around to updating our own code base to 2.0 so the code was never finished. Then Elasticsearch 5.0 
broke compatibility in a pretty major way and I never got around to supporting that. Besides, it included a new Java 
low level client which kind of discouraged me from doing more work on the code base. The high level client was included with 6.0. 

A few years ago, I started using Kotlin and at the same time (finally) migrated our code base to the latest version of 
Elasticsearch. By that time, Elastic had released its RestHighLevel client and I attempted to use it and quickly
realized that I missed some features that by now I had built several times. For example, the idea of having a Bulk 
session that manages the complexity of the bulk API, a convenient way to iterate over the results of a scrolling search, 
or having convenient index repositories that know how to serialize and deserialize json documents.

By this time, I had also rebuilt simple http clients for Java on another project (for Elasticsearch 5.x) and sort of 
ended up building the same things yet again.

So, in 2018, I realized I wanted to build this functionality again for Kotlin and I decided to do it properly this time and
make it an open source project from day one. The startup I was still with at the time shut down around the same time so
I did not immediately have a project to use it on. But I was freelancing as an Elasticsearch specialist at the time and
found myself with enough down time to get the project off the ground. Over the years since, I've kept on working on the 
client and eventually used it in a couple of projects. Every time I use it, I discover I want to add more features, more 
convenience, and find more ways to improve the code base.

I purposely delayed releasing a 1.0 to not set myself up for failure by committing to supporting APIs that felt 
wrong or needed work. At some point, I started thinking about what it would take to get to a 1.0 and started chipping
away at fixing those issues one by one. A big item on my list was having documentation with examples that actually work.

This manual is the result of that effort.
    
""".trimIndent()