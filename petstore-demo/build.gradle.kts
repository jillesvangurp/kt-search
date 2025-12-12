import com.avast.gradle.dockercompose.ComposeExtension
import java.io.File
import java.net.URI

plugins {
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("com.avast.gradle.docker-compose")
    application
}

application {
    mainClass.set("com.jillesvangurp.ktsearch.petstore.PetStoreDemoApplicationKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":search-client"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

val dockerExecutablePath = listOf("/usr/bin/docker", "/usr/local/bin/docker", "/opt/homebrew/bin/docker")
    .firstOrNull { File(it).exists() }
val dockerAvailable = dockerExecutablePath != null

configure<ComposeExtension> {
    buildAdditionalArgs.set(listOf("--force-rm"))
    stopContainers.set(true)
    removeContainers.set(true)
    forceRecreate.set(true)
    val parentDir = project.parent?.projectDir ?: error("parent should exist")
    val searchEngine = project.findProperty("searchEngine")?.toString() ?: "es-9"
    val composeFile = parentDir.resolve("docker-compose-$searchEngine.yml").absolutePath
    dockerComposeWorkingDirectory.set(parentDir)
    useComposeFiles.set(listOf(composeFile))
    startedServices.set(listOf(searchEngine.replace("-", "")))

    dockerExecutablePath?.let { docker ->
        dockerExecutable.set(docker)
    }
}

val composeUp by tasks.named("composeUp")

fun isSearchUp() = kotlin.runCatching {
    URI("http://localhost:9999").toURL().openConnection().connect()
}.isSuccess

if (!dockerAvailable) {
    tasks.matching { it.name.startsWith("compose") }.configureEach {
        enabled = false
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    when {
        isSearchUp() -> logger.lifecycle("Using already running search cluster on port 9999")
        dockerAvailable -> dependsOn(composeUp)
        else -> {
            logger.lifecycle("Skipping tests because Elasticsearch is not running and Docker is unavailable.")
            enabled = false
        }
    }
}
