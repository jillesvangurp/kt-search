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

            section("Related tools") {
                +listOf(
                    "The tools below are useful alternatives or complements.",
                    "",
                    "| Tool | What it is good at | Compared to `ktsearch-cli` |",
                    "|---|---|---|",
                    "| `ecctl` (Elastic Cloud) | Managing Elastic Cloud deployments, traffic filters, and platform settings. | Cloud-control focused. `ktsearch-cli` focuses on index/cluster APIs. |",
                    "| `opensearch-cli` / AWS CLI (OpenSearch) | OpenSearch plugin workflows and Amazon OpenSearch domain/service operations. | Useful for service/domain provisioning and plugin commands. `ktsearch-cli` focuses on search/index operations. |",
                    "| `curl` + `jq` | Universal access to any endpoint. | Very flexible, but no domain-specific commands, no built-in safety prompts, and no integrated completion model. |",
                    "| `elasticdump` | Data migration/export workflows. | Strong ETL focus, but not a general-purpose operational CLI for aliases/templates/snapshots/ILM in one tool. |",
                    "| `elasticsearch-curator` | Policy-style index housekeeping jobs. | Great for scheduled maintenance; less suited as an interactive daily CLI for both Elasticsearch and OpenSearch generations. |",
                    "| OpenSearch/Elastic Dev Tools consoles | Interactive request authoring in UI. | Excellent for ad hoc requests, but browser-based and not ideal for shell automation in CI/scripts. |",
                ).joinToString("\n")
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
