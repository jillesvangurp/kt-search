# Getting Started 

| [KT Search Manual](README.md) | Previous: [What is Kt-Search](WhatIsKtSearch.md) | Next: [Indices, Settings, Mappings, and Aliases](IndexManagement.md) |
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

And then the dependency:

```kotlin
    // check the latest release tag for the latest version
    implementation("com.jillesvangurp:search-client:2.x.y")
```

**IMPORTANT** We've switched maven repositories a couple of times now. Recently we switched back from jitpack.io to using our own repository. Jitpack is just too flaky for us to depend on and somehow they keep on having regressions with kotlin multi-platform projects.

**This also means the groupId has changed**. It's now `com.jillesvangurp` instead of `com.github.jillesvangurp.kt-search`.

I of course would like to get this on maven central eventually. However, I've had a really hard time getting that working and am giving up on that for now. The issue seems to be that I always hit some weird and very unspecific error and their documentation + plugins just never seem to quite work as advertised. Multi platform, multi module, and kotlin scripting are three things that tend to make things complicated apparently. If anyone wants to support me with this, please reach out. Otherwise use our private repository for now.

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
ES8: 8.6.2
docker-test-cluster is Green

```

## Customizing the ktor rest client

One of the parameters on `KtorRestClient` is the client parameter which has a default value
that depends on a default with actual implementations for Javascript and Jvm targets. Of course,
you can override this to further customize this. 

On the JVM, you can choose from several engines. We recently switched from the CIO engine to the 
Java engine that was added in Java 11. This is a non blocking IO http client.
 
The old CIO client is also still included.

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
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |