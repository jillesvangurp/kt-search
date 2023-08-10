# Index Repository 

| [KT Search Manual](README.md) | Previous: [Document Manipulation](DocumentManipulation.md) | Next: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

To cut down on the amount of copy pasting of aliases and index names, kt-search includes 
a useful abstraction: the `IndexRepository`.

An `IndexRepository` allows you to work with a specific index. You can perform document CRUD, query,
do bulk indexing, etc. Additionally, you can configure read and write aliases and ensure the correct
aliases are used.

## Creating a repository

```kotlin
val repo = client.repository("test", TestDoc.serializer())

repo.createIndex {
  mappings {
    text(TestDoc::message)
  }
}
val id = repo.index(TestDoc("A document")).id
repo.delete(id)

// and of course you can search in your index
repo.search {
  query=matchAll()
}
```

## Bulk Indexing

```kotlin
repo.bulk {
  // no need to specify the index
  index(TestDoc("test"))
  index(TestDoc("test1"))
  index(TestDoc("test2"))
  index(TestDoc("test3"))
}
```

## Multi Get

Multi get is of course also supported.

```kotlin
repo.bulk {
  index(TestDoc("One"), id = "1")
  index(TestDoc("Two"), id = "2")
}
// multi get can be very convenient
repo.mGet("1","2")
// but you can also do use the full dsl
repo.mGet {
  ids = listOf("1","2")
}
// or if you insist
repo.mGet {
  doc {
    id="1"
    source=false
  }
  doc {
    id="2"
  }
}
```

## Optimistic locking and updates

Elasticsearch is of course not a database and it does not have transactions.

However, it can do optimistic locking using `primary_term` and `seq_no` attributes that it exposes in 
index responses or get document responses. This works by setiing the `if_primary_term` and `if_seq_no` 
parameters on indexing operations and handling the version conflict http response by trying again with
a freshly fetched version of the document that has the current values of `primary_term` and `seq_no`. 
This happens any time you have concurrent writes updating a document in between when you fetch it 
and when you attempt to replace it. By specifying  `if_primary_term` and `if_seq_no`, the conflict is
detected and you get a version conflict response.

Dealing with this is of course a bit fiddly to do. To make safe updates
easier, you can use the update function instead.

```kotlin
val id = repo.index(TestDoc("A document")).id
repo.update(id, maxRetries = 2) {oldVersion ->
  oldVersion.copy(message = "An updated document")
}
```

This fetches the document and the `primary_term` and `seq_no` values, applies your update function, 
and then stores it. In case of a version conflict, it re-fetches and retries this a configurable 
number of times before eventually failing.
            
You might also want to apply optimistic locking to bulk updates and it has a similar mechanism for
setting `if_primary_term` and `if_seq_no`. The index repository implements an extended version of the
BulkSession that includes update functions similar to the above and a callback based retry mechanism.

```kotlin
val id = repo.index(TestDoc("A document")).id
repo.bulk(
  // these parameters are optional
  // and have sensible defaults
  maxRetries = 1,
  retryTimeout = 2.seconds) {
  update(id, TestDoc("Changed"), 42,42) {
    it
  }
}
```



---

| [KT Search Manual](README.md) | Previous: [Document Manipulation](DocumentManipulation.md) | Next: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |