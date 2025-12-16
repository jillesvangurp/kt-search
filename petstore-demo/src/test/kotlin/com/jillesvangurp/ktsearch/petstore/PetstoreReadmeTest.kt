package com.jillesvangurp.ktsearch.petstore

import com.jillesvangurp.kotlin4example.SourceRepository
import org.junit.jupiter.api.Test
import java.io.File

private const val repoUrl = "https://github.com/jillesvangurp/kt-search"

private val petStoreRepository = SourceRepository(
    repoUrl = repoUrl,
    sourcePaths = setOf(
        "petstore-demo/src/main/kotlin",
        "petstore-demo/src/test/kotlin"
    )
)

class PetstoreReadmeTest {

    @Test
    fun generateReadme() {
        val md = petStoreRepository.md {
            +"""
                # KT Search Pet Store Demo
                
                This module is a small Spring Boot app that demonstrates
                how to combine the kt-search DSLs with a search-friendly
                read model. 
                
                It has a few features to make it a bit more interesting than
                your average hello world application:
                
                - Rich search across several fields and search as you type.
                - Uses aggregations by price, tag, etc. And of course you can **filter** on these.
                - Dashboard with **apache echarts widgets powered* by aggregations as well.
                - Use kt-search to define mappings
                - Organize your indices with read and write aliases
                - You don't index what you store but should use an **ETL pipeline**. This demo has one that **enriches 
                the document with images and wikipedia articles**.
                - There are separate indices for searching and storing. Normally you would use a database perhaps. But whenever 
                something changes it needs to be also added to the search index.
                Additionally, your code might require you to reindex, so that 
                is built into this demo. 
                
                ![petstore demo](petstore-demo.webp)
            """.trimIndent()

            section("Run it locally") {
                +"""
                    The compose file in this module mirrors the root
                    docker-compose setup and starts an Elasticsearch 9.x node
                    on port 9200.
                    
                    ```bash
                    cd petstore-demo
                    docker compose up elasticsearch -d
                    ./gradlew :petstore-demo:bootRun
                    ```
                    
                    The SPA lives at http://localhost:8080 and talks to the API
                    on the same host.
                """.trimIndent()
            }

            section("Wiring the search client") {
                +"""
                    The Spring config uses the Ktor-based REST client that ships
                    with kt-search. Swap the hostname or credentials in
                    `application.yml` if you want to point the demo at a
                    different cluster.
                """.trimIndent()
                exampleFromSnippet(
                    "src/main/kotlin/com/jillesvangurp/ktsearch/petstore/SearchConfiguration.kt",
                    "SEARCH_CLIENT_BEAN"
                )
            }

            section("Indices, aliases, and mappings") {
                +"""
                    On startup we create versioned indices plus read/write
                    aliases so future rollovers become a metadata-only change.
                """.trimIndent()
                exampleFromSnippet(
                    "src/main/kotlin/com/jillesvangurp/ktsearch/petstore/PetStoreService.kt",
                    "ENSURE_INDICES"
                )
                +"""
                    Raw documents live in `pets` with a strict mapping; the
                    search projection in `pet-search` uses a more flexible,
                    text-friendly mapping.
                """.trimIndent()
                exampleFromSnippet(
                    "src/main/kotlin/com/jillesvangurp/ktsearch/petstore/PetStoreService.kt",
                    "PETS_MAPPING"
                )
                exampleFromSnippet(
                    "src/main/kotlin/com/jillesvangurp/ktsearch/petstore/PetStoreService.kt",
                    "SEARCH_MAPPING"
                )
            }

            section("Enriching pets before search") {
                +"""
                    Every pet is augmented with helper data (hero image,
                    Wikipedia link, price bucket) before landing in the search
                    index. That keeps the UI payload compact and search results
                    consistent.
                """.trimIndent()
                exampleFromSnippet(
                    "src/main/kotlin/com/jillesvangurp/ktsearch/petstore/PetStoreService.kt",
                    "ENRICH_PET"
                )
            }

            section("Search flow") {
                +"""
                    The API builds a bool query with optional filters and a
                    dis_max clause to blend typo-friendly prefix matching with
                    best_fields relevance. Aggregations back the UI facets.
                """.trimIndent()
                exampleFromSnippet(
                    "src/main/kotlin/com/jillesvangurp/ktsearch/petstore/PetStoreService.kt",
                    "SEARCH_PETS"
                )
            }

            section("Seeding demo data") {
                +"""
                    On startup we lazily create the indices and load the bundled
                    JSON dataset so the UI always has something to show.
                """.trimIndent()
                exampleFromSnippet(
                    "src/main/kotlin/com/jillesvangurp/ktsearch/petstore/StartupRunner.kt",
                    "STARTUP_BOOTSTRAP"
                )
            }
        }

        File("README.md").writeText(md.value)
    }
}
