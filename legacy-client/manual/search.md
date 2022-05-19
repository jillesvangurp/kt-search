[previous](bulk-indexing.md) | [index](index.md) | [next](query-dsl.md)

___

# Search 

```kotlin
// lets use a slightly different model class this time
data class Thing(val name: String)
```

Lets index some documents to look for ...

```kotlin
// force ES to commit everything to disk so search works right away
repo.bulk(refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE) {
  index("1", Thing("The quick brown fox"))
  index("2", Thing("The quick brown emu"))
  index("3", Thing("The quick brown gnu"))
  index("4", Thing("Another thing"))
  5.rangeTo(100).forEach {
    index("$it", Thing("Another thing: $it"))
  }
}
repo.refresh()
```

## Searching and working with results

```kotlin
// a SearchRequest is created and passed into the block
val results = repo.search {
  // we can use Kotlin's string templating
  val text = "brown"
  configure {
    query = match("name", text)
  }
}
println("Found ${results.totalHits}")

// we can get the deserialized thing from the search response
results.mappedHits.forEach {
  // kotlin sequences are lazy; nothing is deserialized unless you use it
  println(it)
}

// we can also get the underlying `SearchHit` that Elasticsearch returns
val firstSearchHit = results.searchHits.first()
firstSearchHit.apply {
  // this would be useful if we wanted to do some bulk updates
  println("Hit: $id $seqNo $primaryTerm\n$sourceAsString")
}

// or we can get both as a `Pair`
val firstHit = results.hits.first()
val (searchHit, deserialized) = firstHit
println("Hit: ${searchHit.id}:\n$deserialized")
```

Captured Output:

```
Found 3 hits
Thing(name=The quick brown fox, amount=42)
Thing(name=The quick brown emu, amount=42)
Thing(name=The quick brown gnu, amount=42)
Hit: 1 -2 0
{"name":"The quick brown fox","amount":42}
Hit: 1:
Thing(name=The quick brown fox, amount=42)

```

We provide several alternative ways to query elasticsearch; including the Kotlin DSL that we used above, raw json in the form of multi line strings, or the Java builders that come with the Java client. For documentation for that see [Kotlin Query DSL](query-dsl.md)

## Count

We can also query just to get a document count.

```kotlin
println("The total number of documents is ${repo.count()}")

val text = "quick"
val count = repo.count {
  configure {
    // instead of "name" you can also use a property reference
    query = match(Thing::name, text)
  }
}
println("We found $count results matching $text")
```

Captured Output:

```
The total number of documents is 100
We found 3 results matching quick

```

## Using multi line strings

Using the Kotlin DSL is nice if you want to programmatically construct your queries in a typesafe way.

However, sometimes you just want to run a query straight from the Kibana Development console in json form. And since Kotlin has multi line strings, doing this is easy.

```kotlin
val results = repo.search {
  // we can use Kotlin's string templating
  val text = "brown"
  source("""
    {
      "size": 10,
      "query": {
        "match": {
          // did you know ES allows comments in JSON?
          // Look we can inject our variable
          // but of course beware script injection!
          "name": "$text"
        }
      }
    }          
  """.trimIndent())
}
println("Found ${results.totalHits}")
```

Captured Output:

```
Found 3 hits

```

## Multi Search (msearch)

We also have a DSL for `msearch` that fires off multiple queries in one go.

```kotlin
val mSearchResults = repo.mSearch {
  // a header with a custom searchType
  header {
    searchType = SearchType.dfs_query_then_fetch
  } withQuery {
    query = matchAll()
  }

  // an empty header
  header {  } withQuery {
    query = matchAll()
  }

  // adds an empty header
  withQuery {
    query = matchAll()
  }
}

println("returned ${mSearchResults.responses.size} sets of results")
```

Captured Output:

```
returned 3 sets of results

```

## Scrolling searches

Elasticsearch has a notion of scrolling searches for retrieving large amounts of 
documents from an index. Normally this works by keeping track of a scroll token and
passing that to Elasticsearch to fetch subsequent pages of results. Scrolling is useful if
you want to process large amounts of results.

To make scrolling easier and less tedious, the search method on the repository 
has a simpler solution: simply set `scrolling` to `true`.
 
A classic use case for using scrolls is to bulk update your documents. You can do this as follows. 

```kotlin
repo.bulk {
  // simply set scrolling to true will allow us to scroll over the entire index
  // this will scale no matter what the size of your index is. If you use
  // scrolling, you can also set the ttl for the scroll (default is 1m)
  val results = repo.search(
    scrolling = true,
    scrollTtlInMinutes = 10
  ) {
    configure {
      // the page size for the scrolling search
      // note, resultSize is translated to size. But since size is also
      // a function on Map, we work around this.
      resultSize = 5
      query = matchAll()
    }
  }
  // lets not print lots of results
  results.hits.take(15).forEach { (hit, thing) ->
    // if you turn off source on your mapping, thing could be null
    println("${hit.id}: ${thing?.name}")
  }
  // after the last page of results, the scroll is cleaned up
}
```

Captured Output:

```
1: The quick brown fox
2: The quick brown emu
3: The quick brown gnu
4: Another thing
5: Another thing: 5
6: Another thing: 6
7: Another thing: 7
8: Another thing: 8
9: Another thing: 9
10: Another thing: 10
11: Another thing: 11
12: Another thing: 12
13: Another thing: 13
14: Another thing: 14
15: Another thing: 15

```


___

[previous](bulk-indexing.md) | [index](index.md) | [next](query-dsl.md)

