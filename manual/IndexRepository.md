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
index responses or get document responses. This works by setting the `if_primary_term` and `if_seq_no` 
parameters on indexing operations and handling the version conflict http response by trying again with
a freshly fetched version of the document that has the current values of `primary_term` and `seq_no`. 

Conflicts happen any time you have concurrent writes updating a document in between when you fetch it 
and when you attempt to replace it. By specifying `if_primary_term` and `if_seq_no`, the conflict is
detected and you get a version conflict response.

It is called optimistic locking because instead of locking, it simply applies a cheap check that 
can fail that you can then act on by retrying. Since nothing gets locked, everything stays fast. 
And with a rare retry operation, performance should not suffer.

Dealing with this is of course a bit fiddly to do. To make optimistic locking really easy,
easier, you can either use this way of updating a single document or our bulk update with retry, 
which is described below.  

```kotlin
val id = repo.index(TestDoc("A document")).id
repo.update(id, maxRetries = 2) {oldVersion ->
  oldVersion.copy(message = "An updated document")
}
```

This fetches the document and the `primary_term` and `seq_no` values, applies your update function, 
and then stores it. In case of a version conflict, it re-fetches the document, and then applies your 
function to that version. The number of retries is configurable. If all retries fail, you will get a 
version conflict exception. The only time this would happen is if you have a lot of concurrent writes 
to the same documents. 

## Bulk updates and optimistic locking

You may also want to apply optimistic locking to bulk updates and it has a similar mechanism for
setting `if_primary_term` and `if_seq_no`. The index repository implements an extended version of the
BulkSession that includes update functions similar to the above and uses a callback based retry mechanism.

You can still use a custom callback and it the retry callback will delegate to that. 

```kotlin
val aDoc = TestDoc("A document")
val id = repo.index(aDoc).id
repo.bulk(
  // these parameters are optional
  // and have sensible defaults
  maxRetries = 1,
  retryTimeout = 2.seconds
) {
  update(
    id = id,
    // you have to provide the original
    original = aDoc,
    // and the seq_no and primary_term
    // these values are probably wrong
    // amd will trigger a retry
    ifSeqNo = 42,
    ifPrimaryTerm = 42
  ) {
    // like before, we use a function block
    // to make the changes
    it.copy(message = "Changed")
  }
}
```

Digging out primary_term and seq_no numbers is of course a bit tedious. 
So,you can use anything implementing `SourceInformation`. This includes document
get responses, multi get responses, and search hits.

```kotlin
val aDoc = TestDoc("A document")
val id = repo.index(aDoc).id

val (_, getResponse) = repo.get(id)
// note, you should use a multi get if you are updating manu documents
// conflicts could still happen of course, let's force one
repo.index(
  value = aDoc.copy("This will be overwritten"),
  id = getResponse.id
)
repo.bulk {
  update(
    // everything we need is in the getResponse
    // however, our getResponse is now out of date
    // so it will retry
    getResponse
  ) {
    it.copy(message = "Changed it again")
  }
}

```

Using e.g. searchAfter is great for
applying large amounts of updates to an index. This is how that works:

```kotlin
repo.bulk {
  repo.searchAfter {
    query = matchAll()
  }.let { (firstResponse, hitFlow) ->
    // this will page through the entire index
    hitFlow.collect { hit ->
      // if somebody messes with the index while we do this
      // bulk update will just retry it
      update(
        hit
      ) {
        it.copy(message = it.message.reversed())
      }
    }
  }
}
```



---

| [KT Search Manual](README.md) | Previous: [Document Manipulation](DocumentManipulation.md) | Next: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |