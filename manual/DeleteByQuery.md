# Deleting by query 

| [KT Search Manual](README.md) | Previous: [Deep Paging Using search_after and scroll](DeepPaging.md) | Next: [Document Manipulation](DocumentManipulation.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

Delete by query is supported both on the client and the repository.    

```kotlin
val repo = client.repository(indexName, TestDoc.serializer())
repo.bulk(refresh = Refresh.WaitFor) {
  create(TestDoc("1", "banana", price = 2.0))
  create(TestDoc("1", "apple", price = 1.0))
}

repo.deleteByQuery {
  query = match(TestDoc::name, "apple")
}.deleted
```

->

```
2
```

If you need the optional query parameters on this API, use `client.deleteByQuery` instead.



---

| [KT Search Manual](README.md) | Previous: [Deep Paging Using search_after and scroll](DeepPaging.md) | Next: [Document Manipulation](DocumentManipulation.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |