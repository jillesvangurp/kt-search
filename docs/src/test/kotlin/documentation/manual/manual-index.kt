package documentation.manual

import documentation.*
import documentation.manual.bulk.bulkMd
import documentation.manual.bulk.indexmanagement.dataStreamsMd
import documentation.manual.bulk.indexmanagement.indexManagementMd
import documentation.manual.crud.crudMd
import documentation.manual.extending.extendingMd
import documentation.manual.gettingstarted.gettingStartedMd
import documentation.manual.gettingstarted.whatIsKtSearchMd
import documentation.manual.indexrepo.indexRepoMd
import documentation.manual.scripting.scriptingMd
import documentation.manual.search.*

enum class ManualPages(title: String = "") {
    WhatIsKtSearch("What is Kt-Search"),
    GettingStarted("Getting Started"),
    Search("Search and Queries"),
    CompoundQueries("Compound Queries"),
    TextQueries("Text Queries"),
    Aggregations("Aggregations"),
    DeepPaging("Deep Paging Using search_after and scroll"),
    DocumentManipulation("Document Manipulation"),
    IndexRepository("Index Repository"),
    BulkIndexing("Efficiently Ingest Content Using Bulk Indexing"),
    IndexManagement("Indices, Settings, Mappings, and Aliases"),
    DataStreams("Creating Data Streams"),
    Migrating("Migrating from the old Es Kotlin Client"),
    ExtendingTheDSL("Extending the Json DSLs"),
    Scripting("Using Kotlin Scripting"),
    Jupyter("Jupyter Notebooks"),
    ;

    val page by lazy {
        Page(title,"${name}.md", manualOutputDir)
    }
}

val manualPages = listOf(
    ManualPages.WhatIsKtSearch.page to whatIsKtSearchMd,
    ManualPages.GettingStarted.page to gettingStartedMd,
    ManualPages.IndexManagement.page to indexManagementMd,
    ManualPages.Search.page to searchMd,
    ManualPages.CompoundQueries.page to compoundQueriesMd,
    ManualPages.TextQueries.page to textQueriesMd,
    ManualPages.Aggregations.page to aggregationsMd,
    ManualPages.DeepPaging.page to deepPagingMd,
    ManualPages.DocumentManipulation.page to crudMd,
    ManualPages.IndexRepository.page to indexRepoMd,
    ManualPages.BulkIndexing.page to bulkMd,
    ManualPages.DataStreams.page to dataStreamsMd,
    ManualPages.Migrating.page to loadMd("manual/gettingstarted/migrating.md"),
    ManualPages.ExtendingTheDSL.page to extendingMd,
    ManualPages.Scripting.page to scriptingMd,
    ManualPages.Jupyter.page to loadMd("manual/jupyter/jupyter.md"),
)

val manualIndexMd = sourceGitRepository.md {
    includeMdFile("../projectreadme/oneliner.md")

    includeMdFile("intro.md")
    section("Table of contents") {
        +manualPages.joinToString("\n") { (p, _) -> "- " + p.mdLink }.trimIndent()
    }
    section("About this Manual") {
        includeMdFile("outro.md")
    }
}