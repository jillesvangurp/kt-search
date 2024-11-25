# Document Manipulation 

| [KT Search Manual](README.md) | Previous: [Deleting by query](DeleteByQuery.md) | Next: [Index Repository](IndexRepository.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

Mostly, you will use bulk indexing to manipulate documents in Elasticsearch. However, 
sometimes it is useful to be able to manipulate individual documents with the 
Create, Read, Update, and Delete (CRUD) APIs.

## CRUD

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
  // to get the TestDoc, use this extension function
  // this works on the _source field in the response
  .document<TestDoc>()

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

This prints:

```text

```

## Updates

Elasticsearch also has a dedicated update API that you can use with either a partial document or a script.

```kotlin
client.indexDocument(
  target = "myindex",
  document = TestDoc("42", "x"),
  id = "42"
)
var resp = client.updateDocument(
  target = "myindex",
  id = "42",
  docJson = """{"name":"changed"}""",
  source = "true"
)
println(resp.get?.source)

resp = client.updateDocument(
  target = "myindex",
  id = "42",
  script = Script.create {
    source = """ctx._source.name = params.p1 """
    params = mapOf(
      "p1" to "again"
    )
  },
  source = "true"
)
println(resp.get?.source)

```

This prints:

```text
{"id":"42","name":"changed","tags":[]}
{"id":"42","name":"again","tags":[]}
```

## Bulk

The index API has a lot more parameters that are supported here as well
via nullable parameters. You can also use a variant of the index API
that accepts a json String instead of the TestDoc.

Note, for inserting large amounts of documents you should of course use the bulk API. 
You can learn more about that here: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md).               

## Multi Get

To retrieve multiple documents, you can use the `mget` API>

```kotlin
client.indexDocument(
  target = "myindex",
  document = TestDoc("1", "One"),
  id = "1"
)
client.indexDocument(
  target = "myindex",
  document = TestDoc("2", "Two"),
  id = "2"
)

// the simple way is to provide a list of ids
client.mGet(index = "myindex") {
  ids= listOf("1","2")
}.let { resp->
  println("Found ${resp.docs.size} documents")
}
// or do it like this
val resp = client.mGet {
  doc {
    index="myindex"
    id = "1"
    // default is true
    source=true
  }
  doc {
    index="myindex"
    id = "2"
  }
}

// you can of course get the deserialized TestDocs
// from the response with an extension function
resp.documents<TestDoc>().forEach {
  println("${it.id}: ${it.name}")
}
```

This prints:

```text
Found 2 documents
1: One
2: Two
```



---

| [KT Search Manual](README.md) | Previous: [Deleting by query](DeleteByQuery.md) | Next: [Index Repository](IndexRepository.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |