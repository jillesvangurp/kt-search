package com.jillesvangurp.ktsearch.petstore

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
class StartupRunner(
    private val petStoreService: PetStoreService,
    private val properties: DemoProperties,
    private val resourceLoader: ResourceLoader
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    @PostConstruct
    fun initialize() {
        scope.launch {
            petStoreService.ensureIndices()
            val resource = resourceLoader.getResource(properties.sampleData)
            resource.inputStream.use { stream ->
                petStoreService.loadSamplePetsIfEmpty(stream)
            }
        }
    }
}
