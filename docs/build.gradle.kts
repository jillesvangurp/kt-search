import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL
import java.util.*

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use {
        load(it)
    }
}

fun getProperty(propertyName: String, defaultValue: Any?=null) = (localProperties[propertyName] ?: project.properties[propertyName]) ?: defaultValue
fun getBooleanProperty(propertyName: String) = getProperty(propertyName)?.toString().toBoolean()


plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io") {
        content {
            includeGroup("com.github.jillesvangurp")
        }
    }
}

tasks.withType<KotlinCompile> {

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
        freeCompilerArgs += "kotlinx.coroutines.ExperimentalCoroutinesApi"
    }
    this.sourceFilesExtensions
}

val searchEngine: String = getProperty("searchEngine", "es-7").toString()

tasks.withType<Test> {
    val isUp = try {
        URL("http://localhost:9999").openConnection().connect()
        true
    } catch (e: Exception) {
        false
    }
    if(!isUp) {
        dependsOn(
            ":search-client:composeUp"
        )
    }
    useJUnitPlatform()
    testLogging.exceptionFormat = TestExceptionFormat.FULL
    testLogging.events = setOf(
        TestLogEvent.FAILED,
        TestLogEvent.PASSED,
        TestLogEvent.SKIPPED,
        TestLogEvent.STANDARD_ERROR,
        TestLogEvent.STANDARD_OUT
    )
    if(!isUp) {
        // legacy-client finishes last ...
        // FIXME more robust shut down mechanism
//        this.finalizedBy(":search-client:composeDown")
    }
}

dependencies {
    testImplementation(project(":json-dsl"))
    testImplementation(project(":search-dsls"))
    testImplementation(project(":search-client"))

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


    testImplementation("io.github.microutils:kotlin-logging:_")

    // bring your own logging, but we need some in tests
    testImplementation("org.slf4j:slf4j-api:_")
    testImplementation("org.slf4j:jcl-over-slf4j:_")
    testImplementation("org.slf4j:log4j-over-slf4j:_")
    testImplementation("org.slf4j:jul-to-slf4j:_")
    testImplementation("org.apache.logging.log4j:log4j-to-slf4j:_") // es seems to insist on log4j2
    testImplementation("ch.qos.logback:logback-classic:_")

    testImplementation(Testing.junit.jupiter.api)
    testImplementation(Testing.junit.jupiter.engine)

    testImplementation("com.github.jillesvangurp:kotlin4example:_")
}