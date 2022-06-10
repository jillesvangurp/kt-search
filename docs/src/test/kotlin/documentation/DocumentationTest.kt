package documentation

import com.jillesvangurp.kotlin4example.SourceRepository
import documentation.manual.gettingstarted.gettingStartedMd
import documentation.manual.gettingstarted.whatIsKtSearchMd
import documentation.manual.manualIndexMd
import documentation.manual.manualOutputDir
import documentation.manual.manualPages
import documentation.projectreadme.projectReadme
import org.junit.jupiter.api.Test
import java.io.File

data class Page(val title: String, val fileName: String, val outputDir: String)

val Page.mdLink get() = "[$title]($outputDir.$fileName)"

fun Page.write(content: String) {
    File(outputDir, fileName).writeText(
        """
            # $title 
            
        """.trimIndent().trimMargin() + "\n\n" + content
    )
}

val sourceGitRepository = SourceRepository(
    repoUrl = "https://github.com/jillesvangurp/kt-search",
    sourcePaths = setOf("src/main/kotlin", "src/test/kotlin")
)

val readmePages = listOf(
    Page("KT Search Client", "README.md", "..") to projectReadme,
    Page("Manual Index", "README.md", manualOutputDir) to manualIndexMd,
)

class DocumentationTest {

    @Test
    fun documentation() {
        File(manualOutputDir).mkdirs()
        readmePages.forEach { (page, md) ->
            page.write(md.value)
        }
        manualPages.forEach { (page, md) ->
            page.write(md.value)
        }
    }
}