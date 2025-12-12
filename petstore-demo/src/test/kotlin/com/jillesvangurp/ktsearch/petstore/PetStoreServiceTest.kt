package com.jillesvangurp.ktsearch.petstore

import com.jillesvangurp.ktsearch.repository.IndexRepository
import com.jillesvangurp.ktsearch.total
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PetStoreServiceTest {

    companion object {
        @Container
        val elastic = ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.14.1")
            .apply {
                addEnv("xpack.security.enabled", "false")
            }

        @JvmStatic
        @DynamicPropertySource
        fun elasticProps(registry: DynamicPropertyRegistry) {
            val uri = URI(elastic.httpHostAddress)
            registry.add("demo.elastic.host") { uri.host }
            registry.add("demo.elastic.port") { uri.port }
            registry.add("demo.elastic.https") { false }
        }
    }

    @Autowired
    private lateinit var petStoreService: PetStoreService

    @Autowired
    private lateinit var petSearchRepository: IndexRepository<PetSearchDocument>

    @Test
    fun `should create indices and load sample data`(): Unit = runBlocking {
        petStoreService.ensureIndices()

        val aliases = petSearchRepository.search { }
        aliases.total.shouldBeGreaterThan(10)

        val search = petStoreService.searchPets(
            searchText = "pug",
            animal = null,
            breed = null,
            sex = null,
            ageRange = null,
            priceRange = null
        )
        search.total.shouldBeGreaterThan(0)
        val dogCount = search.facets.animals["dog"] ?: 0L
        dogCount.shouldBeGreaterThan(0)
    }

    @Test
    fun `should reindex search index`(): Unit = runBlocking {
        val before = petStoreService.reindexSearch()
        before.shouldBeGreaterThan(0)

        val created = petStoreService.createPet(
            Pet(
                id = "",
                name = "Test Pug",
                animal = "dog",
                breed = "Pug",
                sex = "m",
                age = 1,
                price = 777.0,
                description = "Test pet created by integration test",
                traits = listOf("test", "demo")
            )
        )

        created.wikipediaUrl shouldBe "https://en.wikipedia.org/wiki/Pug"
    }
}
