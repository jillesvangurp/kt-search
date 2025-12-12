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
        // begin STARTUP_BOOTSTRAP
        // Run the startup tasks on a background coroutine so Spring can finish
        // its lifecycle hooks without blocking the main thread.
        scope.launch {
            // Create indices if needed and then seed the demo dataset exactly
            // once (unless the user explicitly resets).
            petStoreService.ensureIndices()
            val resource = resourceLoader.getResource(properties.sampleData)
            resource.inputStream.use { stream ->
                petStoreService.loadSamplePetsIfEmpty(stream)
            }
        }
        // end STARTUP_BOOTSTRAP
    }
}
