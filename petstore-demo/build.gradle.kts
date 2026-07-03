plugins {
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
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

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" &&
            requested.name.startsWith("kotlinx-coroutines")
        ) {
            useVersion("1.11.0")
        }
    }
}

dependencies {
    implementation(project(":search-client"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(KotlinX.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:_")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:_")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(Testing.kotest.assertions.core)
    testImplementation("com.github.jillesvangurp:kotlin4example:_")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
