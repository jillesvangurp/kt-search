# Getting Started 

| [KT Search Manual](README.md) | Previous: [What is Kt-Search](WhatIsKtSearch.md) | Next: [Client Configuration](ClientConfiguration.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

To get started, simply add the dependency to your project and create a client. 
The process is the same for both jvm and kotlin-js.

## Gradle

Kt-search is published to the FORMATION maven repository. 

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
And then add the dependency like this:

```kotlin
    // check the latest release tag for the latest version
    implementation("com.jillesvangurp:search-client:2.x.y")
```

## Maven

If you have maven based kotlin project targeting jvm and can't use kotlin multiplatform dependency, you will need to **append '-jvm' to the artifacts**.

Add the `maven.tryformation.com` repository:

```xml
<repositories>
    <repository>
        <id>try-formation</id>
        <name>kt search repository</name>
        <url>https://maven.tryformation.com/releases</url>
    </repository>
</repositories>
```

And then add dependencies for jvm targets:

```xml
<dependencies>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>search-client-jvm</artifactId>
        <version>2.x.y</version>
    </dependency>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>search-dsls-jvm</artifactId>
        <version>2.x.y</version>
    </dependency>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>json-dsl-jvm</artifactId>
        <version>3.x.y</version>
    </dependency>
</dependencies>
```
**Note:** The `json-dsl` is moved to separate repository. To find the latest version number, check releases: https://github.com/jillesvangurp/json-dsl/releases

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