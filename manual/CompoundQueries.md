# Compound Queries 

| [KT Search Manual](README.md) | Previous: [Term Level Queries](TermLevelQueries.md) | Next: [Aggregations](Aggregations.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

Elasticsearch has several query varieties that you can use to combine other queries. These are called compound queries.
We'll use this extension function on `SearchResponse` to print the results and the same `TestDoc` class that 
we used before.

```kotlin
fun SearchResponse.pretty(message: String): String =
  // simple extension function to print the results
  "$message Found ${total} results:\n" +
      hits!!.hits.joinToString("\n") { h ->
        "- ${h.score} ${h.id} ${h.parseHit<TestDoc>().name}"
      }
```

## Bool

The most basic compound query is the `bool` query which as the name suggest is about doing 
logical and's or's and not's.

```kotlin
client.search(indexName) {
  query = bool {
    must(
      match(TestDoc::tags,"fruit")
    )
    mustNot(range(TestDoc::price) {
      lt=10
    })
    should(
      range(TestDoc::price) {
        lte=50
      },
      range(TestDoc::price) {
        gte=20
      },
    )
    filter(range(TestDoc::price) {
      gte=0
    })
  }
}.pretty("Bool query.").let {
  println(it)
}
```

Captured Output:

```
Bool query. Found 0 results:


```

## Dis-max

Dismax may be used as an alternative to bool with a bit more control over the scoring.

```kotlin
client.search(indexName) {
  query = disMax {
    queries(
      matchPhrasePrefix(TestDoc::name, "app"),
      matchPhrasePrefix(TestDoc::name, "banana"),
      range(TestDoc::price) {
        lte = 0.95
      }
    )
    tieBreaker = 0.75
  }
}.pretty("Dismax query.").let {
  println(it)
}
```

Captured Output:

```
Dismax query. Found 2 results:
- 1.8425692 1 Apple
- 1.8425692 2 Banana

```

## Boosting

Instead of completely disregarding expensive items, we can use a boosting 
query with a negative boost on the price if it is too high. This 
will cause expensive items to be ranked lower.

```kotlin

client.search(indexName) {
  // all fruits but with negative score on high prices
  query = boosting {
    positive = match(TestDoc::tags, "fruit")
    negative = range(TestDoc::price) {
      gte = 0.6
    }
  }
}.pretty("Boosting query.").let {
  println(it)
}
```

Captured Output:

```
Boosting query. Found 2 results:
- 0.4700036 1 Apple
- 0.2350018 2 Banana

```

## Function score

The last compound query is the function_score query. **Warning**: you may want to consider using the 
simpler `distance_rank` function instead as function_score is one of the more complex things to
reason about in Elasticsearch. Howwever, if you need it, kt-search supports it.

```kotlin
client.search(indexName) {
  query = functionScore {
    query = matchAll()
    // you can add multiple functions
    function {
      weight = 0.42
      exp("price") {
        origin = ".5"
        scale = "0.25"
        decay = 0.1
      }
    }
    function {
      filter = this@search.range(TestDoc::price) {
        gte = 0.6
      }
      weight = 0.1
    }
    function {
      weight = 0.25
      randomScore {
        seed = 10
        field = "_seq_no"
      }
    }
    function {
      fieldValueFactor {
        field(TestDoc::price)
        factor = 0.666
        missing = 0.01
        modifier =
          FieldValueFactorConfig.FieldValueFactorModifier.log2p
      }
    }
    function {
      weight = 0.1
      scriptScore {
        params = withJsonDsl {
          this["a"] = 42
        }
        source = """params.a * doc["price"].value """
      }
    }
    // and influence the score like this
    boostMode = FunctionScoreQuery.BoostMode.avg
    // IMPORTANT, if any of your functions return 0, the score is 0!
    scoreMode = FunctionScoreQuery.ScoreMode.multiply
    boost = 0.9
  }
}.pretty("Function score").let {
  println(it)
}
```

Captured Output:

```
Function score Found 3 results:
- 0.46640626 1 Apple
- 0.45020398 2 Banana
- 0.45000196 3 Green Beans

```



---

| [KT Search Manual](README.md) | Previous: [Term Level Queries](TermLevelQueries.md) | Next: [Aggregations](Aggregations.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |