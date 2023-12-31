# Text Queries 

| [KT Search Manual](README.md) | Previous: [Search and Queries](Search.md) | Next: [Term Level Queries](TermLevelQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

If you are doing textual search, Elasticsearch offers a lot of functionality out of the box. We'll cover only
the basics here. Please refer to the Opensearch and Elasticsearch manuals for full coverage of all the options 
and parameters.

## Match

```kotlin
client.search(indexName) {
  // will match on beans
  query = match(TestDoc::name, "red beans") {
    boost = 2.0
    lenient = true
    autoGenerateSynonymsPhraseQuery = true
  }
}.pretty("Match query").let {
  println(it)
}
```

This prints:
 
```
Match query Found 1 results:
- 1.6285465 3 Green Beans

```

## Match Phrase

```kotlin
client.search(indexName) {
  // will match on "green beans"
  query = matchPhrase(TestDoc::name, "green beans") {
    boost = 2.0
    slop = 2
    zeroTermsQuery = ZeroTermsQuery.none
  }
}.pretty("Match Phrase query").let {
  println(it)
}
```

This prints:
 
```
Match Phrase query Found 1 results:
- 3.257093 3 Green Beans

```

## Match Phrase Prefix

```kotlin
client.search(indexName) {
  // will match on "green beans"
  query = matchPhrasePrefix(TestDoc::name, "green bea") {
    boost = 2.0
    slop = 2
    zeroTermsQuery = ZeroTermsQuery.none
  }
}.pretty("Match Phrase Prefix query").let {
  println(it)
}
```

This prints:
 
```
Match Phrase Prefix query Found 1 results:
- 3.257093 3 Green Beans

```

## Multi Match

```kotlin
client.search(indexName) {
  // will match on "green beans"
  query = multiMatch("banana beans",
    "name", "tags.txt") {
    type = MultiMatchType.best_fields
    tieBreaker = 0.3
    operator = MatchOperator.OR
  }
}.pretty("Multi Match").let {
  println(it)
}
```

This prints:
 
```
Multi Match Found 2 results:
- 1.0925692 2 Banana
- 0.81427324 3 Green Beans

```

## Simple Query String

A simple query string parser that can query multiple fields

```kotlin
client.search(indexName) {
  query = simpleQueryString( "beans OR fruit", "name", "tags.txt" )
}.pretty("Multi Match").let {
  println(it)
}
```

This prints:
 
```
Multi Match Found 3 results:
- 0.81427324 3 Green Beans
- 0.4700036 1 Apple
- 0.4700036 2 Banana

```

## Query String Query

Similar to simple query string but with a more strict query language and less leniency.

```kotlin
client.search(indexName) {
  query = queryString( "(banana) OR (apple)", TestDoc::name)
}.pretty("Multi Match").let {
  println(it)
}
```

This prints:
 
```
Multi Match Found 2 results:
- 1.0925692 1 Apple
- 1.0925692 2 Banana

```

## Intervals query

The intervals query is a powerful but also complicated query. Be sure to refer to the Elasticsearch
manual. It allows you to query for terms using interval based match rules. These rules can get
quite complicated and you need to be careful with how they interact. For example the order matters
and the most minimal interval "wins". 

Here is a simple example

```kotlin
client.search(indexName) {
  query = intervals("name") {
    matchRule {
      query="green beans"
      maxGaps = 1
    }
  }
}.pretty("Combined fields").let {
  println(it)
}
```

This prints:
 
```
Combined fields Found 1 results:
- 0.5 3 Green Beans

```

You can combine multiple rules with `any_of`, or `all_of`.

```kotlin
client.search(indexName) {
  query = intervals("name") {
    allOfRule {
      intervals(
        matchRule {
          query = "green beans"
          maxGaps = 1
          withFilter {
            notContaining(matchRule { query="red" })
          }
        },
        prefixRule {
          prefix = "gr"
        }
      )
    }
  }
}.pretty("Combined fields").let {
  println(it)
}
```

This prints:
 
```
Combined fields Found 1 results:
- 0.5 3 Green Beans

```

## Combined fields query

```kotlin
client.search(indexName) {
  query = combinedFields( "banana fruit", "name^2","tags.txt") {
    operator = MatchOperator.AND
  }
}.pretty("Combined fields").let {
  println(it)
}
```

This prints:
 
```
Combined fields Found 1 results:
- 1.9290931 2 Banana

```



---

| [KT Search Manual](README.md) | Previous: [Search and Queries](Search.md) | Next: [Term Level Queries](TermLevelQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |