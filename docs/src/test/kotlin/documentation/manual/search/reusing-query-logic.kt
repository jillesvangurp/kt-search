package documentation.manual.search

import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.searchdsls.querydsl.QueryClauses
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.constructQueryClause
import com.jillesvangurp.searchdsls.querydsl.matchPhrase
import com.jillesvangurp.searchdsls.querydsl.term
import documentation.sourceGitRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

val reusingQueryLogicMd = sourceGitRepository.md {
    val indexName = "docs-search-demo"
    client.indexTestFixture(indexName)

    +"""
            The Search DSL allows you to construct queries via helper extension functions on 
            `SearchDSL` and the `QueryClauses` interface. This makes it easy to use with the search
            functions in the client or `IndexRepository`.
            
            Here's a simple example of a query that looks for the "best thing ever" suitable for work.
             We'll build out this example to show you how to 
        """.trimIndent()

    example {
        client.search(indexName) {
            query = bool {
                filter(term(TestDoc::tags, "work"))
                must(matchPhrase(TestDoc::name, "best thing ever"))
            }
        }
    }

    +"""
        In this example, bool, and match are all extension functions on `QueryClauses` and 
        the receiver block has a `this` of type `SearchDSL`, which implements `QueryClauses`.
    """

    section("Adding some logic") {
        +"""    
            Because it is all kotlin, you can programmatically construct complex queries, 
            use conditional logic, maybe look up a few things and generate clauses for that, etc. 
            All while keeping things type safe.
            
            Let's make our example a bit more interesting by taking time into account.
        """.trimIndent()

        example {
            val now = Clock.System.now()

            client.search(indexName) {
                query = bool {
                    val hour = now.toLocalDateTime(TimeZone.UTC).hour
                    if (hour in 9..17) {
                        filter(
                            term(TestDoc::tags, "work")
                        )
                    } else {
                        filter(
                            term(TestDoc::tags, "leisure")
                        )
                    }
                    must(matchPhrase(TestDoc::name, "best thing ever"))
                }
            }
        }

        +"""
            This simple example creates different queries depending on the time. 
            
            After all, what's the best thing ever probably depends on some context. 
            Which would include the time of day.           
        """.trimIndent()
    }


    section("Writing your own extension function") {
        +"""
            Conditional logic like above can get complicated pretty quickly. To keep that under control,
            you will want to use all the usual strategies such as extracting functions, 
            using object oriented designs, or doing things with extension functions, delegation, etc.
                                   
            Note, that the example above uses UTC. Obviously we'd want to take things like 
            Timezones into account as well. And maybe the user has a calendar and a 
            busy schedule. There are all sorts of things to consider!
            
            The point is that queries can get complicated quite quickly and you need 
            good mechanisms to keep things clean and structured. Kt-search provides you all the tools
            you need to stay on top of this.
        """.trimIndent()

        example {
            fun QueryClauses.timeTagClause() = term(TestDoc::tags,
                if(Clock.System.now().toLocalDateTime(TimeZone.UTC).hour in 9..17) {
                    "work"
                } else {
                    "leisure"
                })

            client.search(indexName) {
                bool {
                    filter(timeTagClause())
                    must(matchPhrase(TestDoc::name, "best thing ever"))
                }
            }

            client.search(indexName) {
                bool {
                    filter(timeTagClause())
                    must(matchPhrase(TestDoc::name, "something I need"))
                }
            }
        }
        +"""
            Here's an improved version of the query. We extracted the logic that creates the term clause
            and we can now easily add it to different queries.
        """.trimIndent()
    }
    section("Helper functions") {
        +"""
            The above example is nice but it is not always practical to rely on having a receiver object.
        """.trimIndent()

        example {
            class MyFancyQueryBuilder {
                fun timeTagClause() = constructQueryClause {
                    term(
                        TestDoc::tags,
                        if (Clock.System.now().toLocalDateTime(TimeZone.UTC).hour in 9..17) {
                            "work"
                        } else {
                            "leisure"
                        }
                    )
                }
            }

            val qb = MyFancyQueryBuilder()

            client.search(indexName) {
                bool {
                    filter(qb.timeTagClause())
                    must(matchPhrase(TestDoc::name, "best thing ever"))
                }
            }
        }
        +"""
            Here we introduce a helper class. You could imagine adding all sorts of logic and helper classes.
                       
            Of course using a class creates the problem that using that it complicates using extension functions.
             
            The `constructQueryClause` helper function provides a solution to that problem. It allows you to create
            functions that take a block that expects a `QueryClauses` object and returns an `ESQuery`. 
            And it then creates an anonymous QueryClauses object for you uses that to call your block.
        """.trimIndent()
    }
}