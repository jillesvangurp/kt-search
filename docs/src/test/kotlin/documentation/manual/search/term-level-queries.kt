package documentation.manual.search

import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.printStdOut
import documentation.sourceGitRepository

val termLevelQueriesMd = sourceGitRepository.md {
    val indexName = "docs-term-queries-demo"
    client.indexTestFixture(indexName)

    +"""
        The most basic queries in Elasticsearch are queries on individual terms.
    """.trimIndent()

    section("Term query") {
        example {
            client.search(indexName) {
                query = term(TestDoc::tags, "fruit")
            }.pretty("Term Query.").let { println(it) }
        }.printStdOut()

        +"""
            You can also do terms queries using numbers or booleans.
        """.trimIndent()
        example {
            client.search(indexName) {
                query = term(TestDoc::price, 0.80)
            }.pretty("Term Query.").let { println(it) }
        }

        +"""
            By default term queries are case sensitive. But you can turn that off.
        """.trimIndent()
        example {
            client.search(indexName) {
                query = term(TestDoc::tags, "fRuIt") {
                    caseInsensitive = true
                }
            }.pretty("Term Query.").let { println(it) }
        }.printStdOut()


    }
    section("Terms query") {
        example {
            client.search(indexName) {
                query = terms(TestDoc::tags, "fruit", "legumes")
            }.pretty("Terms Query.").let { println(it) }
        }.printStdOut()
    }
    section("Fuzzy query") {
        example {
            client.search(indexName) {
                query = fuzzy(TestDoc::tags, "friut") {
                    fuzziness = "auto"
                }
            }.pretty("Fuzzy Query.").let { println(it) }
        }

    }
    section("Prefix query") {
        example {
            client.search(indexName) {
                query = prefix(TestDoc::tags, "fru")
            }.pretty("Prefix Query.").let { println(it) }
        }.printStdOut()

    }
    section("Wildcard query") {
        example {
            client.search(indexName) {
                query = wildcard(TestDoc::tags, "f*")
            }.pretty("Wildcard Query.").let { println(it) }
        }.printStdOut()

    }
    section("RegExp query") {
        example {
            client.search(indexName) {
                query = regExp(TestDoc::tags, "(fruit|legumes)")
            }.pretty("RegExp Query.").let { println(it) }
        }.printStdOut()

    }
    section("Ids query") {
        example {
            client.search(indexName) {
                query = ids("1", "2")

            }.pretty("Ids Query.").let { println(it) }
        }.printStdOut()

    }
    section("Exists query") {
        example {
            client.search(indexName) {
                query = ids("1", "2")
            }.pretty("Exists Query.").let { println(it) }
        }.printStdOut()

    }
    section("Range query") {
        example {
            client.search(indexName) {
                query = range(TestDoc::price) {
                    gt=0
                    lte=100.0
                }

            }.pretty("Range Query.").let { println(it) }
        }.printStdOut()
    }
    section("Terms Set query") {
        example {
            client.search(indexName) {
                query = termsSet(TestDoc::tags, "fruit","legumes","foo") {
                    minimumShouldMatchScript = Script.create {
                        source = "2"
                    }
                }
            }.pretty("Terms Set Query").let { println(it) }
        }.printStdOut()
    }
}