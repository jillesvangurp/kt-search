# Getting Started 

Add the dependency to your gradle build file:

// TODO gradle kts snippet


## Creating a Client

First you have to create a client. Similar to what the Elastic and Opensearch Java client do, there is a
simple `RestClient` interface that currently has a default implementation based on `ktor-client`. This client
takes care of sending http calls to your search cluster.

```kotlin
val client = SearchClient()
```

```kotlin
runBlocking {
  // all apis are `suspend` functions, so you need a co-routine scope
  client.searchEngineVersion().let { resp ->
    println("${resp.variantInfo.variant}: ${resp.version.number}")
  }
  client.clusterHealth().let {resp ->
    println(resp.clusterName + " is " + resp.status)
  }
}
```

Captured Output:

```
ES8: 8.2.0
docker-test-cluster is Green

```

You will probably want to override some of the default parameter values. For example, this is how you would
connect to your cluster in Elastic Cloud.

```kotlin
val client2=SearchClient(
  KtorRestClient(
    https = true,
    user = "alice",
    password ="secret",
    nodes = arrayOf( Node("xxxxx.europe-west3.gcp.cloud.es.io", 9243))
  )
)
```

