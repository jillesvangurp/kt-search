# Deep Paging Using search_after and scroll 

| [KT Search Manual](README.md) | Previous: [Aggregations](Aggregations.md) | Next: [Deleting by query](DeleteByQuery.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

You can page through results using the `from` and `size` parameters (called `resultSize` in the DSL because
the name clashes with `Map.size`). However, this has performance issues and for that result, the from is 
limited to 10000. To retrieve more than that number of results, you need deep paging.

There are two ways of doing this:

- using `search_after` and a point in time
- using the scroll API

Both are somewhat complex APIs to use and kt-search provides an easy alternative that uses
the Kotlin `Flow` API. It simply returns you a flow and handles the paging for you; completely automatically.

This works both with scrolling and `search_after`. In the examples below, we use the same index 
with `TestDoc` documents.

## Search after

```kotlin
val (resp,hitsFlow) = client.searchAfter(indexName,1.minutes) {
  // 1 result per page
  // use something higher obviously, 500 would be good
  resultSize = 1
  query = matchAll()
  // note if you want to add sorting, you should also short on _shard_doc
}
println("reported result set size ${resp.total}")
println("results in the hits flow: ${hitsFlow.count()}")
```

This prints:
 
```
reported result set size 3
results in the hits flow: 3

```

This orchestrates creating a point in time and paging through results using 
sort values returned in the response.

The `hitsFlow` is a flow that allows you to process all the results. Doing so, will 
fetch page after page of result and you can process very large indices with this.                                 

The main principle with using search_after is relying on the sort order of the results and specifying
the sort values of the last value you have processed as the `search_after`.

Because you can sort on multiple fields, this has to be an array. To guarantee consistency, `search_after`
is generally used in combination with the Point in Time API. Kt-search adds a sort on "_shard_doc" for you.

If you want to add your own sort, you have to opt-in to this and deal with any issues yourself as you can
easily break search_after this way.

Note. Currently only works with Elasticsearch. Opensearch has a very similar implementation but it works 
slightly differently and currently kt-search only supports the elastic variant for this.

```kotlin
val (resp,hitsFlow) = client.searchAfter(
  target = indexName,
  keepAlive = 1.minutes,
  optInToCustomSort = true
) {
  // 1 result per page
  // use something higher obviously, 500 would be good
  resultSize = 1
  query = matchAll()

  sort {
    add(TestDoc::id)
  }
}
println("reported result set size ${resp.total}")
println("results in the hits flow: ${hitsFlow.count()}")
```

This prints:
 
```
reported result set size 3
results in the hits flow: 1

```

## Reindexing using search_after and bulk

A key use case for deep paging is processing all documents in an index.
And of course indices in Elasticsearch can be extremely large. So, it is
important to this incrementally.

A good use case is reindexing documents. This is easy with kt-search!

```kotlin
// we will rely on dynamic mapping
val newIndex="${indexName}-v2"
// WaitFor enables us to search right after
client.bulk(refresh = Refresh.WaitFor) {
  val (_,flow) = client.searchAfter(
    target = indexName,
    keepAlive = 1.minutes
  )
  // for each document ...
  flow.onEach { hit ->
    val doc = hit.parseHit<TestDoc>()
    // modify the name and put it in our new index
    index(doc.copy(name= "${doc.name} v2"), index=newIndex)
  }.collect()
}
println("$newIndex has ${client.search(newIndex).total} documents")
```

This prints:
 
```
docs-search-demo-v2 has 3 documents

```

## Scrolling searches

Even though `search_after` is the recommended way to do this, you can still use scrolling searches. 
Scrolling searches work in a very similar way to `search_after` but are a bit easier to use.
 
The main difference is that you have more control over consistency of the index with `search_after` 
if changes happen while you page through results.

For a scrolling search, simply search as normally but set the scroll parameter:

```kotlin
val response = client.search(indexName, scroll = "1m")
// the response has a scrollId that we can use to scroll all the results
val hitsFlow = client.scroll(response)
// the flow contains all the hits
println("Found ${hitsFlow.count()} results")
```

## Doing it the hard way

If you want to do deep paging manually, this is of course possible. The example 
below serves as an illustration why you might prefer to use the easy way
above instead:

```kotlin
// create a point in time
val pit = client.createPointInTime(indexName,1.minutes)
val q = SearchDSL().apply {
  resultSize=2

  query = matchAll()

  // this is not part of the DSL
  this["pit"] = withJsonDsl  {
    this["id"] = pit
  }
  // it's recommended to sort on _shard_doc
  sort {
    add("_shard_doc", SortOrder.ASC)
  }
}
// don't specify the index
// the pit id implies the index
var resp = client.search(null, q)

var results=resp.hits?.hits?.size ?:0
// specify from where to continue searching
resp.hits?.hits?.last()?.sort?.let { sort ->
  q["search_after"] = sort
}
// the response will include a pit id
resp.pitId?.let { pid ->
  q["pit"] = withJsonDsl  {
    this["id"] = pid
    this["keep_alive"] = "60s"
  }
}
// get the next page of results
resp = client.search(null, q)
results += resp.hits?.hits?.size ?: 0

// repeat ...
println("found $results results")
```

This prints:
 
```
found 3 results

```

Like with `search_after` you can also choose to specify scroll ids manually. If you do this,
be sure to delete your scrolls after you are done searching.

```kotlin
var resp = client.search(indexName, scroll = "1m") {
  resultSize=2
  query=matchAll()
}
var results=resp.hits?.hits?.size ?:0
// fetch the next page with the scroll API
resp = client.scroll(
  scrollId = resp.scrollId?:error("no scrollId"),
  scroll = 1.minutes
)
results+=resp.hits?.hits?.size ?:0

// .. repeat this until done

// finally release the scrollId
client.deleteScroll(resp.scrollId)
println("found $results results")
```

This prints:
 
```
found 3 results

```



---

| [KT Search Manual](README.md) | Previous: [Aggregations](Aggregations.md) | Next: [Deleting by query](DeleteByQuery.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |