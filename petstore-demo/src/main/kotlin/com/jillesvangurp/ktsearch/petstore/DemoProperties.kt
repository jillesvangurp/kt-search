package com.jillesvangurp.ktsearch.petstore

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties("demo")
data class DemoProperties(
    // Connection settings for Elasticsearch/OpenSearch. Override via environment
    // variables or application.yml when you want to point the demo elsewhere.
    val elastic: ElasticProperties = ElasticProperties(),
    // Central place that keeps read/write aliases in sync so the service code
    // doesn't have to worry about hard-coded strings.
    val indices: IndexNames = IndexNames(),
    @DefaultValue("classpath:data/pets.json")
    val sampleData: String = "classpath:data/pets.json"
)

data class ElasticProperties(
    // Host and port match the docker-compose defaults; change them to reuse an
    // existing cluster.
    @DefaultValue("localhost")
    val host: String = "localhost",
    @DefaultValue("9200")
    val port: Int = 9200,
    // Flip this when talking to a secured Elastic Cloud deployment.
    val https: Boolean = false,
    // Leave credentials null for an unsecured development node.
    val username: String? = null,
    val password: String? = null,
)

data class IndexNames(
    // Primary CRUD index that stores the raw pet documents.
    @DefaultValue("pets-v1")
    val petsIndex: String = "pets-v1",
    // Read alias for pets, swapped when we need to roll over to a new version.
    @DefaultValue("pets-read")
    val petsRead: String = "pets-read",
    // Write alias for pets; all mutations should go through this name.
    @DefaultValue("pets-write")
    val petsWrite: String = "pets-write",
    // Search-optimized projection that enriches pets with derived fields.
    @DefaultValue("pet-search-v1")
    val petSearchIndex: String = "pet-search-v1",
    // Read alias for the search projection.
    @DefaultValue("pet-search-read")
    val petSearchRead: String = "pet-search-read",
    // Write alias used by the enrichment pipeline.
    @DefaultValue("pet-search-write")
    val petSearchWrite: String = "pet-search-write",
)
