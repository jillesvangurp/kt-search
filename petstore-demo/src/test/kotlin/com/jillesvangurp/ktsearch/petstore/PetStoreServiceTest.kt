package com.jillesvangurp.ktsearch.petstore

import com.jillesvangurp.ktsearch.repository.IndexRepository
import com.jillesvangurp.ktsearch.total
import com.jillesvangurp.searchdsls.querydsl.matchAll
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader

@SpringBootTest(
    properties = [
        "demo.elastic.host=127.0.0.1",
        "demo.elastic.port=9999",
        "demo.elastic.https=false"
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PetStoreServiceTest {

    @Autowired
    private lateinit var petStoreService: PetStoreService

    @Autowired
    private lateinit var petSearchRepository: IndexRepository<PetSearchDocument>

    @Autowired
    private lateinit var resourceLoader: ResourceLoader

    @Test
    fun `should create indices and load sample data`(): Unit = runBlocking {
        petStoreService.ensureIndices()
        resourceLoader.getResource("classpath:data/pets.json").inputStream.use { stream ->
            petStoreService.loadSamplePetsIfEmpty(stream)
        }

        eventually(10.seconds) {
            val search = petStoreService.searchPets(
                searchText = null,
                animal = null,
                breed = null,
                sex = null,
                ageRange = null,
                priceRange = null
            )
            search.total.shouldBeGreaterThan(10)
            search.total.shouldBeGreaterThan(0)
            val dogCount = search.facets.animals["dog"] ?: 0L
            dogCount.shouldBeGreaterThan(0)
        }
    }

    @Test
    fun `should reindex search index and index test pet`(): Unit = runBlocking {
        petStoreService.rebuildSearchIndex()

        eventually(10.seconds) {
            petSearchRepository.search {
                query = matchAll()
                trackTotalHits = "true"
            }.total shouldBeGreaterThan 20
        }

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

        eventually(10.seconds) {
            created.wikipediaUrl shouldBe "https://en.wikipedia.org/wiki/Pug"
        }
    }
}
