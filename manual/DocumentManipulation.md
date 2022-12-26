# Document Manipulation 

| [KT Search Manual](README.md) | Previous: [Deep Paging Using search_after and scroll](DeepPaging.md) | Next: [Index Repository](IndexRepository.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |

---                

Mostly, you will use bulk indexing to manipulate documents in Elasticsearch. However, 
sometimes it is useful to be able to manipulate individual documents with the 
Create, Read, Update, and Delete (CRUD) APIs.

```kotlin
// create
val resp = client.indexDocument(
  target = "myindex",
  document = TestDoc("1", "A Document"),
  // optional id, you can let elasticsearch assign one
  id = "1",
  // this is the default
  // fails if the id already exists
  opType = OperationType.Create
)

// read
val doc = client.getDocument("myindex", resp.id)
  // source is a JsonDoc, which you can deserialize
  // with an extension function
  .source.parse<TestDoc>()

// update
client.indexDocument(
  target = "myindex",
  document = TestDoc("1", "A Document"),
  id = "1",
  // will overwrite if the id already existed
  opType = OperationType.Index
)

// delete
client.deleteDocument("myindex", resp.id)
```

The index API has a lot more parameters that are supported here as well
via nullable parameters. You can also use a variant of the index API
that accepts a json String instead of the TestDoc.

Note, for inserting large amounts of documents you should of course use the bulk API. You can learn more about that here: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md).



---

| [KT Search Manual](README.md) | Previous: [Deep Paging Using search_after and scroll](DeepPaging.md) | Next: [Index Repository](IndexRepository.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |