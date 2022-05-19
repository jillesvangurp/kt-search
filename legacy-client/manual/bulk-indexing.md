[previous](crud-support.md) | [index](index.md) | [next](search.md)

___

# Bulk Indexing made easy 

An important part of working with Elasticsearch is adding content. While the CRUD support is useful
for manipulating individual objects in an index, it is not suitable for sending large amounts of data.

For that, bulk indexing should be used. The bulk API in Elasticsearch is one of the more complex APIs
in ES. The Kotlin client provides a few key abstractions to make bulk indexing easy, robust, 
and straightforward.

## Using the Repository to bulk index

Again we use our `Thing` class and `thingRepository`

```kotlin
data class Thing(val name: String, val amount: Long = 42)
```

To make this easy, the library comes with a [`BulkIndexingSession`](https://github.com/jillesvangurp/es-kotlin-wrapper-client/tree/master/src/main/kotlin/com/jillesvangurp/eskotlinwrapper/BulkIndexingSession.kt). This takes care
of all the boiler plate of constructing and sending bulk requests. Of course, our `IndexRepository` provides a
simple `bulk` method that creates a session for you:

```kotlin
// creates a BulkIndexingSession<Thing> and passes it to the block
repo.bulk {
  1.rangeTo(500).forEach {
    index("doc-$it", Thing("indexed $it", 666))
  }
}

println("Lets get one of them " + repo.get("doc-100"))
```

Captured Output:

```
Lets get one of them Thing(name=indexed 100, amount=666)

```

The `BulkIndexingSession` aggregates our `index` operations into `BulkRequest` 
requests and sends them to Elasticsearch for us. You can control how many operations are sent 
with each request by setting the `bulkSize` parameter. BulkIndexingSession implements `AutoClosable`
and will send the last request when it is closed. All this is taken care off by the `bulk` method of
course.

In addition to `index` we have a few more operations.

```kotlin
repo.bulk(bulkSize = 50) {
  // setting create=false overwrites and is the appropriate thing
  // to do if you are replacing documents in bulk
  index("doc-1", Thing("upserted 1", 666), create = false)

  // you can do a safe bulk update similar to the CRUD update.
  // this has the disadvantage of doing 1 get per item and may not scale
  getAndUpdate("doc-2") { currentVersion ->
    // this works just like the update on the repository and it will retry a
    // configurable number of times.
    currentVersion.copy(name = "updated 2")
  }

  // if you already have the seqNo, primary term, and current version
  // there you can skip the get. A good way to get these efficiently would be
  // a scrolling search.
  update(
    id = "doc-3",
    // yes, these two values are wrong; but it falls back to doing a
    // getAndUpdate.
    seqNo = 12,
    primaryTerms = 34,
    original = Thing("indexed $it", 666)
  ) { currentVersion ->
    currentVersion.copy(name = "safely updated 3")
  }
  // and of course you can delete items
  delete("doc-4")
}

println(repo.get("doc-1"))
println(repo.get("doc-2"))
println(repo.get("doc-3"))
// should print null
println(repo.get("doc-4"))
```

Captured Output:

```
Thing(name=upserted 1, amount=666)
Thing(name=updated 2, amount=666)
Thing(name=indexed 3, amount=666)
null

```

## Item Callbacks

An important aspect of bulk indexing is actually inspecting the response. The `BulkIndexingSession`
uses a callback mechanism that allows you to respond to do something. The default implementation for
this does two things: 

- it logs failures
- it retries conflicting updates

For most users this should be OK but if you want, you can do something custom:

```kotlin
repo.bulk(
  itemCallback = object : (BulkOperation<Thing>, BulkItemResponse) -> Unit {
    // Elasticsearch confirms what it did for each item in a bulk request
    // and you can implement this callback to do something custom
    override fun invoke(op: BulkOperation<Thing>, response: BulkItemResponse) {
      if (response.isFailed) {
        println(
          "${op.id}: ${op.operation.opType().name} failed, " +
            "code: ${response.failure.status}"
        )
      } else {
        println("${op.id}: ${op.operation.opType().name} succeeded!")
      }
    }
  }
) {

  update(
    id = "doc-2",
    // these values are wrong and this will now fail instead of retry
    seqNo = 12,
    primaryTerms = 34,
    original = Thing("updated 2", 666)
  ) { currentVersion ->
    currentVersion.copy(name = "safely updated 3")
  }
}
println("" + repo.get("doc-2"))

+"""
  # Other parameters
  
  There are a few more parameters that you can override.
"""
block {
  repo.bulk(
    // controls the number of items to send to Elasticsearch
    // what is optimal depends on the size of your documents and
    // your cluster setup.
    bulkSize = 10,
    // controls how often documents are retried by the default
    // item callback
    retryConflictingUpdates = 3,
    // controls how Elasticsearch refreshes and whether
    // the bulk request blocks until ES has refreshed or not
    refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE
  ) {

    delete("doc-1")
    update(
      id = "doc-2",
      // these values are wrong so this will be retried
      seqNo = 12,
      primaryTerms = 34,
      original = Thing("updated 2", 666)
    ) { currentVersion ->
      currentVersion.copy(name = "safely updated 3")
    }
  }
}
```

# Other parameters

There are a few more parameters that you can override.

```kotlin
repo.bulk(
  // controls the number of items to send to Elasticsearch
  // what is optimal depends on the size of your documents and
  // your cluster setup.
  bulkSize = 10,
  // controls how often documents are retried by the default
  // item callback
  retryConflictingUpdates = 3,
  // controls how Elasticsearch refreshes and whether
  // the bulk request blocks until ES has refreshed or not
  refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE
) {

  delete("doc-1")
  update(
    id = "doc-2",
    // these values are wrong so this will be retried
    seqNo = 12,
    primaryTerms = 34,
    original = Thing("updated 2", 666)
  ) { currentVersion ->
    currentVersion.copy(name = "safely updated 3")
  }
}
```

Captured Output:

```
doc-2: UPDATE failed, code: CONFLICT
Thing(name=updated 2, amount=666)

```


___

[previous](crud-support.md) | [index](index.md) | [next](search.md)

