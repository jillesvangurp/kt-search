import com.avast.gradle.dockercompose.ComposeExtension
import java.io.File

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

configure<ComposeExtension> {
    buildAdditionalArgs.set(listOf("--force-rm"))
    stopContainers.set(true)
    removeContainers.set(true)
    forceRecreate.set(true)
    val parentDir = project.parent?.projectDir ?: error("parent should exist")
    val composeFile = parentDir.resolve("docker-compose-es-9.yml").absolutePath
    dockerComposeWorkingDirectory.set(parentDir)
    useComposeFiles.set(listOf(composeFile))
    startedServices.set(listOf("es9"))
}

val composeUp by tasks.named("composeUp")

if (!listOf("/usr/bin/docker", "/usr/local/bin/docker", "/opt/homebrew/bin/docker").any { File(it).exists() }) {
    tasks.matching { it.name.startsWith("compose") }.configureEach {
        enabled = false
    }
}

tasks.withType<Test> {
    dependsOn(composeUp)
    useJUnitPlatform()
}
