package com.jillesvangurp.eskotlinwrapper.documentation.manual

import com.jillesvangurp.eskotlinwrapper.documentation.Thing
import com.jillesvangurp.eskotlinwrapper.documentation.sourceGitRepository
import com.jillesvangurp.searchdsls.querydsl.TermQuery
import com.jillesvangurp.eskotlinwrapper.withTestIndex
import com.jillesvangurp.jsondsl.dslObject
import com.jillesvangurp.jsondsl.withJsonDsl

val dslCustomizationsMd by withTestIndex<Thing, Lazy<String>> {
    sourceGitRepository.md {
        +"""
            The provided Kotlin DSLs for mappings, settings, or querying are nice but don't cover 100% of what Elasticsearch provides. And Elasticsearch keeps on adding new things to their client library with each new release so it is quite hard for us to keep up with that. So, we made a choice to focus on supporting the commonly used things; or at least things we ourselves use. 
            
            Luckily, it is quite easy to work around this and either extend our DSLs to add support for new things, or simply fall back to using the underlying functionality for constructing arbitrary JSON structures that our DSLs are built on.
            
            ## How does this work?
            
            Elasticsearch provides a REST api that accepts JSON. So the goal of our DSLs is to programmatically construct an object structure that can be serialized to a JSON that matches what Elasticsearch expects. This JSON is sent over the network via the Elasticsearch `LowLevelClient`.
            
            For serialization, we piggy back on `XContent` which is the built in framework that the elasticsearch client uses for dealing with JSON content. 
            
            To do this, we make use of a few useful Kotlin language features. One of these is interface delegation, which we use to implement a special base class: `MapBackedProperties`. That implements a simple `Map<String, Any>` using interface delegation. 
            
            In most places in the DSL where you are provided a block, the receiver is an object of this type; or a derived class with that type as its parent. This enables us to define properties that are under the hood simply inserted into the map delegate. 
            
            Simply extending this class makes it possible to define class properties that delegate to this map. Additionally, enables users of your custom class to simply put values to the map directly for anything that you forgot to add as a delegated property.
            
            ## Example: the TermQuery Implementation
            
            As an example, here is the implementation of the TermQuery in our library:
              
        """.trimIndent()

        // FIXME file moved
//        snippetFromSourceFile("src/main/kotlin/com/jillesvangurp/eskotlinwrapper/dsl/term-level-queries.kt","term-query")

        +"""
            `TermQuery` extends a base class called `ESQuery`, which in turn is a MapBackedProperties with a single field (the query name) mapped to another `MapBackedProperties` (the query details). From there on it is pretty straightforward: TermQuery has two constructor parameters: `field` and `value`. field is used as the key to yet another `MapBackedProperties` object with the `TermConfiguration` which in this case contains things like the value and the boost.
            
            Finally, note that we added a `SearchDSL.term` extension function this makes it easy to find supported queries via autocomplete in your IDE. And of course you can add your own extension functions as well.
            
        """.trimIndent()

        block(runBlock = true) {
            val termQuery = TermQuery("myField", "someValue") {
                boost = 10.0
            }

            println(termQuery.toString())
        }

        +"""
            As you can see, `TermQuery` inherits a convenient `toString` implementation that prints JSON. This is useful for debugging and logging if you ar programmatically creating queries using the DSL.
            
            Also note how we use delegated properties in the `TermConfiguration`. This allows you to set values to these properties when using the DSL using a simple assignment.
            
            But suppose we forgot to add something here and you need to set a (non existing) property named foo on a the term query configuration:
            
        """
        block(runBlock = true) {
            val termQuery = TermQuery("myField", "someValue") {
                // we support boost
                boost=2.0
                // but foo is not something we support
                // but we can still add it to the TermQueryConfig
                // because it is backed by MapBackedProperties
                // and implements Map<String, Any>
                this["foo"] = "bar"
            }

            println(termQuery)
        }
        +"""
            Obviously, Elasticsearch would reject this query with a bad request because there is no `foo` property for the term query.
            
            ## Creating more complex JSON
            
            You can construct arbitrary json pretty easily. If you want to create a json object, you can use `mapProps`
        """.trimIndent()

        block(runBlock = true) {
            val aCustomObject = withJsonDsl {
                // mixed type lists
                this["icanhasjson"] = listOf(1,2,"4")
                this["meaning_of_life"] = 42
                this["nested_object"] = dslObject {
                    this["another"] = dslObject {
                        this["nested_object_prop"] = 42
                    }
                    this["some more stuff"] = "you get the point"
                }
            }

            println(aCustomObject)
        }

        +"""
            You can mix different types in the map. To enable XContent to serialize things, we use the `writeAny` extension function as part of the `toXContent` function on `MapBackedProperties`. That function currently supports most primitives, maps, enums, iterables, and more.
            
            ## Snake Case vs. Camel Case
            
            Most of the APIs in Elasticsearch expect snake case (lower case and underscores) in json keys used in the DSLs. Kotlin on the other hand uses camel case as a convention for things like variable names. 
            
            Therefore, `MapBackedProperties` uses a `put` implementation that snake cases field values. For some things like field names this is not desirable and you should use the `putNoSnakeCase` method instead to bypass this behavior. 
            
            ## XContent extensions
            
            `XContent` is something that Elasticsearch, and the Elasticsearch Java client uses internally for dealing with JSON content. As this may be quite alien to people used to dealing with e.g. Jackson, GSon, or kotlinx-serialization, this library provides some extension functions to make dealing with XContent straightforward. Mostly the problem boils down to somehow providing XContent to java library functions that expect some kind of json structure as a parameter. 
            
            Most of these Java functions come with a builder that will typically accept either a Java builder that produces the right XContent, or just any XContent object; typically via a builder function called `source`.
            
            As mentioned in the DSL function, you can of course use these builders. But the builder pattern is of course not that nice from Kotlin, which is the reason we provide a Kotlin DSL as well.
            
            But sometimes you just want to bypass the builders and provide some json straight to Elasticsearch. For this, we provide a few `source` extension functions on `SearchRequest`, `CountRequest`, and a few other requests that take either a string (or Kotlin multi line string) or a reader with raw json. 
            
            The `MapBackedProperties` mentioned above of course implements the `ToXContent` interface, which allows us to use any instance of that to be passed to the before mentioned source functions. 
            
            ## Extending the DSL
            
            We've covered most of the basic term, text, and compound queries in the search DSL and most of their configuration properties. Currently we are adding to this only on a need to have basis. However, should you have a need for something we do not yet provide, it is very easy to extend the DSL.
            Simply extend ESQuery and use delegated properties as explained above. Also don't forget to add an extension function to `SearchDSL`. Of course pull requests with new query types or improvements to the existing ones are welcome. 
            
            There are also other client APIs in the Elasticsearch client that have their own DSL we currently don't support. For these, you can of course also create your own DSLs. And of course pull requests for this are very much appreciated as well.
            
        """.trimIndent()


    }
}