package documentation.manual

import documentation.*
import documentation.manual.bulk.bulkMd
import documentation.manual.crud.crudMd
import documentation.manual.extending.extendingMd
import documentation.manual.gettingstarted.gettingStartedMd
import documentation.manual.gettingstarted.whatIsKtSearchMd
import documentation.manual.indexrepo.indexRepoMd
import documentation.manual.scripting.scriptingMd
import documentation.manual.search.deepPagingMd
import documentation.manual.search.searchMd
import documentation.manualOutputDir

val manualPages = listOf(
    Page("What is Kt-Search", "WhatIsKtSearch.md", manualOutputDir) to whatIsKtSearchMd,
    Page("Getting Started", "GettingStarted.md", manualOutputDir) to gettingStartedMd,
    Page("Searching", "Search.md", manualOutputDir) to searchMd,
    Page("Deep Paging", "DeepPaging.md", manualOutputDir) to deepPagingMd,
    Page("Document Manipulation", "crud.md", manualOutputDir) to crudMd,
    Page("Index Repository", "IndexRepository.md", manualOutputDir) to indexRepoMd,
    Page("Bulk Indexing", "BulkIndexing.md", manualOutputDir) to bulkMd,
    Page("Migrating from Es-Kotlin-Client", "Migrating.md", manualOutputDir) to loadMd("manual/gettingstarted/migrating.md"),
    Page("Extending the Search or Mapping DSL","ExtendingTheDSL.md", manualOutputDir) to extendingMd,
    Page("Scripting Search with KTS","Scripting.md", manualOutputDir) to scriptingMd,
)

val manualIndexMd = sourceGitRepository.md {
    section("Table of contents") {
        +manualPages.joinToString("\n") { (p, _) -> "- " + p.mdLink }.trimIndent()
    }
    section("About this Manual") {
        includeMdFile("outro.md")
    }
}