package documentation.manual

import documentation.Page
import documentation.manual.gettingstarted.gettingStartedMd
import documentation.manual.gettingstarted.whatIsKtSearchMd
import documentation.mdLink
import documentation.sourceGitRepository

internal const val manualOutputDir = "build/manual"

val manualPages = listOf(
    Page("What is Kt-Search", "WhatIsKtSearch.md", manualOutputDir) to whatIsKtSearchMd,
    Page("Getting Started", "GettingStarted.md", manualOutputDir) to gettingStartedMd,
)

val manualIndexMd = sourceGitRepository.md {
    section("Table of contents") {
        +manualPages.joinToString("\n") { (p, _) -> "- " + p.mdLink }.trimIndent()
    }
    section("About this Manual") {
        includeMdFile("outro.md")
    }
}