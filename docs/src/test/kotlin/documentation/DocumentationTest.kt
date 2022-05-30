package documentation

import com.jillesvangurp.kotlin4example.SourceRepository
import documentation.projectreadme.projectReadme
import org.junit.jupiter.api.Test
import java.io.File

data class Page(val title: String, val fileName: String, val outputDir: String)

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


val pages = listOf(
    Page("KT Search Client", "README.md", "..") to projectReadme,
//    Page("Manual Index", "README.md", ".") to projectReadme
)

class DocumentationTest {

    @Test
    fun docs() {
        pages.forEach {(page,md) ->
            page.write(md.value)
        }
    }
}