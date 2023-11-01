package documentation.manual

import documentation.*
import documentation.manual.bulk.bulkMd
import documentation.manual.bulk.indexmanagement.dataStreamsMd
import documentation.manual.bulk.indexmanagement.indexManagementMd
import documentation.manual.crud.crudMd
import documentation.manual.extending.extendingMd
import documentation.manual.gettingstarted.clientConfiguration
import documentation.manual.gettingstarted.gettingStartedMd
import documentation.manual.gettingstarted.whatIsKtSearchMd
import documentation.manual.indexrepo.indexRepoMd
import documentation.manual.knn.knnMd
import documentation.manual.scripting.scriptingMd
import documentation.manual.search.*

enum class ManualPages(title: String = "") {
    WhatIsKtSearch("What is Kt-Search"),
    GettingStarted("Getting Started"),
    ClientConfiguration("Client Configuration"),
    Search("Search and Queries"),
    TextQueries("Text Queries"),
    TermLevelQueries("Term Level Queries"),
    CompoundQueries("Compound Queries"),
    GeoQueries("Geo Spatial Queries"),
    SpecializedQueries("Specialized Queries"),
    Aggregations("Aggregations"),
    DeepPaging("Deep Paging Using search_after and scroll"),
    DeleteByQuery("Deleting by query"),
    DocumentManipulation("Document Manipulation"),
    IndexRepository("Index Repository"),
    BulkIndexing("Efficiently Ingest Content Using Bulk Indexing"),
    IndexManagement("Indices, Settings, Mappings, and Aliases"),
    DataStreams("Creating Data Streams"),
    KnnSearch("KNN Search"),
    Migrating("Migrating from the old Es Kotlin Client"),
    ExtendingTheDSL("Extending the Json DSLs"),
    Scripting("Using Kotlin Scripting"),
    Jupyter("Jupyter Notebooks"),
    ;

    val page by lazy {
        Page(title, "${name}.md", manualOutputDir)
    }
    val publicLink = "https://jillesvangurp.github.io/kt-search/manual/${name}.html"
}

data class Section(val title: String, val pages: List<Pair<ManualPages, Lazy<String>>>)

val sections = listOf(
    Section(
        "Introduction", listOf(
            ManualPages.WhatIsKtSearch to whatIsKtSearchMd,
            ManualPages.GettingStarted to gettingStartedMd,
            ManualPages.ClientConfiguration to clientConfiguration,
            ManualPages.IndexManagement to indexManagementMd,
        )
    ),
    Section(
        "Search", listOf(
            ManualPages.Search to searchMd,
            ManualPages.TextQueries to textQueriesMd,
            ManualPages.TermLevelQueries to termLevelQueriesMd,
            ManualPages.CompoundQueries to compoundQueriesMd,
            ManualPages.GeoQueries to geoQueriesMd,
            ManualPages.SpecializedQueries to specializedQueriesMd,
            ManualPages.Aggregations to aggregationsMd,
            ManualPages.DeepPaging to deepPagingMd,
        )
    ),
    Section("Indices and Documents", listOf(
        ManualPages.DeleteByQuery to deleteByQueryMd,
        ManualPages.DocumentManipulation to crudMd,
        ManualPages.IndexRepository to indexRepoMd,
        ManualPages.BulkIndexing to bulkMd,
        ManualPages.DataStreams to dataStreamsMd,
    )),
    Section("Advanced Topics", listOf(
        ManualPages.KnnSearch to knnMd,
        ManualPages.ExtendingTheDSL to extendingMd,
        ManualPages.Scripting to scriptingMd,
        ManualPages.Jupyter to loadMd("manual/jupyter/jupyter.md"),
        ManualPages.Migrating to loadMd("manual/gettingstarted/migrating.md"),
    ))
)

val manualPages = sections.flatMap { it.pages }.map { (mp,md) -> mp.page to md }

val manualIndexMd = sourceGitRepository.md {
    includeMdFile("../projectreadme/oneliner.md")

    section("Table of contents") {

        sections.forEach {
            +"""
            ### ${it.title}
                
            """.trimIndent()
            it.pages.forEach {(mp,_) ->
                +"${mp.page.mdLink}\n"
            }

        }
    }
    section("About this Manual") {
        includeMdFile("outro.md")
    }

    includeMdFile("../projectreadme/related.md")
}