# Getting Started 

To get started, simply add the dependency to your project and create a client. 
The process is the same for both jvm and kotlin-js.

## Gradle

Add the [tryformation](https://tryformation.com) maven repository:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.jillesvangurp.kt-search")
        }
    }
}
```

Then add the latest version:

```kotlin
implementation("com.github.jillesvangurp.kt-search:search-client:1.99.18")
```

**Check the [releases](https://github.com/jillesvangurp/kt-search/releases) page** for the latest release tag.

Note, we may at some point try to push this to maven-central. For now, please use Jitpack. All the pre-releases will have the `1.99.x` prefix. Despite this, the project can at this point be considered stable, feature complete, and usable. I'm holding off on labeling this as a 2.0 until I've had a chance to use it in anger on my own projects. Until then, some API refinement may happen once in a while. I will try to minimize breakage between releases.

## Create a Client

First you have to create a client. Similar to what the Elastic and Opensearch Java client do, there is a
simple `RestClient` interface that currently has a default implementation based on `ktor-client`. This client
takes care of sending HTTP calls to your search cluster.

```kotlin
val client = SearchClient()
```

```kotlin
runBlocking {
  // all apis are `suspend` functions, so you need a co-routine scope
  client.root().let { resp ->
    println("${resp.variantInfo.variant}: ${resp.version.number}")
  }
  client.clusterHealth().let { resp ->
    println(resp.clusterName + " is " + resp.status)
  }
}
```

Captured Output:

```
ES7: 7.17.4
docker-test-cluster is Green

```

## Alternative ways to create a client

You may want to override some of the default parameter values. For example, this is how you would
connect to your cluster in Elastic Cloud.

```kotlin
val client2 = SearchClient(
  KtorRestClient(
    https = true,
    user = "alice",
    password = "secret",
    nodes = arrayOf(Node("xxxxx.europe-west3.gcp.cloud.es.io", 9243))
  )
)
```

`KtorRestClient` has an alternate constructor that you can use if
 you have a proxy in front of your cluster or only one node.

```kotlin
val client3 = SearchClient(KtorRestClient("127.0.0.1", 9200))
```

## JSON handling

The `SearchClient` has a json parameter with the kotlinx.serialization `Json` 
that has a default value with a carefully constructed instance that is configured
to be lenient and do the right thing with e.g. nulls and default values. But you 
can of course use your own instance should you need to.
           
There are two instances included with this library that are used by default that you may use here:

- `DEFAULT_JSON` this is what is used by default
- `DEFAULT_PRETTY_JSON` a pretty printing variant of DEFAULT_JSON that otherwise has the same settings.

```kotlin
val DEFAULT_JSON = Json {
  // don't rely on external systems being written in kotlin
  // or even having a language with default values
  // the default of false is insane and dangerous
  encodeDefaults = true
  // save space
  prettyPrint = false
  // people adding things to the json is OK, we're forward compatible
  // and will just ignore it
  isLenient = true
  // encoding nulls is meaningless and a waste of space.
  explicitNulls = false
  // adding enum values is OK even if older clients won't understand it
  ignoreUnknownKeys = true
  // will decode missing enum values as null
  coerceInputValues = true
}
```

