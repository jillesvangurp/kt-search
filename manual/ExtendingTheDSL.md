# Extending the Json DSLs 

This library includes Kotlin DSLs for querying, mapping and other functionality in Elasticsearch. 
ELasticsearch has a very rich set of features and new ones are constantly being added. Keeping up with 
that is hard and instead of doing that, we designed the Kotlin DSL to be easily extensible so that users 
don't get stuck when e.g. a field is missing in the Kotlin DSL or when we have simply not gotten around
to supporting a particular feature.

## Creating a custom DSL

All of the DSLs in this library are based on `JsonDSL`. To create your own JsonDSL, 
all you need to do is extend this class.

For example, consider this bit of Json:

```json
{
    "foo": "bar",
    "bar": {
        "xxx": 1234,
        "yyy": true                    
    }
}
```

To create a DSL for this, you simply create a new class:

```kotlin
class BarDsl : JsonDsl(
  // this is the default
  namingConvention = PropertyNamingConvention.AsIs
) {
  var xxx by property<Long>()
  var yYy by property<Boolean>()
}

class FooDSL : JsonDsl(
  namingConvention = ConvertToSnakeCase
) {
  var foo by property<String>()

  fun bar(block: BarDsl.() -> Unit) {
    this["bar"] = BarDsl().apply(block)
  }
}

fun foo(block: FooDSL.() -> Unit) = FooDSL().apply(block)


foo {
  foo = "Hello World"
  bar {
    xxx = 123
    yYy = false
    // you can just improvise things that aren't part of your DSL
    this["zzz"] = listOf(1, 2, 3)
    this["missingFeature"] = JsonDsl(
      namingConvention = ConvertToSnakeCase
    ).apply {
      this["another"] = " field"
      put(
        key = "camelCasing",
        value = "may be forced",
        namingConvention = PropertyNamingConvention.AsIs
      )
      // you can also refer class properties
      // if you want to keep things type safe
      put(FooDSL::foo, "bar")
    }
  }
}.let {
  println(it.json(pretty = true))
}
```

Captured Output:

```
{
  "foo": "Hello World",
  "bar": {
  "xxx": 123,
  "yYy": false,
  "zzz": [
    1, 
    2, 
    3
  ],
  "missingFeature": {
    "another": " field",
    "camelCasing": "may be forced",
    "foo": "bar"
  }
  }
}

```

## Naming Convention and Naming Things

Most of the DSLs in Elasticsearch use snake casing (lower case with underscores). Of course, this goes
against the naming conventions in Kotlin, where using camel case is preferred. You can configure the naming
convention via the namingConvention parameter in JsonDSL.

Both the `SearchDSL` and the `IndexSettingsAndMappingsDSL` use the same names as the Elasticsearch DSLs 
they model whereever possible. Exceptions to this are Kotlin keywords and functions that are part of the 
`JsonDsl` parent class. For example, `size` is part of the `Map` interface it implements and therefore we 
can't use it to e.g. specify the query size attribute.

## Implementing your own queries

As an example, we'll use the `term` query implementation in this library.                       

```kotlin
class TermQueryConfig : JsonDsl(namingConvention = ConvertToSnakeCase) {
  var value by property<String>()
  var boost by property<Double>()
}

@SearchDSLMarker
class TermQuery(
  field: String,
  value: String,
  termQueryConfig: TermQueryConfig = TermQueryConfig(),
  block: (TermQueryConfig.() -> Unit)? = null
) : ESQuery("term") {

  init {
    put(field, termQueryConfig, PropertyNamingConvention.AsIs)
    termQueryConfig.value = value
    block?.invoke(termQueryConfig)
  }
}

fun SearchDSL.term(
  field: KProperty<*>,
  value: String,
  block: (TermQueryConfig.() -> Unit)? = null
) =
  TermQuery(field.name, value, block = block)

fun SearchDSL.term(
  field: String,
  value: String,
  block: (TermQueryConfig.() -> Unit)? = null
) =
  TermQuery(field, value, block = block)
```

The query dsl has this convention of wrapping various types of queries with a single 
field object where the object key is the name of the query. Therefore, `TermQuery` extends `EsQuery`, which
takes care of this. Additionally it sets snake casing as the naming convention as most of the DSL uses that.

All the query implementations have convenient extension functions on `SearchDSL`. This ensures that you
can easily find the functions in any place that has a receiver block for `SearchDSL`.

Term queries always have at least a field name and a value. This is why these are constructor 
parameters on `TermQueryConfig`. Since specifying additional configuration is optional, the block in both 
`term` functions defaults to null.
 
Since, mostly you will have Kotlin data classes for your document models, there is a variant of the term 
function that takes a `KProperty`.  
           
Here's an example of how you can use the term query:

```kotlin
SearchDSL().apply {
  data class MyDoc(val keyword: String)
  query = bool {
    should(
      term(MyDoc::keyword, "foo") {
        boost = 2.0
      },
      term("keyword", "foo") {
        boost = 2.0
      },
      term("keyword", "foo")
    )
  }
}.json(pretty = true)
```

->

```
{
  "query": {
  "bool": {
    "should": [
    {
      "term": {
      "keyword": {
        "value": "foo",
        "boost": 2.0
      }
      }
    }, 
    {
      "term": {
      "keyword": {
        "value": "foo",
        "boost": 2.0
      }
      }
    }, 
    {
      "term": {
      "keyword": {
        "value": "foo"
      }
      }
    }
    ]
  }
  }
}
```

