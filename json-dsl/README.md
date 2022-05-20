# Json DSL

JsonDsl is a multi platform kotlin library to allow people to create Kotlin DSLs that serialize to json.

It is part of a larger group of libraries that together provide a multi platform client for Elasticsearch and its fork Opensearch. The goal of this library is to provide a portable building block that can be used for this as well as other multi platform json apis we may support in the future.

The search dsl is not part of this library and is implemented elsewhere.

The builtin json function uses a very simplistic serializer. However, the `JsonDslSerializer` interface can be overridden to serialize your dsl with your json framework of choice. E.g. the RestHighLevelClient uses its internal XContent framework. If you use spring, you might prefer Jackson. Or if you are targeting Kotlin native or js, you might prefer using `kotlinx-serialization`. 

## How does it work

The `JsonDsl` base class can be extended to create your own DSL classes. It uses interface delegation to implement a map of properties. This map is what is serialized to Json. Using a map gives us some flexibility to manipulate the names at construction time (rather than at serialization time).

You add elements to your DSL via property delegation. Additionally, you can create the naming conventions used during serialization. For example, many Json APIs snake case their property names whereas Kotlin uses camel cased property names.

## Example

This minimal example defines a simple DSL.

```kotlin
class MyDsl:JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var foo by property<String>()
    // will be snake_cased in the json
    var meaningOfLife by property<Int>()
    // we override the property name here
    var l by property<List<Any>>("a_custom_list")
    var m by property<Map<Any,Any>>()
}

val myDsl = MyDsl().apply {
    foo = "Hello\tWorld"
    meaningOfLife = 42
    l = listOf("1", 2, 3.0)
    m = mapOf(42 to "fortytwo")
}
println(myDsl.json(pretty=true))
```

becomes 

```json
{
  "foo": "Hello\tWorld",
  "meaning_of_life": 42,
  "a_custom_list": [
    "1", 
    2, 
    3.0
  ],
  "m": {
    "42": "fortytwo"
  }
}
```

## Limitations and contributing

Note, that this is a multi-platform library and that we intend to keep it that way. Any contributions that add platform specific code cannot be accepted for this reason. For the same reason, we intend to keep the list of run-time dependencies restricted to just the Kotlin standard library.