@file:Suppress("DSL_SCOPE_VIOLATION")
import java.util.*

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use {
        load(it)
    }
}

fun getProperty(propertyName: String, defaultValue: Any? = null) =
    (localProperties[propertyName] ?: project.properties[propertyName]) ?: defaultValue

fun getBooleanProperty(propertyName: String) = getProperty(propertyName)?.toString().toBoolean()


plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}
repositories {
    mavenCentral()
    maven(url = "https://jitpack.io") {
        content {
            includeGroup("com.github.jillesvangurp")
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs= listOf("-Xcontext-receivers")
    }
}

val searchEngine: String = getProperty("searchEngine", "es-7").toString()

dependencies {
    testImplementation(project(":search-dsls"))
    testImplementation(project(":search-client"))
    testImplementation(libs.json.dsl)
    testImplementation(libs.kotlinx.serialization.extensions)

    testImplementation(kotlin("stdlib-jdk8"))
    testImplementation(libs.kotlinx.coroutines.jdk8)
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.logging)
    testImplementation(libs.ktor.client.serialization)
    testImplementation(libs.ktor.client.logging)
    testImplementation(libs.ktor.serialization.kotlinx)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.ktor.client.content.negotiation)


    // bring your own logging, but we need some in tests
    testImplementation(libs.slf4j.api)
    testImplementation(libs.slf4j.jcl.over)
    testImplementation(libs.slf4j.log4j.over)
    testImplementation(libs.slf4j.jul.to)
    testImplementation(libs.log4j.to.slf4j) // es seems to insist on log4j2
    testImplementation(libs.logback.classic)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)

    testImplementation(libs.kotlin4example)
    testImplementation(libs.kotlin.csv)
}
