package com.jillesvangurp.ktsearch.petstore

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.repository.IndexRepository
import com.jillesvangurp.ktsearch.repository.KotlinxSerializationModelSerializationStrategy
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SearchConfiguration {

    @Bean
    fun demoJson(): Json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Bean
    fun searchClient(properties: DemoProperties, json: Json): SearchClient {
        val elastic = properties.elastic
        return SearchClient(
            KtorRestClient(
                host = elastic.host,
                port = elastic.port,
                https = elastic.https,
                user = elastic.username,
                password = elastic.password,
                logging = true
            ),
            json = json
        )
    }

    @Bean
    fun petsRepository(properties: DemoProperties, client: SearchClient, json: Json): IndexRepository<Pet> =
        IndexRepository(
            indexNameOrWriteAlias = properties.indices.petsWrite,
            indexReadAlias = properties.indices.petsRead,
            client = client,
            serializer = KotlinxSerializationModelSerializationStrategy(Pet.serializer(), json)
        )

    @Bean
    fun petSearchRepository(properties: DemoProperties, client: SearchClient, json: Json): IndexRepository<PetSearchDocument> =
        IndexRepository(
            indexNameOrWriteAlias = properties.indices.petSearchWrite,
            indexReadAlias = properties.indices.petSearchRead,
            client = client,
            serializer = KotlinxSerializationModelSerializationStrategy(PetSearchDocument.serializer(), json)
        )
}
