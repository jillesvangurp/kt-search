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
        suspendingExample {
            client.search(indexName) {
                query = term(TestDoc::tags, "fruit")
            }.pretty("Term Query.").let { println(it) }
        }.printStdOut()

    }
    section("Terms query") {
        suspendingExample {
            client.search(indexName) {
                query = terms(TestDoc::tags, "fruit", "legumes")
            }.pretty("Terms Query.").let { println(it) }
        }.printStdOut()
    }
    section("Fuzzy query") {
        suspendingExample {
            client.search(indexName) {
                query = fuzzy(TestDoc::tags, "friut") {
                    fuzziness = "auto"
                }
            }.pretty("Fuzzy Query.").let { println(it) }
        }

    }
    section("Prefix query") {
        suspendingExample {
            client.search(indexName) {
                query = prefix(TestDoc::tags, "fru")
            }.pretty("Prefix Query.").let { println(it) }
        }.printStdOut()

    }
    section("Wildcard query") {
        suspendingExample {
            client.search(indexName) {
                query = wildcard(TestDoc::tags, "f*")
            }.pretty("Wildcard Query.").let { println(it) }
        }.printStdOut()

    }
    section("RegExp query") {
        suspendingExample {
            client.search(indexName) {
                query = regExp(TestDoc::tags, "(fruit|legumes)")
            }.pretty("RegExp Query.").let { println(it) }
        }.printStdOut()

    }
    section("Ids query") {
        suspendingExample {
            client.search(indexName) {
                query = ids("1", "2")

            }.pretty("Ids Query.").let { println(it) }
        }.printStdOut()

    }
    section("Exists query") {
        suspendingExample {
            client.search(indexName) {
                query = ids("1", "2")
            }.pretty("Exists Query.").let { println(it) }
        }.printStdOut()

    }
    section("Range query") {
        suspendingExample {
            client.search(indexName) {
                query = range(TestDoc::price) {
                    gt=0
                    lte=100.0
                }

            }.pretty("Range Query.").let { println(it) }
        }.printStdOut()
    }
    section("Terms Set query") {
        suspendingExample {
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