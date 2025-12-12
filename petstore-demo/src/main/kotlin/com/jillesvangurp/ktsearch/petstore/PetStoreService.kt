package com.jillesvangurp.ktsearch.petstore

import com.jillesvangurp.ktsearch.Aggregations
import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.bulk
import com.jillesvangurp.ktsearch.deleteByQuery
import com.jillesvangurp.ktsearch.getIndexesForAlias
import com.jillesvangurp.ktsearch.parsedBuckets
import com.jillesvangurp.ktsearch.rangesResult
import com.jillesvangurp.ktsearch.repository.IndexRepository
import com.jillesvangurp.ktsearch.snakeCase
import com.jillesvangurp.ktsearch.termsResult
import com.jillesvangurp.ktsearch.updateAliases
import com.jillesvangurp.ktsearch.post
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.util.UUID
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger { }

/**
 * Central service for managing indices, ETL logic, and API level search helpers.
 */
@Service
class PetStoreService(
    private val properties: DemoProperties,
    private val searchClient: SearchClient,
    private val petsRepository: IndexRepository<Pet>,
    private val petSearchRepository: IndexRepository<PetSearchDocument>,
    private val json: Json
) {
    private val wikiLookup = mapOf(
        "dalmatian" to "https://en.wikipedia.org/wiki/Dalmatian_dog",
        "pug" to "https://en.wikipedia.org/wiki/Pug",
        "maine coon" to "https://en.wikipedia.org/wiki/Maine_Coon",
        "siamese" to "https://en.wikipedia.org/wiki/Siamese_cat",
        "a fish called wanda" to "https://en.wikipedia.org/wiki/A_Fish_Called_Wanda",
        "great white shark" to "https://en.wikipedia.org/wiki/Jaws_(film)",
        "shark" to "https://en.wikipedia.org/wiki/Jaws_(film)",
        "clownfish" to "https://en.wikipedia.org/wiki/Finding_Nemo",
        "tasmanian devil" to "https://en.wikipedia.org/wiki/Tasmanian_devil",
        "saint bernard" to "https://en.wikipedia.org/wiki/Cujo",
        "cockatiel" to "https://en.wikipedia.org/wiki/Cockatiel",
        "goldfish" to "https://en.wikipedia.org/wiki/Goldfish",
        "rabbit" to "https://en.wikipedia.org/wiki/Rabbit",
        "hamster" to "https://en.wikipedia.org/wiki/Hamster"
    )

    private val stockImages = mapOf(
        "dalmatian" to "https://images.unsplash.com/photo-1507146426996-ef05306b995a?auto=format&fit=crop&w=800&q=80",
        "pug" to "https://images.unsplash.com/photo-1505628346881-b72b27e84530?auto=format&fit=crop&w=800&q=80",
        "maine coon" to "https://images.unsplash.com/photo-1618826411640-4b532771ab7d?auto=format&fit=crop&w=800&q=80",
        "siamese" to "https://images.unsplash.com/photo-1619983081563-430f63602796?auto=format&fit=crop&w=800&q=80",
        "a fish called wanda" to "https://images.unsplash.com/photo-1502720705749-3c9255857623?auto=format&fit=crop&w=800&q=80",
        "great white shark" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80",
        "clownfish" to "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=800&q=80",
        "tasmanian devil" to "https://upload.wikimedia.org/wikipedia/commons/5/59/Tasmanian_Devil_02.jpg",
        "saint bernard" to "https://images.unsplash.com/photo-1619983851958-92c7ad3f49c7?auto=format&fit=crop&w=800&q=80",
        "cockatiel" to "https://images.unsplash.com/photo-1601758124385-e9b4e5c0dd4b?auto=format&fit=crop&w=800&q=80",
        "goldfish" to "https://images.unsplash.com/photo-1501004318641-b39e6451bec6?auto=format&fit=crop&w=800&q=80",
        "rabbit" to "https://images.unsplash.com/photo-1504208434309-cb69f4fe52b0?auto=format&fit=crop&w=800&q=80",
        "hamster" to "https://images.unsplash.com/photo-1545243424-0ce743321e11?auto=format&fit=crop&w=800&q=80"
    )

    suspend fun ensureIndices() {
        ensureIndexExists(
            indexName = properties.indices.petsIndex,
            readAlias = properties.indices.petsRead,
            writeAlias = properties.indices.petsWrite,
            repository = petsRepository,
            mappingBuilder = this::petsMapping
        )

        ensureIndexExists(
            indexName = properties.indices.petSearchIndex,
            readAlias = properties.indices.petSearchRead,
            writeAlias = properties.indices.petSearchWrite,
            repository = petSearchRepository,
            mappingBuilder = this::petSearchMapping
        )
    }

    private suspend fun ensureIndexExists(
        indexName: String,
        readAlias: String,
        writeAlias: String,
        repository: IndexRepository<*>,
        mappingBuilder: () -> IndexSettingsAndMappingsDSL
    ) {
        val aliasExists = searchClient.getIndexesForAlias(readAlias).isNotEmpty()
        if (!aliasExists) {
            logger.info { "Creating index $indexName with aliases $readAlias / $writeAlias" }
            repository.createIndex(indexName, mappingBuilder())
            searchClient.updateAliases {
                addAliasForIndex(indexName, readAlias)
                addAliasForIndex(indexName, writeAlias)
            }
        }
    }

    private fun petsMapping(): IndexSettingsAndMappingsDSL = IndexSettingsAndMappingsDSL().apply {
        settings {
            shards = 1
            replicas = 0
        }
        mappings(dynamicEnabled = false) {
            keyword(Pet::id)
            keyword(Pet::animal) { index = false; store = true }
            keyword(Pet::breed) { index = false; store = true }
            keyword(Pet::sex) { index = false; store = true }
            field("age", "integer") { index = false; store = true }
            field("price", "double") { index = false; store = true }
            text(Pet::name) { index = false; store = true }
            text(Pet::description) { index = false; store = true }
            keyword(Pet::traits) { index = false; store = true }
            keyword("imageUrl") { index = false; store = true }
        }
    }

    private fun petSearchMapping(): IndexSettingsAndMappingsDSL = IndexSettingsAndMappingsDSL().apply {
        settings {
            shards = 1
            replicas = 0
        }
        mappings(dynamicEnabled = true) {
            keyword(PetSearchDocument::id)
            keyword(PetSearchDocument::animal)
            keyword(PetSearchDocument::breed)
            keyword(PetSearchDocument::sex)
            field("age", "integer")
            field("price", "double")
            keyword(PetSearchDocument::priceBucket)
            keyword(PetSearchDocument::traits) { index = true }
            text(PetSearchDocument::name)
            text(PetSearchDocument::description)
            keyword("wikipediaUrl")
            keyword("image_url")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun loadSamplePetsIfEmpty(sampleStream: InputStream) {
        val current = rawSearch<PetSearchDocument>(
            target = properties.indices.petSearchRead
        ) { resultSize = 0 }
        val total = current.hits.total.value
        if (total > 0) {
            logger.info { "Skipping import; pet search index already has $total docs" }
            return
        }
        val pets = decodeSamplePets(sampleStream)
        indexPets(pets)
    }

    /** Wipes both indices and reloads the bundled sample data set. */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun resetSampleData(sampleStream: InputStream): ResetStats {
        ensureIndices()

        val petsDeleted = petsRepository.deleteByQuery { query = matchAll() }.deleted
        val searchDocsDeleted = petSearchRepository.deleteByQuery { query = matchAll() }.deleted

        val pets = decodeSamplePets(sampleStream)
        indexPets(pets)

        return ResetStats(
            deletedPets = petsDeleted,
            deletedSearchDocs = searchDocsDeleted,
            reloaded = pets.size.toLong()
        )
    }

    suspend fun indexPets(pets: List<Pet>) {
        if (pets.isEmpty()) return
        petsRepository.bulk {
            pets.forEach { pet ->
                index(petsRepository.serializer.serialize(pet), id = pet.id)
            }
        }
        petSearchRepository.bulk(callBack = null, refresh = Refresh.WaitFor) {
            pets.map { it.toSearchDocument() }.forEach { doc ->
                index(petSearchRepository.serializer.serialize(doc), id = doc.id)
            }
        }
    }

    suspend fun reindexSearch(): Long {
        val hits = rawSearch<Pet>(
            target = properties.indices.petsRead
        ) {
            from = 0
            resultSize = 500
        }
        val pets = hits.hits.hits.mapNotNull { it.source }
        petSearchRepository.deleteByQuery {
            // Explicit match_all is required by ES when using _delete_by_query
            query = matchAll()
        }
        indexPets(pets)
        return pets.size.toLong()
    }

    suspend fun createPet(pet: Pet): PetSearchDocument {
        val petWithId = if (pet.id.isBlank()) pet.copy(id = UUID.randomUUID().toString()) else pet
        indexPetRaw(petWithId)
        val searchDoc = petWithId.toSearchDocument()
        indexPetSearchRaw(searchDoc)
        return searchDoc
    }

    suspend fun updatePet(id: String, pet: Pet): PetSearchDocument {
        val updated = pet.copy(id = id)
        indexPetRaw(updated)
        val searchDoc = updated.toSearchDocument()
        indexPetSearchRaw(searchDoc)
        return searchDoc
    }

    suspend fun deletePet(id: String) {
        petsRepository.delete(id = id)
        petSearchRepository.delete(id = id)
    }

    suspend fun getPet(id: String): Pet? = petsRepository.getDocument(id)

    suspend fun searchPets(
        searchText: String?,
        animal: String?,
        breed: String?,
        sex: String?,
        ageRange: String?,
        priceRange: String?
    ): PetSearchResponse {
        val response = rawSearch<PetSearchDocument>(
            target = properties.indices.petSearchRead
        ) {
            from = 0
            resultSize = 100
            val filters = collectFilters(animal, breed, sex, ageRange, priceRange)
            query = bool {
                searchText?.takeIf { it.isNotBlank() }?.let { q ->
                    should(
                        match(PetSearchDocument::name, q) { fuzziness = "AUTO" },
                        matchPhrasePrefix(PetSearchDocument::description, q),
                        multiMatch(
                            fields = listOf("name", "description", "traits"),
                            query = q
                        )
                    )
                    minimumShouldMatch(1)
                }
                if (filters.isNotEmpty()) {
                    filter(filters)
                }
            }
            agg("animals", TermsAgg("animal") { aggSize = 10 })
            agg("breeds", TermsAgg("breed") { aggSize = 20 })
            agg("sexes", TermsAgg("sex") { aggSize = 5 })
            agg(
                "ages",
                RangesAgg("age") {
                    ranges = listOf(
                        AggRange.create { to = 2.0; key = "0-2" },
                        AggRange.create { from = 2.0; to = 8.0; key = "2-8" },
                        AggRange.create { from = 8.0; key = "8+" }
                    )
                }
            )
            agg(
                "prices",
                RangesAgg("price") {
                    ranges = listOf(
                        AggRange.create { to = 500.0; key = "budget" },
                        AggRange.create { from = 500.0; to = 1500.0; key = "mid" },
                        AggRange.create { from = 1500.0; key = "premium" }
                    )
                }
            )
        }

        val pets = response.hits.hits.mapNotNull { it.source }

        val facets = SearchFacets(
            animals = response.aggregations?.termsResult("animals")?.parsedBuckets
                ?.associate { bucket -> bucket.parsed.key to bucket.parsed.docCount } ?: emptyMap(),
            breeds = response.aggregations?.termsResult("breeds")?.parsedBuckets
                ?.associate { bucket -> bucket.parsed.key to bucket.parsed.docCount } ?: emptyMap(),
            sexes = response.aggregations?.termsResult("sexes")?.parsedBuckets
                ?.associate { bucket -> bucket.parsed.key to bucket.parsed.docCount } ?: emptyMap(),
            ageRanges = response.aggregations?.rangesResult("ages")?.parsedBuckets
                ?.associate { bucket -> bucket.parsed.key to bucket.parsed.docCount } ?: emptyMap(),
            priceRanges = response.aggregations?.rangesResult("prices")?.parsedBuckets
                ?.associate { bucket -> bucket.parsed.key to bucket.parsed.docCount } ?: emptyMap()
        )

        return PetSearchResponse(total = response.hits.total.value, pets = pets, facets = facets)
    }

    private fun QueryClauses.collectFilters(
        animal: String?,
        breed: String?,
        sex: String?,
        ageRange: String?,
        priceRange: String?
    ): List<ESQuery> {
        val filters = mutableListOf<ESQuery>()
        animal?.takeIf { it.isNotBlank() }?.let { filters += term("animal", it) }
        breed?.takeIf { it.isNotBlank() }?.let { filters += term("breed", it) }
        sex?.takeIf { it.isNotBlank() }?.let { filters += term("sex", it) }
        when (ageRange) {
            "0-2" -> filters += range("age") { lte = 2.0 }
            "2-8" -> filters += range("age") { gte = 2.0; lte = 8.0 }
            "8+" -> filters += range("age") { gte = 8.0 }
        }
        when (priceRange) {
            "budget" -> filters += range("price") { lte = 500.0 }
            "mid" -> filters += range("price") { gte = 500.0; lte = 1500.0 }
            "premium" -> filters += range("price") { gte = 1500.0 }
        }
        return filters
    }

    private fun Pet.toSearchDocument(): PetSearchDocument {
        val normalizedBreed = breed.lowercase()
        val wiki = wikiLookup[normalizedBreed] ?: wikiLookup[animal.lowercase()]
        val image = imageUrl ?: stockImages[normalizedBreed]
        val priceBucket = when {
            price < 500 -> "budget"
            price < 1500 -> "mid"
            else -> "premium"
        }
        return PetSearchDocument(
            id = id,
            name = name,
            animal = animal,
            breed = breed,
            sex = sex,
            age = age,
            price = price,
            priceBucket = priceBucket,
            description = description,
            traits = traits,
            wikipediaUrl = wiki,
            imageUrl = image
        )
    }

    private suspend fun indexPetRaw(pet: Pet) {
        val payload = json.encodeToString(Pet.serializer(), pet)
        searchClient.restClient.post {
            path(properties.indices.petsWrite, "_doc", pet.id)
            parameter("refresh", Refresh.WaitFor.snakeCase())
            rawBody(payload)
        }.getOrThrow()
    }

    private suspend fun indexPetSearchRaw(doc: PetSearchDocument) {
        val payload = json.encodeToString(PetSearchDocument.serializer(), doc)
        searchClient.restClient.post {
            path(properties.indices.petSearchWrite, "_doc", doc.id)
            parameter("refresh", Refresh.WaitFor.snakeCase())
            rawBody(payload)
        }.getOrThrow()
    }

    @Serializable
    private data class RawTotal(val value: Long = 0)

    @Serializable
    private data class RawHits<T>(
        val total: RawTotal = RawTotal(),
        val hits: List<RawHit<T>> = emptyList()
    )

    @Serializable
    private data class RawHit<T>(
        @SerialName("_id") val id: String,
        @SerialName("_source") val source: T? = null
    )

    @Serializable
    private data class RawSearchResponse<T>(
        val hits: RawHits<T> = RawHits(),
        val aggregations: Aggregations? = null
    )

    private suspend inline fun <reified T> rawSearch(
        target: String,
        crossinline block: SearchDSL.() -> Unit = {}
    ): RawSearchResponse<T> {
        val dsl = SearchDSL().apply(block)
        val response = searchClient.restClient.post {
            path(target, "_search")
            json(dsl)
        }.getOrThrow()
        return searchClient.json.decodeFromString(response.text)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun decodeSamplePets(sampleStream: InputStream): List<Pet> =
        json.decodeFromStream(ListSerializer(Pet.serializer()), sampleStream)

    /** Summary counters for a reset operation. */
    data class ResetStats(
        /** Documents removed from the pets store. */
        val deletedPets: Long,
        /** Documents removed from the pet-search store. */
        val deletedSearchDocs: Long,
        /** Sample pets ingested after the reset. */
        val reloaded: Long
    )
}
