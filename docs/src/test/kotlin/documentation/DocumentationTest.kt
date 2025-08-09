package documentation

import com.jillesvangurp.kotlin4example.SourceRepository
import documentation.manual.manualIndexMd
import documentation.manual.manualPages
import documentation.projectreadme.projectReadme
import org.junit.jupiter.api.Test
import java.io.File

const val githubLink = "https://github.com/jillesvangurp/kt-search"
//const val jitpackLink = "[![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search)"

data class Page(
    val title: String,
    val fileName: String,
    val outputDir: String
)

val Page.mdLink get() = "[$title]($fileName)"

fun Page.write(content: String) {
    File(outputDir, fileName).writeText(
        """
            # $title 
            
        """.trimIndent().trimMargin() + "\n\n" + content
    )
}

val sourceGitRepository = SourceRepository(
    repoUrl = githubLink,
    sourcePaths = setOf("src/main/kotlin", "src/test/kotlin")
)

internal const val manualOutputDir = "build/manual"

val manualRootPage = Page("KT Search Manual", "README.md", manualOutputDir)
val readmePages = listOf(
    Page("KT Search Client", "README.md", "..") to projectReadme,
    manualRootPage to manualIndexMd,
)

fun loadMd(fileName: String) = sourceGitRepository.md {
    includeMdFile(fileName)
}

class DocumentationTest {

    @Test
    fun documentation() {
        File(manualOutputDir).mkdirs()
        readmePages.forEach { (page, md) ->
            page.write(md.value)
        }
        val pagesWithNav = manualPages.indices.map { index ->
            val (previousPage,_)=if(index>0) {
                 manualPages[index-1]
            } else {
                null to null
            }
            val (nextPage,_)=if(index< manualPages.size-1) {
                 manualPages[index+1]
            } else {
                null to null
            }
            val navigation = """
                | ${manualRootPage.mdLink} | ${previousPage?.let{ "Previous: ${it.mdLink}" } ?: "-"} | ${nextPage?.let{ "Next: ${it.mdLink}" } ?: "-"} |
                | [Github]($githubLink) | &copy; Jilles van Gurp |  |
            """.trimIndent()

            val (page,md) = manualPages[index]
            page to ("""
$navigation

---                

${md.value}

---

$navigation
""".trimIndent())
        }
        pagesWithNav.forEach { (page, md) ->
            page.write(md)
        }

        // Quarto expects an index.md as entry point. Keep README.md and write
        // a copy so existing markdown remains unaffected.
        val readme = File(manualOutputDir, "README.md")
        File(manualOutputDir, "index.md").writeText(readme.readText())

        // Basic Quarto configuration. This produces html, pdf and epub output
        // in build/quarto. Customize as needed.
        val chapters = listOf("index.md") + manualPages.map { it.first.fileName }
        val quartoConfig = buildString {
            appendLine("project:")
            appendLine("  type: book")
            appendLine("  output-dir: quarto")
            appendLine("book:")
            appendLine("  title: \"KT Search Manual\"")
            appendLine("  chapters:")
            chapters.forEach { appendLine("    - $it") }
            appendLine("format:")
            appendLine("  html:")
            appendLine("    theme: cosmo")
            appendLine("  pdf: default")
            appendLine("  epub: default")
        }
        File(manualOutputDir, "_quarto.yml").writeText(quartoConfig)
    }
}
