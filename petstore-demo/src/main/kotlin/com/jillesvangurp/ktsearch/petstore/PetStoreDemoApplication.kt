package com.jillesvangurp.ktsearch.petstore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(DemoProperties::class)
class PetStoreDemoApplication

fun main(args: Array<String>) {
    // Bootstraps Spring and the coroutine-based search infrastructure defined in this
    // module; nothing fancy happens here beyond wiring beans and starting the web server.
    runApplication<PetStoreDemoApplication>(*args)
}
