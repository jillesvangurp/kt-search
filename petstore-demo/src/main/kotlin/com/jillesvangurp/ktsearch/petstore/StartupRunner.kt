package com.jillesvangurp.ktsearch.petstore

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.runBlocking
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
class StartupRunner(
    private val petStoreService: PetStoreService,
    private val properties: DemoProperties,
    private val resourceLoader: ResourceLoader
) {
    @PostConstruct
    private fun initialize() {
        // begin STARTUP_BOOTSTRAP
        // Complete initialization before Spring starts serving requests.
        runBlocking {
            petStoreService.ensureIndices()
            val resource = resourceLoader.getResource(properties.sampleData)
            resource.inputStream.use { stream ->
                petStoreService.loadSamplePetsIfEmpty(stream)
            }
        }
        // end STARTUP_BOOTSTRAP
    }
}
