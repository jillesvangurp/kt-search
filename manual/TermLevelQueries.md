# Term Level Queries 

| [KT Search Manual](README.md) | Previous: [Text Queries](TextQueries.md) | Next: [Compound Queries](CompoundQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |

---                

The most basic queries in Elasticsearch are queries on individual terms.

## Term query

```kotlin
client.search(indexName) {
  query = term(TestDoc::tags, "fruit")
}.pretty("Term Query.").let { println(it) }
```

Captured Output:

```
Term Query. Found 2 results:
- 0.4700036 1 Apple
- 0.4700036 2 Banana

```

## Terms query

```kotlin
client.search(indexName) {
  query = terms(TestDoc::tags, "fruit", "legumes")
}.pretty("Terms Query.").let { println(it) }
```

Captured Output:

```
Terms Query. Found 3 results:
- 1.0 1 Apple
- 1.0 2 Banana
- 1.0 3 Green Beans

```

## Fuzzy query

```kotlin
client.search(indexName) {
  query = fuzzy(TestDoc::tags, "friut") {
    fuzziness = "auto"
  }
}.pretty("Fuzzy Query.").let { println(it) }
```

Captured Output:

```
Fuzzy Query. Found 2 results:
- 0.3760029 1 Apple
- 0.3760029 2 Banana

```

## Prefix query

```kotlin
client.search(indexName) {
  query = prefix(TestDoc::tags, "fru")
}.pretty("Prefix Query.").let { println(it) }
```

Captured Output:

```
Prefix Query. Found 2 results:
- 1.0 1 Apple
- 1.0 2 Banana

```

## Wildcard query

```kotlin
client.search(indexName) {
  query = wildcard(TestDoc::tags, "f*")
}.pretty("Wildcard Query.").let { println(it) }
```

Captured Output:

```
Wildcard Query. Found 2 results:
- 1.0 1 Apple
- 1.0 2 Banana

```

## RegExp query

```kotlin
client.search(indexName) {
  query = regExp(TestDoc::tags, "(fruit|legumes)")
}.pretty("RegExp Query.").let { println(it) }
```

Captured Output:

```
RegExp Query. Found 3 results:
- 1.0 1 Apple
- 1.0 2 Banana
- 1.0 3 Green Beans

```

## Ids query

```kotlin
client.search(indexName) {
  query = ids("1", "2")

}.pretty("Ids Query.").let { println(it) }
```

Captured Output:

```
Ids Query. Found 2 results:
- 1.0 1 Apple
- 1.0 2 Banana

```

## Exists query

```kotlin
client.search(indexName) {
  query = ids("1", "2")
}.pretty("Exists Query.").let { println(it) }
```

Captured Output:

```
Exists Query. Found 2 results:
- 1.0 1 Apple
- 1.0 2 Banana

```

## Range query

```kotlin
client.search(indexName) {
  query = range(TestDoc::price) {
    gt=0
    lte=100.0
  }

}.pretty("Range Query.").let { println(it) }
```

Captured Output:

```
Range Query. Found 3 results:
- 1.0 1 Apple
- 1.0 2 Banana
- 1.0 3 Green Beans

```

## Terms Set query

```kotlin
// FIXME not implemented yet
```



---

| [KT Search Manual](README.md) | Previous: [Text Queries](TextQueries.md) | Next: [Compound Queries](CompoundQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |