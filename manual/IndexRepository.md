# Index Repository 

                | [KT Search Manual](README.md) | Previous: [Document Manipulation](DocumentManipulation.md) | Next: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |
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

## Optimistic locking and updates

Elasticsearch is of course not a database and it does not have transactions.

However, it can do optimistic locking using primary_term and seq_no attributes that it exposes in 
index responses or get document responses. Doing this is of course a bit fiddly. To make safe updates
easier, you can use the update function instead.

```kotlin
val id = repo.index(TestDoc("A document")).id
repo.update(id, maxRetries = 2) {oldVersion ->
  oldVersion.copy(message = "An updated document")
}
```

Conflicts might happen when a document is updated concurrently or when some time has 
passed in between when you fetched the document and when you update it. The above function 
fetches the document, applies your update, and then stores it. In case of a version conflict,
it re-fetches and retries this a configurable number of times before failing.            


                ---
                | [KT Search Manual](README.md) | Previous: [Document Manipulation](DocumentManipulation.md) | Next: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |