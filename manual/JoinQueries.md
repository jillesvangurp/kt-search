# Join Queries 

| [KT Search Manual](README.md) | Previous: [Deep Paging Using search_after and scroll](DeepPaging.md) | Next: [Deleting by query](DeleteByQuery.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

Elasticsearch has the possibility of doing SQL style joins but in a way that is horizonatally scalable.
However, Elasticsearch does warn again using these due to potential performance issues.
Read more about this in the following documentation:
https://www.elastic.co/guide/en/elasticsearch/reference/current/joining-queries.html
https://www.elastic.co/guide/en/elasticsearch/reference/current/nested.html
https://www.elastic.co/guide/en/elasticsearch/reference/current/parent-join.html

## Parent / Child

Elasticsearch supports parent and child style joins.
The has_child query returns parent documents whose child documents match the specified query
While the has_parent query returns child documents whose parent document matches the specified query.

First you need to create an index with a join field mapping and insert some documents:

```kotlin
  @Serializable
  data class JoinField(val name: String, val parent: String? = null)

  @Serializable
  data class TestDoc(val name: String, val joinField: JoinField)

  val parent1 = "parent-1"
  val parent2 = "parent-2"
  val child1 = "child-1"

  val mapping = IndexSettingsAndMappingsDSL().apply {
    mappings(dynamicEnabled = false) {
      text("name")
      join("joinField") {
        relations("parent" to listOf("child"))
      }
    }
  }

  client.createIndex(indexName, mapping)

  val repo = client.repository(indexName, TestDoc.serializer())

  repo.index(
    TestDoc(name = parent1, joinField = JoinField(name = "parent")),
    id = parent1
  )
  repo.index(
    TestDoc(name = parent2, joinField = JoinField(name = "parent")),
    id = parent2
  )
  repo.index(
    TestDoc(
      name = child1,
      joinField = JoinField(name = "child", parent = parent1)
    ),
    id = child1,
    routing = parent1
  )
}
```

## Has Child

Return all documents that have a child that matches the query

```kotlin
client.search(indexName) {
  query = hasChild("") {
    query = matchAll()
  }
}.parseHits(TestDoc.serializer()).map {
  it.name
}
```

## Has Parent

Return all documents that have a parent that matches the query

```kotlin
client.search(indexName) {
  query = hasParent("") {
    query = matchAll()
  }
}.parseHits(TestDoc.serializer()).map {
  it.name
}
```

## Inner Hits

Return matching documents with inner hits of matching children

```kotlin
client.search(indexName) {
  query = hasChild("") {
    query = matchAll()
    innerHits()
  }
}.parseHits(TestDoc.serializer()).map {
  it.name
}
```

You can also add options to inner_hits such as name: 

```kotlin
client.search(indexName) {
  query = hasChild("") {
    query = matchAll()
    innerHits {
      name = "child-inner-hits"
    }
  }
}.parseHits(TestDoc.serializer()).map {
  it.name
}
```

## Parent ID

Returns child documents joined to a specific parent document.

```kotlin
client.search(indexName) {
  query = parentId(type = "child", id = "parent1")
}.parseHits(TestDoc.serializer()).map {
  it.name
}
```



---

| [KT Search Manual](README.md) | Previous: [Deep Paging Using search_after and scroll](DeepPaging.md) | Next: [Deleting by query](DeleteByQuery.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |