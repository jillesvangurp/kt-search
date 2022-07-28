package documentation.manual

import documentation.*
import documentation.manual.bulk.bulkMd
import documentation.manual.extending.extendingMd
import documentation.manual.gettingstarted.gettingStartedMd
import documentation.manual.gettingstarted.whatIsKtSearchMd
import documentation.manual.indexrepo.indexRepoMd
import documentation.manualOutputDir

val manualPages = listOf(
    Page("What is Kt-Search", "WhatIsKtSearch.md", manualOutputDir) to whatIsKtSearchMd,
    Page("Getting Started", "GettingStarted.md", manualOutputDir) to gettingStartedMd,
    Page("IndexRepository", "IndexRepository.md", manualOutputDir) to indexRepoMd,
    Page("Bulk Indexing", "BulkIndexing.md", manualOutputDir) to bulkMd,
    Page("Migrating from Es-Kotlin-Client", "Migrating.md", manualOutputDir) to loadMd("manual/gettingstarted/migrating.md"),
    Page("Extending the Search or Mapping DSL","ExtendingTheDSL.md", manualOutputDir) to extendingMd
)

val manualIndexMd = sourceGitRepository.md {
    section("Table of contents") {
        +manualPages.joinToString("\n") { (p, _) -> "- " + p.mdLink }.trimIndent()
    }
    section("About this Manual") {
        includeMdFile("outro.md")
    }
}