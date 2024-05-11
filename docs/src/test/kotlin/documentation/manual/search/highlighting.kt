package documentation.manual.search

import com.jillesvangurp.ktsearch.DEFAULT_PRETTY_JSON
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.searchdsls.querydsl.Fragmenter
import com.jillesvangurp.searchdsls.querydsl.Type
import com.jillesvangurp.searchdsls.querydsl.highlight
import com.jillesvangurp.searchdsls.querydsl.match
import documentation.sourceGitRepository
import kotlinx.serialization.encodeToString

val highlightingMd = sourceGitRepository.md {
    val indexName = "docs-term-highlighting-demo"
    client.indexTestFixture(indexName)

    +"""
        Highlighting allows you to show to your users why particular results are matching a query.
    """.trimIndent()

    example {
        client.search(indexName) {
            query=match(TestDoc::name,"bananana") {
                fuzziness="AUTO"
            }
            // create a highlight on the name field with default settings
            highlight {
                add(TestDoc::name)
            }
        }
    }.let {
        +DEFAULT_PRETTY_JSON.encodeToString(it.result.getOrThrow())
    }
    +"""
        Of course you can customize how highlighting works:
    """.trimIndent()

    example {
        client.search(indexName) {
            query=match(TestDoc::name,"bananana") {
                fuzziness="AUTO"
            }
            // create a highlight on the name field with default settings
            highlight {
                // use some alternative tags instead of the defaults
                preTags="<pre>"
                postTags="</pre>"

                add(TestDoc::name) {
                    // configure some per field settings
                    type = Type.plain
                    fragmenter=Fragmenter.span
                }
            }
        }
    }.let {
        +DEFAULT_PRETTY_JSON.encodeToString(it.result.getOrThrow())
    }
}
