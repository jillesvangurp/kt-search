@file:Suppress("UnusedReceiverParameter", "unused")

package documentation.manual.extending

import com.jillesvangurp.jsondsl.*
import com.jillesvangurp.jsondsl.PropertyNamingConvention.ConvertToSnakeCase
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.printStdOut
import documentation.sourceGitRepository
import kotlin.reflect.KProperty

val extendingMd = sourceGitRepository.md {
    +"""
        This library includes Kotlin DSLs for querying, mapping and other functionality in Elasticsearch. 
        Elasticsearch has a very rich set of features and new ones are being added with every release. Keeping up with 
        that is hard and instead of doing that, we designed the Kotlin DSL to be easily extensible so that users 
        don't get stuck when e.g. a property they need is missing in the Kotlin DSL or when we have simply not gotten around
        to supporting a particular feature.
        
    """.trimIndent()

    section("Creating custom Json DSLs with JsonDsl") {
        +"""
            All of the DSLs in this library are based on `JsonDSL`, which lives in a separat module 
            and is intended to build custom JsonDSLs.
            To create your own JsonDSL, all you need to do is extend this class.
            
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
        """.trimIndent()

        example {
            // first we create support for representing the bar object
            class BarDsl : JsonDsl(
                // this is the default
                namingConvention = PropertyNamingConvention.AsIs
            ) {
                // you use property delegation to define properties
                var xxx by property<Long>()
                var yYy by property<Boolean>()
            }

            class FooDSL : JsonDsl(
                
            ) {
                var foo by property<String>()
                var bar by property<BarDsl>()

                // calling this function is nicer than doing
                // bar = BarDsl().apply {
                //   ....
                // }
                // but both are possible of course
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
                    this["missingFeature"] = JsonDsl().apply {
                        this["another"] = " field"
                        // if you need to you can override naming per field
                        put(
                            key = "camelCasing",
                            value = "may be forced",
                            namingConvention = PropertyNamingConvention.AsIs
                        )
                        // and you can use class properties
                        // if you want to keep things type safe
                        put(FooDSL::foo, "bar")
                        // and of course you can mix this with string literals
                        // via the RawJson value class
                        this["raw"] = RawJson("""{"foo":"bar"}""")
                    }
                    // you can also use withJsonDsl as a short hand
                    // for JsonDsl().apply
                    this["anotherObject"] = withJsonDsl {
                        this["value"] = "Priceless!"
                        // you can go completely schemaless if you need to
                        this["list"] = arrayOf(1,2,3)
                        this["list2"] = listOf(1,2,3)
                        // json list elements don't have to be of the
                        // same type even
                        this["map"] = mapOf("foo" to listOf(1,"2",3.0,
                            RawJson("""{"n":42}""")))
                    }
                }
            }.let {
                println(it.json(pretty = true))
            }
        }.printStdOut()

        +"""
            As you can see, JsonDsl is very flexible and you can use it to create model classes for 
            just about any json dialect. It's also a very minimalistic library with no library dependencies.
            It does not even depend on kotlinx.serialiation and instead uses its own serializer. 
            
            No parser is provided currently as this would be redundant for the intended use case of sending 
            serialized json to some API. But of course if people feel inspired, I will consider pull requests for this.
            
            Because JsonDsl implements `MutableMap<String, Any?>` (via delegation), you can manipulate the underlying 
            data structure easily. The `property` helper function is there to help you setup property delegation. It 
            knows how to deal with most. It has a few optional parameters that you can use to control the behavior.
        """.trimIndent()
    }
    section("Naming Convention and Naming Things") {
        +"""
            Most of the DSLs in Elasticsearch use snake casing (lower case with underscores). Of course, this goes
            against the naming conventions in Kotlin, where using camel case is preferred. You can configure the naming
            convention via the namingConvention parameter in JsonDSL. It defaults to snake casing as this is so pervasive
            in the Elasticsearch DSLs. If you don't want this, use the `AsIs` strategy. Or override the property name of
            your properties.
            
            Both the `SearchDSL` and the `IndexSettingsAndMappingsDSL` use the same names as the Elasticsearch DSLs 
            they model where-ever possible. Exceptions to this are Kotlin keywords and functions that are part of the 
            `JsonDsl` parent class. For example, `size` is part of the `Map` interface it implements and therefore we 
            can't use it to e.g. specify the query size attribute. 
        """.trimIndent()
    }

    section("Implementing your own queries") {
        +"""
            As an example, we'll use the `term` query implementation in this library.                       
        """.trimIndent()

        example(false) {
            class TermQueryConfig : JsonDsl() {
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

            fun QueryClauses.term(
                field: KProperty<*>,
                value: String,
                block: (TermQueryConfig.() -> Unit)? = null
            ) =
                TermQuery(field.name, value, block = block)

            fun QueryClauses.term(
                field: String,
                value: String,
                block: (TermQueryConfig.() -> Unit)? = null
            ) =
                TermQuery(field, value, block = block)
        }

        +"""
            The query dsl has this convention of wrapping various types of queries with a single 
            field object where the object key is the name of the query. Therefore, `TermQuery` extends `EsQuery`, which
            takes care of this. 
            
            All the query implementations have convenient extension functions on `SearchDSL`. This ensures that you
            can easily find the functions in any place that has a receiver block for `SearchDSL` and makes for a nice 
            developer experience when using the DSL.
            
            Term queries always have at least a field name and a value. This is why these are constructor 
            parameters on `TermQueryConfig`. Since specifying additional configuration is optional, the block in both 
            `term` functions defaults to null.
             
            Since, mostly you will have Kotlin data classes for your document models, there is a variant of the term 
            function that takes a `KProperty`. This allows you to use property references.
                       
            Here's an example of how you can use the term query:
        """.trimIndent()

        example {
            SearchDSL().apply {
                data class MyDoc(val keyword: String)
                query = bool {
                    should(
                        term("keyword", "foo") {
                            boost = 2.0
                        },
                        // we can use property references 
                        // instead of string literals
                        term(MyDoc::keyword, "foo") {
                            boost = 2.0
                        },
                        // the block is optional
                        term(MyDoc::keyword, "foo")
                    )
                }
            }.json(pretty = true)
        }

        +"""
            If you end up writing your own queries, of course please consider making a pull request.
        """.trimIndent()
    }

    section("Replacing ktor client (experimental)") {
        +"""
            Currently the client uses ktor-client and this is of course fine. However, we use a simple wrapper for this
            that you can write alternative implementations for. This is probably a case of severe YAGNI 
            (You Aint Gonna Need It), But if you need this for some reason or don't want to use ktor client, it's there.
             
            All you need to do for this is implement the `RestClient` interface.
        """.trimIndent()
    }

    section("Use just the DSL") {
        +"""
            The search client is of course a bit opinionated in how it is implemented and it picks an httpclient and 
            serialization framework that not everybody might agree with. If this bothers you, you can use just the 
            search DSL and easily build your own client using that.           
        """.trimIndent()
    }

    section("Custom serialization") {
        +"""
            There are cases where enums must be serialized and the text values are not valid enum values or 
            they don't follow Java/Kotlin naming conventions. In these cases implement `CustomValue` interface 
            like in this example:
            
            ```kotlin
            enum class Conflict(override val value: String) : CustomValue<String> {
                ABORT("abort"),
                PROCEED("proceed");
            }
            ```
                                    
            This interface can be of course implemented by any class not only enums.
        """.trimIndent()

    }
}