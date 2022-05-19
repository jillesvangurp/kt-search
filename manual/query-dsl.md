[previous](search.md) | [index](index.md) | [next](coroutines.md)

___

# Kotlin Query DSL 

Elasticsearch has a Query DSL and the Java Rest High Level Client comes with a very expansive
set of builders that you can use to programmatically construct queries. Of course builders are 
something that you should avoid in Kotlin. 

On this page we outline a few ways in which you can build queries both programmatically using the builders
that come with the Java client, using json strings, and using our **Kotlin DSL**.

We will use the same example as before in [Search](search.md). 

## Java Builders

The Java client comes with `org.elasticsearch.index.query.QueryBuilders` which provides static methods 
to create builders for the various queries. This covers most but probably not all of the query DSL 
but should cover most commonly used things.

```kotlin
val results = repo.search {
  source(
    searchSource()
      .size(20)
      .query(
        boolQuery()
          .must(matchQuery("name", "quick").boost(2.0f))
          .must(matchQuery("name", "brown"))
      )
  )
}
println("We found ${results.totalHits} results.")
```

Captured Output:

```
We found 3 hits results.

```

This is unfortunately quite ugly from a Kotlin point of view. Lets see if we can clean that up a little.

```kotlin

// more idomatic Kotlin using apply { ... }
val results = repo.search {
  source(
    searchSource().apply {
      query(
        boolQuery().apply {
          must().apply {
            add(matchQuery("name", "quick").boost(2.0f))
            add(matchQuery("name", "brown"))
          }
        }
      )
    }
  )
}
println("We found ${results.totalHits} results.")
```

Captured Output:

```
We found 3 hits results.

```

Using `apply` gets rid of the need to chain all the calls and it is a little better but still a little verbose. 

## Kotlin Search DSL

To address this, this library provides a Kotlin DSL that allows you to mix both type safe DSL constructs 
and simple schema-less manipulation of maps. We'll show several versions of the same query above to
show how this works.

The example below uses the type safe way to set up the same query as before.

```kotlin
// more idomatic Kotlin using apply { ... }
val results = repo.search {
  // SearchRequest.dsl is the extension function that allows us to use the dsl.
  configure {
    // SearchDSL is passed to the block as this
    // It extends our MapBackedProperties class
    // This allows us to delegate properties to a MutableMap

    // from is a property that is stored in the map
    from = 0

    // MapBackedProperties actually implements MutableMap
    // and delegates to a simple MutableMap.
    // so this works too: this["from"] = 0

    // Unfortunately Maps have their own size property so we can't
    // use that as a property name for the query size :-(
    resultSize = 20
    // this actually puts a key "size" in the map

    // query is a function that takes an ESQuery instance
    query =
      // bool is a function that create a BoolQuery,
      // which extends ESQuery, that is injected into the block
      bool {
        // BoolQuery has a function called must
        // it also has filter, should, and mustNot
        must(
          // must has a vararg list of ESQuery
          match(Thing::name, "qiuck") {
            // match always needs a field and query
            // but boost is optional
            boost = 2.0
            // so we find something despite the misspelled quick
            fuzziness = "auto"
          },
          // the block param for match is nullable and
          // defaults to null
          // you can refer fields by string name as well
          matchPhrase("name", "quick brown") {
            slop = 1
          }
        )
      }
  }
}
println("We found ${results.totalHits} results.")
```

Captured Output:

```
We found 3 hits results.

```

## Extending the DSL

The Elasticsearch DSL is huge and only a part is covered in our Kotlin DSL so far. Currently, most 
compound, text, and term level queries are supported.

Using the DSL
in schema-less mode allows you to work around this and you can of course mix both approaches.

However, if you need something added to the DSL it is really easy to do this yourself. For example 
this is the implementation of the match we use above: 

For more information on how to extend the DSL read [Extending and Customizing the Kotlin DSLs](dsl-customization.md)


___

[previous](search.md) | [index](index.md) | [next](coroutines.md)

