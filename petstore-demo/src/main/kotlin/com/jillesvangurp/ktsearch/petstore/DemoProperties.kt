package com.jillesvangurp.ktsearch.petstore

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties("demo")
data class DemoProperties(
    val elastic: ElasticProperties = ElasticProperties(),
    val indices: IndexNames = IndexNames(),
    @DefaultValue("classpath:data/pets.json")
    val sampleData: String = "classpath:data/pets.json"
)

data class ElasticProperties(
    @DefaultValue("localhost")
    val host: String = "localhost",
    @DefaultValue("9200")
    val port: Int = 9200,
    val https: Boolean = false,
    val username: String? = null,
    val password: String? = null,
)

data class IndexNames(
    @DefaultValue("pets-v1")
    val petsIndex: String = "pets-v1",
    @DefaultValue("pets-read")
    val petsRead: String = "pets-read",
    @DefaultValue("pets-write")
    val petsWrite: String = "pets-write",
    @DefaultValue("pet-search-v1")
    val petSearchIndex: String = "pet-search-v1",
    @DefaultValue("pet-search-read")
    val petSearchRead: String = "pet-search-read",
    @DefaultValue("pet-search-write")
    val petSearchWrite: String = "pet-search-write",
)
