package documentation.manual

import documentation.*
import documentation.manual.gettingstarted.gettingStartedMd
import documentation.manual.gettingstarted.whatIsKtSearchMd
import documentation.manualOutputDir

val manualPages = listOf(
    Page("What is Kt-Search", "WhatIsKtSearch.md", manualOutputDir) to whatIsKtSearchMd,
    Page("Getting Started", "GettingStarted.md", manualOutputDir) to gettingStartedMd,
    Page("Migrating from Es-Kotlin-Client", "Migrating.md", manualOutputDir) to loadMd("manual/gettingstarted/migrating.md"),
)

val manualIndexMd = sourceGitRepository.md {
    section("Table of contents") {
        +manualPages.joinToString("\n") { (p, _) -> "- " + p.mdLink }.trimIndent()
    }
    section("About this Manual") {
        includeMdFile("outro.md")
    }
}