# Getting Started 

| [KT Search Manual](README.md) | Previous: [What is Kt-Search](WhatIsKtSearch.md) | Next: [Client Configuration](ClientConfiguration.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

To get started, simply add the dependency to your project and create a client. 
The process is the same for both jvm and kotlin-js.

## Gradle

Add the `maven.tryformation.com` repository:

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

And then the dependency to commonsMain or main:

```kotlin
    // check the latest release tag for the latest version
    implementation("com.jillesvangurp:search-client:2.x.y")
```

**About maven central ...** I've switched maven repositories a couple of times now. Jitpack and multiplatform just doesn't work. Of course I would have liked to get this on maven central. However, after repeated attempts to get that done, I've decided to not sacrifice more time on this. The (lack of) documentation, the Jira bureaucracy, the uninformative errors, the gradle plugin, etc.  just doesn't add up to something that works for a multi module, multi platform project. I'm sure it can be done but I'm not taking more time out my schedule to find out.

If somebody decides to fix a proper, modern solution for hosting packages, I'll consider using it but I'm done with maven central for now. Google buckets work fine for hosting. So does ssh or any old web server. So does aws. It's just maven central that's a huge PITA. 

## Create a Client

To use `kt-search` you need a `SearchClient` instance. Similar to what the Elastic and Opensearch Java client do, there is a
simple `RestClient` interface that currently has a default implementation based on `ktor-client`. This client
takes care of sending HTTP calls to your search cluster.

```kotlin
// creates a client with the default RestClient
val client = SearchClient()
```

## Using the client

After creating the client, you can use it. Since kt-search uses non blocking IO via ktor client, all 
calls are suspending and have to be inside a co-routine.

```kotlin
// use a simple runBlocking
// normally you would get a co-routine via e.g. Spring's flux async framework.
runBlocking {
  // call the root API with some version information
  client.root().let { resp ->
    println("${resp.variantInfo.variant}: ${resp.version.number}")
  }
  // get the cluster health
  client.clusterHealth().let { resp ->
    println(resp.clusterName + " is " + resp.status)
  }

}
```

Captured Output:

```
ES8: 8.9.0
docker-test-cluster is Green

```

The main purpose of kt-search is of course searching. This is how you do a simple search and work with 
data classes:

```kotlin

// define a model for your indexed json documents
data class MyModelClass(val title: String, )

// a simple search
val results = client.search("myindex") {
  query = matchPhrase(
    field = "title",
    query = "lorum ipsum")
}

// returns a list of MyModelClass
val parsedHits = results.parseHits<MyModelClass>()

// if you don't have a model class, you can just use a JsonObject
val jsonObjects = results
  .hits
  ?.hits
  // extract the source from the hits (JsonObject)
  ?.map { it.source }
  // fall back to empty list
  ?: listOf()
```

## Next steps

- [Client Configuration](ClientConfiguration.md): Learn how to customize the client further.
- [Search and Queries](Search.md): Learn more about how to use the query DSL.
- [Index Repository](IndexRepository.md): Configure an index repository to make working with a specific index easier.



---

| [KT Search Manual](README.md) | Previous: [What is Kt-Search](WhatIsKtSearch.md) | Next: [Client Configuration](ClientConfiguration.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |