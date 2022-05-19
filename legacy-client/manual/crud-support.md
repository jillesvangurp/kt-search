[previous](getting-started.md) | [index](index.md) | [next](bulk-indexing.md)

___

# Using the IndexRepository 

To do anything with Elasticsearch we have to store documents in some index. The Java client
provides everything you need to do this but using it the right way requires quite a bit of boiler plate 
as well as a deep understanding of what needs doing.

An important part of this library is providing a user friendly abstraction for this that 
should be familiar if you've ever written a database application using modern frameworks such
as Spring, Ruby on Rails, etc. In such frameworks a  Repository 
provides primitives for interacting with objects in a particular database table.

We provide a similar abstraction the [`IndexRepository`](https://github.com/jillesvangurp/es-kotlin-wrapper-client/tree/master/src/main/kotlin/com/jillesvangurp/eskotlinwrapper/IndexRepository.kt). You create one for each 
index that you have and it allows you to do Create, Read, Update, and Delete (CRUD) operations as well as 
a few other things.

Since Elasticsearch stores Json documents, we'll want to use a data class to represent on the 
Kotlin side and let the `IndexRepository` take care of serializing/deserializing.

## Creating an `IndexRepository`

Lets use a simple data class with a few fields.

```kotlin
data class Thing(val name: String, val amount: Long = 42)
```

Now we can create an `IndexRepository` for our `Thing` using the `indexRepository` extension function:

```kotlin
// we pass in the index name
val repo = esClient.indexRepository<Thing>("things")
```

## Creating the index

Before we store any objects, we should create the index. Note this is optional but using
Elasticsearch in schema-less mode is probably not what you want. We use a simple mapping here.

```kotlin
repo.createIndex {
  // use our friendly DSL to configure the index
  configure {
    settings {
      replicas = 0
      shards = 1
    }
    mappings {
      // in the block you receive FieldMappings as this
      // a simple text field "title": {"type":"text"}
      text("name")
      // a numeric field with sub fields, use generics
      // to indicate what kind of number
      number<Long>("amount") {
        // we can customize the FieldMapping object
        // that we receive in the block
        fields {
          // we get another FieldMappings
          // lets add a keyword field
          keyword("somesubfield")
          // if you want, you can manipulate the
          // FieldMapping as a map
          // this is great for accessing features
          // not covered by our Kotlin DSL
          this["imadouble"] = mapOf("type" to "double")
          number<Double>("abetterway")
        }
      }
    }
  }
}
```

Of course you can also simply set the settings json using source. This is 
useful if you maintain your mappings as separate json files.

```kotlin
// delete the previous version of our index
repo.deleteIndex()
// create a new one using json source
repo.createIndex {
  source(
    """
      {
        "settings": {
        "index": {
          "number_of_shards": 3,
          "number_of_replicas": 0,
          "blocks": {
          "read_only_allow_delete": "false"
          }
        }
        },
        "mappings": {
        "properties": {
          "name": {
          "type": "text"
          },
          "amount": {
          "type": "long"
          }
        }
        }
      }
    """
  )
}
```

## CRUD operations

Now that we have an index, we can use Create, Read, Update, and Delete (CRUD) operations.

```kotlin
val id = "first"
println("Object does not exist: ${repo.get(id)}")
// so lets store something
repo.index(id, Thing("A thing", 42))

println("Now we get back our object: ${repo.get(id)}")
```

Captured Output:

```
Object does not exist: null
Now we get back our object: Thing(name=A thing, amount=42)

```

You cannot index an object twice unless you opt in to it being overwritten.

```kotlin
val id = "first"
try {
  repo.index(id, Thing("A thing", 42))
} catch (e: ElasticsearchStatusException) {
  println("we already had one of those and es returned ${e.status().status}")
}
// this how you do upserts
repo.index(id, Thing("Another thing", 666), create = false)
println("It was changed: ${repo.get(id)}")
```

Captured Output:

```
we already had one of those and es returned 409
It was changed: Thing(name=Another thing, amount=666)

```

Of course deleting an object is also possible.

```kotlin
repo.delete("1")
println(repo.get("1"))
```

Captured Output:

```
null

```

## Updates with optimistic locking

One useful feature in Elasticsearch is that it can do optimistic locking. The way this works is
that it keeps track of a `sequenceNumber` and `primaryTerm` for each document. 
If you provide both in index, it will check that what you provide matches what it has and only
overwrite the document if that lines up.

This works as follows.

```kotlin
repo.index("2", Thing("Another thing"))

val (obj, rawGetResponse) = repo.getWithGetResponse("2")
  ?: throw IllegalStateException("We just created this?!")

println(
  "obj with name '${obj.name}' has id: ${rawGetResponse.id}, " +
    "primaryTerm: ${rawGetResponse.primaryTerm}, and " +
    "seqNo: ${rawGetResponse.seqNo}"
)
// This works
repo.index(
  "2",
  Thing("Another Thing"),
  seqNo = rawGetResponse.seqNo,
  primaryTerm = rawGetResponse.primaryTerm,
  create = false
)
try {
  // ... but if we use these values again it fails
  repo.index(
    "2",
    Thing("Another Thing"),
    seqNo = rawGetResponse.seqNo,
    primaryTerm = rawGetResponse.primaryTerm,
    create = false
  )
} catch (e: ElasticsearchStatusException) {
  println("Version conflict! Es returned ${e.status().status}")
}
```

Captured Output:

```
obj with name 'Another thing' has id: 2, primaryTerm: 1, and seqNo: 0
Version conflict! Es returned 409

```

While you can do this manually, the Kotlin client makes optimistic locking a bit easier by 
providing a robust update method instead.

```kotlin
repo.index("3", Thing("Yet another thing"))

repo.update("3") { currentThing ->
  currentThing.copy(name = "an updated thing", amount = 666)
}

println("It was updated: ${repo.get("3")?.name}")

repo.update("3") { currentThing ->
  currentThing.copy(name = "we can do this again and again", amount = 666)
}

println("It was updated again ${repo.get("3")?.name}")
```

Captured Output:

```
It was updated: an updated thing
It was updated again we can do this again and again

```

Update simply does the same as we did manually earlier: it gets the current version of the object
along with the metadata. It then passes the current version to the update lambda function where
you can do with it what you want. In this case we simply use Kotlin's copy to create a copy and 
modify one of the fields and then we return it as the new value. 

The `update` method  traps the version conflict and retries a configurable number of times. 
Conflicts can happen if you have concurrent writes to the same object. The retry gets the latest 
version and applies the update lambda again and then attempts to store that.

To simulate what happens without retries, we can throw some threads at this and configure 0
retries:

```kotlin
repo.index("4", Thing("First version of the thing", amount = 0))

try {
  1.rangeTo(30).toList().parallelStream().forEach { n ->
    // the maxUpdateTries parameter is optional and has a default value of 2
    // so setting this to 0 and doing concurrent updates is going to fail
    repo.update("4", 0) { Thing("nr_$n") }
  }
} catch (e: Exception) {
  println("It failed because we disabled retries and we got a conflict")
}
```

Captured Output:

```
It failed because we disabled retries and we got a conflict

```

Doing the same with 10 retries, fixes the problem.

```kotlin
repo.index("5", Thing("First version of the thing", amount = 0))

1.rangeTo(30).toList().parallelStream().forEach { n ->
  // but if we let it retry a few times, it will be eventually consistent
  repo.update("5", 10) { Thing("nr_$n", amount = it.amount + 1) }
}
println("All updates succeeded! amount = ${repo.get("5")?.amount}.")
```

Captured Output:

```
All updates succeeded! amount = 30.

```

## Searching in your index

Now that we know how to add content, we can of course search as well.

We will dive into the different ways of searching in next chapters. But here is how you do simple search

```kotlin

repo.search {
  configure {
    resultSize = 5
    query = match(Thing::name,"another")
  }
}?.let {
  it.mappedHits.forEach { thing ->
    println("name: ${thing.name}, amount: ${thing.amount}")
  }
}
```

## Custom serialization 

By default, we use the popular jackson framework.         
However, If you want something else, you can customize how serialization and deserialization works. 

To do this, you have to provide your own [`ModelReaderAndWriter`](https://github.com/jillesvangurp/es-kotlin-wrapper-client/tree/master/src/main/kotlin/com/jillesvangurp/eskotlinwrapper/ModelReaderAndWriter.kt) implementation.

The default value of this is an instance of the included JacksonModelReaderAndWriter, which
uses Jackson to serialize and deserialize our `Thing` objects. 

If you don't want the default Jackson based serialization, or if you want to customize the jackson 
object mapper, you simply create your own instance and pass it to the `IndexRepository`.

```kotlin
// this is what is used by default but you can use your own implementation
val modelReaderAndWriter = JacksonModelReaderAndWriter(
  Thing::class,
  ObjectMapper().findAndRegisterModules()
)

val thingRepository = esClient.indexRepository<Thing>(
  index = "things", modelReaderAndWriter = modelReaderAndWriter
)
```

## Index Commits

Elasticsearch by default does not wait for changes to get committed to your index. This can lead
to inconsistent results when you search right after making modifications.

There are two solutions to this:

```kotlin
// this tells Elasticsearch to wait until changes
// to the index for the repo have been committed
repo.refresh()
// after this, any changes should be visible in searches
```

Alternatively, you can specify `waitUntil=true` on index, update, or delete operations

```kotlin
repo.index("something", Thing("a thing"),
  refreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL)
repo.update("something",
  refreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL) {
  it.copy(name = "modified")
}
val name = repo.search {
  configure {
    // query on the _id field that ES adds
    query = term("_id", "something")
  }
}.mappedHits.first()
println("Search returns the correct name: '$name'")
```

Captured Output:

```
Search returns the correct name: 'Thing(name=modified, amount=42)'

```

## Co-routine support

As with most of this library, the same functionality is also available in a co-routine friendly
variant `AsyncIndexRepository`. To use that, you need to use `esClient.asyncIndexRepository`. 

This works almost the same as the synchronous version except all of the functions are marked as 
suspend on the `AsyncIndexRepository` class. Additionally, the return type of the search method
is different and makes use of the Flow API. 

For more details on how to use co-routines with the ES Kotlin Client, see [Asynchronous IO with Co-routines](coroutines.md)


___

[previous](getting-started.md) | [index](index.md) | [next](bulk-indexing.md)

