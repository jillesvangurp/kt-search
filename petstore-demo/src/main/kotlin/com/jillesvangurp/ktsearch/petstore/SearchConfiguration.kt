package com.jillesvangurp.ktsearch.petstore

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.repository.IndexRepository
import com.jillesvangurp.ktsearch.repository.KotlinxSerializationModelSerializationStrategy
import com.jillesvangurp.serializationext.DEFAULT_JSON
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SearchConfiguration {

    @Bean
    fun demoJson(): Json = DEFAULT_JSON // uses sane defaults

    @Bean
    fun searchClient(properties: DemoProperties, json: Json): SearchClient {
        // begin SEARCH_CLIENT_BEAN
        // The Ktor-based REST client powers both the DSL-based repository
        // helpers and the lower-level raw requests we occasionally need.
        val elastic = properties.elastic
        return SearchClient(
            KtorRestClient(
                host = elastic.host,
                port = elastic.port,
                https = elastic.https,
                user = elastic.username,
                password = elastic.password,
                // Very verbose but allows you to
                // see all the interactions with Elasticsearch
                logging = true
            ),
            json = json
        )
        // end SEARCH_CLIENT_BEAN
    }

    @Bean
    fun petsRepository(properties: DemoProperties, client: SearchClient, json: Json): IndexRepository<Pet> =
        // Lightweight helper that wraps the DSL with strongly typed serialization.
        IndexRepository(
            indexNameOrWriteAlias = properties.indices.petsWrite,
            indexReadAlias = properties.indices.petsRead,
            client = client,
            serializer = KotlinxSerializationModelSerializationStrategy(Pet.serializer(), json)
        )

    @Bean
    fun petSearchRepository(properties: DemoProperties, client: SearchClient, json: Json): IndexRepository<PetSearchDocument> =
        // Same repository pattern for the search projection index.
        IndexRepository(
            indexNameOrWriteAlias = properties.indices.petSearchWrite,
            indexReadAlias = properties.indices.petSearchRead,
            client = client,
            serializer = KotlinxSerializationModelSerializationStrategy(PetSearchDocument.serializer(), json)
        )
}
