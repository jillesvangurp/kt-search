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
    TextQueries("Text Queries"),
    TermLevelQueries("Term Level Queries"),
    CompoundQueries("Compound Queries"),
    Aggregations("Aggregations"),
    DeepPaging("Deep Paging Using search_after and scroll"),
    DeleteByQuery("Deleting by query"),
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
    ManualPages.WhatIsKtSearch to whatIsKtSearchMd,
    ManualPages.GettingStarted to gettingStartedMd,
    ManualPages.IndexManagement to indexManagementMd,
    ManualPages.Search to searchMd,
    ManualPages.TextQueries to textQueriesMd,
    ManualPages.TermLevelQueries to termLevelQueriesMd,
    ManualPages.CompoundQueries to compoundQueriesMd,
    ManualPages.Aggregations to aggregationsMd,
    ManualPages.DeepPaging to deepPagingMd,
    ManualPages.DeleteByQuery to deleteByQueryMd,
    ManualPages.DocumentManipulation to crudMd,
    ManualPages.IndexRepository to indexRepoMd,
    ManualPages.BulkIndexing to bulkMd,
    ManualPages.DataStreams to dataStreamsMd,
    ManualPages.Migrating to loadMd("manual/gettingstarted/migrating.md"),
    ManualPages.ExtendingTheDSL to extendingMd,
    ManualPages.Scripting to scriptingMd,
    ManualPages.Jupyter to loadMd("manual/jupyter/jupyter.md"),
).map {(mp,md)-> mp.page to md}

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