package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.kotlin4example.SourceRepository
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

private const val repoUrl = "https://github.com/jillesvangurp/kt-search"

private val cliReadmeRepository = SourceRepository(
    repoUrl = repoUrl,
    sourcePaths = setOf(
        "src/commonMain/kotlin",
        "src/jvmMain/kotlin",
        "src/nativeMain/kotlin",
        "src/jvmTest/kotlin",
    )
)

class CliReadmeTest {
    @Test
    fun generateCliReadmeMarkdown() {
        val markdown = cliReadmeRepository.md {
            +"""
                # ktsearch-cli

                `ktsearch-cli` is a multiplatform command line tool for
                Elasticsearch and OpenSearch operations based on `kt-search`.
                It supports Elasticsearch `7-9` and OpenSearch `1-3`, with
                installable binaries for macOS/Linux and Bash/Zsh completion.

                The full command reference is generated in
                [`cli-manual.md`](./cli-manual.md).
            """.trimIndent()

            section("Install / uninstall") {
                +"""
                    Use the scripts from the repository root:
                """.trimIndent()
                block(type = "bash") {
                    println("./ktsearch-cli/install.sh")
                    println("./ktsearch-cli/uninstall.sh")
                }
                +"""
                    `install.sh` builds and installs `ktsearch` for the current
                    macOS/Linux host and also installs Bash/Zsh completion.
                """.trimIndent()
            }

            section("Examples") {
                +"""
                    For more examples and all flags, see
                    [`cli-manual.md`](./cli-manual.md).
                """.trimIndent()
                block(type = "bash") {
                    println("ktsearch cluster health")
                    println("ktsearch info")
                    println("ktsearch cat indices")
                    println("ktsearch index create products")
                    println("ktsearch index wait-green products")
                }
            }

            section("Environment") {
                +"""
                    Configure connection defaults via environment variables:
                """.trimIndent()
                unorderedList(
                    "`KTSEARCH_HOST`",
                    "`KTSEARCH_PORT`",
                    "`KTSEARCH_HTTPS`",
                    "`KTSEARCH_USER`",
                    "`KTSEARCH_PASSWORD`",
                    "`KTSEARCH_LOGGING`",
                )
            }

            section("Completion") {
                +"""
                    Generate completion scripts for your shell:
                """.trimIndent()
                block(type = "bash") {
                    println("ktsearch completion bash")
                    println("ktsearch completion zsh")
                    println("ktsearch completion fish")
                }
            }

            section("Build artifacts") {
                unorderedList(
                    "Native executable: `ktsearch`",
                    "JVM fat jar: `./gradlew :ktsearch-cli:jvmFatJar`",
                )
            }
        }

        val outputFile = outputFile()
        Files.writeString(outputFile, markdown.value)

        Files.exists(outputFile) shouldBe true
    }

    private fun outputFile(): Path {
        return findProjectRoot().resolve("ktsearch-cli").resolve("README.md")
    }

    private fun findProjectRoot(): Path {
        var current: Path? = Paths.get("").toAbsolutePath()
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Unable to locate project root from ${Paths.get("").toAbsolutePath()}")
    }
}
