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

val manualReadmePage = Page("KT Search Manual", "README.md", manualOutputDir)
val manualIndexPage = Page("KT Search Manual", "index.md", manualOutputDir)
val readmePages = listOf(
    Page("KT Search Client", "README.md", "..") to projectReadme,
    manualReadmePage to manualIndexMd,
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

        // write bookdown index with yaml front matter
        val bookdownFrontMatter = """
            ---
            title: "KT Search Manual"
            site: bookdown::bookdown_site
            output:
              bookdown::gitbook: default
              bookdown::pdf_book: default
              bookdown::epub_book: default
            ---
        """.trimIndent()
        File(manualOutputDir, manualIndexPage.fileName).writeText(
            bookdownFrontMatter + "\n\n" + manualIndexMd.value
        )

        // bookdown configuration listing chapters
        val bookdownConfig = buildString {
            appendLine("book_filename: \"kt-search-manual\"")
            appendLine("rmd_files:")
            appendLine("  - ${manualIndexPage.fileName}")
            manualPages.forEach { (page, _) ->
                appendLine("  - ${page.fileName}")
            }
        }
        File(manualOutputDir, "_bookdown.yml").writeText(bookdownConfig)

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
                | ${manualReadmePage.mdLink} | ${previousPage?.let{ "Previous: ${it.mdLink}" } ?: "-"} | ${nextPage?.let{ "Next: ${it.mdLink}" } ?: "-"} |
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
    }
}
