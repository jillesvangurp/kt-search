package com.jillesvangurp.ktsearch.petstore

import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/pets")
class PetController(
    private val petStoreService: PetStoreService,
    private val properties: DemoProperties,
    private val resourceLoader: ResourceLoader
) {

    @GetMapping("/search", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) animal: String?,
        @RequestParam(required = false) breed: String?,
        @RequestParam(required = false) sex: String?,
        @RequestParam(required = false) ageRange: String?,
        @RequestParam(required = false) priceRange: String?
    ): PetSearchResponse = petStoreService.searchPets(
        searchText = q,
        animal = animal,
        breed = breed,
        sex = sex,
        ageRange = ageRange,
        priceRange = priceRange
    )

    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun get(@PathVariable id: String): Pet? = petStoreService.getPet(id)

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun create(@RequestBody pet: Pet): PetSearchDocument = petStoreService.createPet(pet)

    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun update(@PathVariable id: String, @RequestBody pet: Pet): PetSearchDocument =
        petStoreService.updatePet(id, pet)

    @DeleteMapping("/{id}")
    suspend fun delete(@PathVariable id: String) {
        petStoreService.deletePet(id)
    }

    @PostMapping("/reindex")
    suspend fun reindex(): Map<String, Long> = mapOf("reindexed" to petStoreService.reindexSearch())

    @PostMapping("/reset")
    suspend fun reset(): PetStoreService.ResetStats {
        val resource = resourceLoader.getResource(properties.sampleData)
        return resource.inputStream.use { petStoreService.resetSampleData(it) }
    }
}
