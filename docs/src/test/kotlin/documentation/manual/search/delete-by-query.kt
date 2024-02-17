package documentation.manual.search

import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.create
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.match
import documentation.sourceGitRepository

val deleteByQueryMd = sourceGitRepository.md {
    val indexName = "docs-term-queries-demo"
    client.indexTestFixture(indexName)

    +"""
        Delete by query is supported both on the client and the repository.    
    """.trimIndent()

    example {
        val repo = client.repository(indexName, TestDoc.serializer())
        repo.bulk(refresh = Refresh.WaitFor) {
            create(TestDoc("1", "banana", price = 2.0))
            create(TestDoc("1", "apple", price = 1.0))
        }

        repo.deleteByQuery {
            query = match(TestDoc::name, "apple")
        }.deleted
    }
    +"""
        If you need the optional query parameters on this API, use `client.deleteByQuery` instead.
    """.trimIndent()
}
