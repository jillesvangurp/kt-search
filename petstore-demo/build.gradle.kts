plugins {
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("com.avast.gradle.docker-compose")
    application
}

extra["composeSkipTestsWithoutDocker"] = true

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
    maven(url = "https://jitpack.io") {
        content {
            includeGroup("com.github.jillesvangurp")
        }
    }
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
    testImplementation("com.github.jillesvangurp:kotlin4example:_")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
