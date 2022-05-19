package com.jillesvangurp.eskotlinwrapper.documentation

import com.jillesvangurp.eskotlinwrapper.withTestIndex
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.match
import com.jillesvangurp.searchdsls.querydsl.term
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.search.configure
import org.elasticsearch.client.asyncIndexRepository
import org.elasticsearch.client.configure
import org.elasticsearch.client.indexRepository

val readmeMd by withTestIndex<Thing, Lazy<String>>(index = "manual", refreshAllowed = true, createIndex = false) {
    sourceGitRepository.md {

        includeMdFile("readme-introduction.md")
        +"""            
            ## Example
            
            This example is a bit more complicated than a typical hello world but more instructive 
            than just putting some objects in a schema less index. Which of course is something you should not do. The idea here
            is to touch on most topics a software engineer would need to deal with when creating a new project using Elasticsearch:
            
            - figuring out how to create an index and define a mapping
            - populating the index with content using the bulk API
            - querying data 
        """
        snippetBlockFromClass(Thing::class, "thing-class")

        block(runBlock = false) {
            // create a Repository
            // with the default jackson model reader and writer
            // (you can use something else by overriding default values of the args)
            val thingRepository = esClient.indexRepository<Thing>(
                index = "things",
                // you have to opt in to refreshes, bad idea to refresh in production code
                refreshAllowed = true
            )

            // let the Repository create the index with the specified mappings & settings
            thingRepository.createIndex {
                // we use our settings DSL here
                // you can also choose to use a source block with e.g. multiline strings
                // containing json
                configure {

                    settings {
                        replicas = 0
                        shards = 2
                    }
                    mappings {
                        // mappings DSL, most common field types are supported
                        text("name")
                        // floats, longs, doubles, etc. should just work
                        number<Int>("amount")
                    }
                }
            }

            // lets create a few Things
            thingRepository.index("1", Thing("foo", 42))
            thingRepository.index("2", Thing("bar", 42))
            thingRepository.index("3", Thing("foobar", 42))

            // make sure ES commits the changes so we can search
            thingRepository.refresh()

            val results = thingRepository.search {
                configure {
                    // added names to the args for clarity here, but optional of course
                    query = match(field = Thing::name, query = "bar")
                }
            }
            // results know hot deserialize Things
            results.mappedHits.forEach {
                println(it.name)
            }
            // but you can also access the raw hits of course
            results.searchHits.forEach {
                println("hit with id ${it.id} and score ${it.score}")
            }

            // putting things into an index 1 by 1 is not scalable
            // lets do some bulk inserts with the Bulk DSL
            thingRepository.bulk {
                // we are passed a BulkIndexingSession<Thing> in the block as 'this'

                // we will bulk re-index the objects we already added with
                // a scrolling search. Scrolling searches work just
                // like normal searches (except they are not ranked)
                // all you do is set scrolling to true and you can
                // scroll through billions of results.
                val sequence = thingRepository.search(scrolling = true) {
                    configure {
                        from = 0
                        // when scrolling, this is the scroll page size
                        resultSize = 10
                        query = bool {
                            should(
                                // you can use strings
                                match("name", "foo"),
                                // or property references
                                match(Thing::name, "bar"),
                                match(Thing::name, "foobar")
                            )
                        }
                    }
                }.hits
                // hits is a Sequence<Pair<SearchHit,Thing?>> so we get both the hit and
                // the deserialized value. Sequences are of course lazy and we fetch
                // more results as you process them.
                // Thing is nullable because Elasticsearch allows source to be
                // disabled on indices.
                sequence.forEach { (esResult, deserialized) ->
                    index(
                        esResult.id, deserialized.copy(amount = deserialized.amount + 1),
                        // allow updates of existing things
                        create = false
                    )
                }
            }
        }
        +"""
            ## Co-routines
            
            Using co-routines is easy in Kotlin. Mostly things work almost the same way. Except everything is non blocking
            and asynchronous, which is nice. In other languages this creates all sorts of complications that Kotlin largely avoids.
            
            The Java client in Elasticsearch has support for non blocking IO. We leverage this to add our own suspending
            calls using extension functions via our gradle code generation plugin. This runs as part of the build process for this
             library so there should be no need for you to use  this plugin. 

            The added functions have the same signatures as their blocking variants. Except of course they have the 
            word async in their names and the suspend keyword in front of them. 
            
            We added suspending versions of the `Repository` and `BulkSession` as well, so either blocking or non
            blocking. It's up to you.
        """

        block {
            // we reuse the index we created already to create an ayncy index repo
            val repo = esClient.asyncIndexRepository<Thing>(
                index = "things",
                refreshAllowed = true
            )
            // co routines require a CoroutineScope, so let use one
            runBlocking {
                // lets create some more things; this works the same as before
                repo.bulk {
                    // but we now get an AsyncBulkIndexingSession<Thing>
                    (1..1000).forEach {
                        index(
                            "$it",
                            Thing("thing #$it", it.toLong())
                        )
                    }
                }
                // refresh so we can search
                repo.refresh()
                // if you are wondering, yes this is almost identical
                // to the synchronous version above.
                // However, we now get an AsyncSearchResults back
                val results = repo.search {
                    configure {
                        query = term("name.keyword", "thing #666")
                    }
                }
                // However, mappedHits is now a Flow instead of a Sequence
                results.mappedHits.collect {
                    // collect is part of the kotlin Flow API
                    // this is one of the few parts where the API is different
                    println("we've found a thing with: ${it.amount}")
                }
            }
        }

        includeMdFile("readme-outtro.md")
    }
}
