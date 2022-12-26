# Getting Started 

| [KT Search Manual](README.md) | Previous: [What is Kt-Search](WhatIsKtSearch.md) | Next: [Indices, Settings, Mappings, and Aliases](IndexManagement.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |

---                

To get started, simply add the dependency to your project and create a client. 
The process is the same for both jvm and kotlin-js.

## Gradle

Add the Jitpack repository:

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

And then add the latest version:

```kotlin
implementation("com.github.jillesvangurp.kt-search:search-client:1.99.18")
```

**Check the [releases](https://github.com/jillesvangurp/kt-search/releases) page** for the latest release tag.

The 1.99.x releases are intended as release candidates for an eventual 2.0 release. At this point the API is stable and the library is feature complete. A 2.0 release will happen very soon now.

## Create a Client

To use `kt-search` you need a `SearchClient` instance. Similar to what the Elastic and Opensearch Java client do, there is a
simple `RestClient` interface that currently has a default implementation based on `ktor-client`. This client
takes care of sending HTTP calls to your search cluster.

```kotlin
// creates a client with the default RestClient
val client = SearchClient()
```

After creating the client, you can use it. Since kt-search uses non blocking IO via ktor client, all 
calls are suspending and have to be inside a co-routine.

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

You can also use multiple nodes and use a node selection strategy.

```kotlin
val nodes= arrayOf(
  Node("127.0.0.1", 9200),
  Node("127.0.0.1", 9201),
  Node("127.0.0.1", 9202)
)
val client4 = SearchClient(
  KtorRestClient(
    nodes = nodes,
    nodeSelector = RoundRobinNodeSelector(nodes),
  )
)
```

There are currently just one NodeSelector implementation that implements a simple round robin
strategy. Note, it currently does not attempt to detect failing nodes or implements any cluster 
sniffing. This is something that may be added later (pull requests welcome). 

You can easily add your own node selection strategy by implementing the `NodeSelector` interface.

```kotlin
runBlocking {
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
ES7: 7.17.5
docker-test-cluster is Green

```

## JSON handling

The `SearchClient` has a `json` parameter with a kotlinx.serialization `Json`.
The default value for this is a carefully constructed instance that is configured
to be lenient and do the right thing with e.g. nulls and default values. But you 
can of course use your own instance should you need to. Note, the kotlinx.serialization defaults are pretty 
terrible for the real world. So, beware if you provide a custom instance.
           
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
  // adding new enum values is OK even if older clients won't understand it
  // they should be forward compatible
  ignoreUnknownKeys = true
  // decode missing enum values as null
  coerceInputValues = true
}
```



---

| [KT Search Manual](README.md) | Previous: [What is Kt-Search](WhatIsKtSearch.md) | Next: [Indices, Settings, Mappings, and Aliases](IndexManagement.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |