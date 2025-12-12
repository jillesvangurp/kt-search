package com.jillesvangurp.ktsearch.petstore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(DemoProperties::class)
class PetStoreDemoApplication

fun main(args: Array<String>) {
    runApplication<PetStoreDemoApplication>(*args)
}
