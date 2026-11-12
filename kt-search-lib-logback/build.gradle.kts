import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    `maven-publish`
    id("com.avast.gradle.docker-compose")
}

group = rootProject.group
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(project(":search-client"))
    api("ch.qos.logback:logback-classic:_")

    testImplementation(kotlin("test-junit5", "_"))
    testImplementation(Testing.junit.jupiter.api)
    testRuntimeOnly(Testing.junit.jupiter.engine)
    testImplementation(Testing.kotest.assertions.core)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                description.set(
                    "Optional Logback appender that bulk indexes logs " +
                        "to Elasticsearch and OpenSearch using KT Search."
                )
            }
        }
    }
}

afterEvaluate {
    publishing.publications.named<MavenPublication>("mavenJava") {
        artifact(tasks.named("dokkaJar"))
    }
}
