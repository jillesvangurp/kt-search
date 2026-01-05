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
    kotlin("plugin.serialization")
    id("com.avast.gradle.docker-compose")
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
        optIn.addAll(
            "kotlin.RequiresOptIn",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlin.time.ExperimentalTime"
        )
    }
}

val searchEngine: String = getProperty("searchEngine", "es-7").toString()

dependencies {
    testImplementation(project(":search-dsls"))
    testImplementation(project(":search-client"))
    testImplementation("com.jillesvangurp:json-dsl:_")
    testImplementation("com.jillesvangurp:kotlinx-serialization-extensions:_")

    testImplementation(Kotlin.stdlib.jdk8)
    testImplementation(KotlinX.coroutines.jdk8)
    testImplementation(KotlinX.datetime)
    testImplementation(Ktor.client.core)
    testImplementation(KotlinX.coroutines.core)

    testImplementation(KotlinX.serialization.json)
    testImplementation(Ktor.client.core)
    testImplementation(Ktor.client.logging)
    testImplementation(Ktor.client.serialization)
    testImplementation("io.ktor:ktor-client-logging:_")
    testImplementation("io.ktor:ktor-serialization-kotlinx:_")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:_")
    testImplementation("io.ktor:ktor-client-content-negotiation:_")


    // bring your own logging, but we need some in tests
    testImplementation("org.slf4j:slf4j-api:_")
    testImplementation("org.slf4j:jcl-over-slf4j:_")
    testImplementation("org.slf4j:log4j-over-slf4j:_")
    testImplementation("org.slf4j:jul-to-slf4j:_")
    testImplementation("org.apache.logging.log4j:log4j-to-slf4j:_") // es seems to insist on log4j2
    testImplementation("ch.qos.logback:logback-classic:_")

    testImplementation(kotlin("test-junit5"))
    testImplementation(Testing.junit.jupiter.api)
    testImplementation(Testing.junit.jupiter.engine)

    testImplementation("com.github.jillesvangurp:kotlin4example:_")
    testImplementation("com.github.doyaaaaaken:kotlin-csv:_")
}
