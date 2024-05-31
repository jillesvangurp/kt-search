@file:OptIn(ExperimentalWasmDsl::class)

import com.avast.gradle.dockercompose.ComposeExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.net.URI
import java.net.URL
import java.util.*
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

// Stub secrets to let the project sync and build without the publication values set up
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null

val overrideKeys =
    listOf("signing.keyId", "signing.password", "signing.secretKeyRingFile", "sonatypeUsername", "sonatypePassword")

overrideKeys.forEach {
    ext[it] = null
}
val localProperties = Properties().apply {
    // override gradle.properties with properties in a file that is ignored in git
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use {
        load(it)
    }
}.also { ps ->
    overrideKeys.forEach {
        if (ps[it] != null) {
            println("override $it from local.properties")
            ext[it] = ps[it]
        }
    }
}

fun getProperty(propertyName: String, defaultValue: Any? = null) =
    (localProperties[propertyName] ?: project.properties[propertyName]) ?: defaultValue

fun getStringProperty(propertyName: String, defaultValue: String) =
    getProperty(propertyName)?.toString() ?: defaultValue

fun getBooleanProperty(propertyName: String) = getProperty(propertyName)?.toString().toBoolean()


plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.avast.gradle.docker-compose")
}

repositories {
    mavenCentral()
}

val searchEngine = getStringProperty("searchEngine", "es-8")

kotlin {
    jvm {
    }
    js(IR) {
        browser {
            testTask {
                useMocha {
                    // javascript is a lot slower than Java, we hit the default timeout of 2000
                    timeout = "30s"
                }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    // javascript is a lot slower than Java, we hit the default timeout of 2000
                    timeout = "30s"
                }
            }
        }
    }
    // several issues with the linux build that prevent this from working
    // keep disabled for now.
//    if(DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
//        // some weird linking error
//        linuxX64()
//        // lib curl is not found for this one :-(
//        linuxArm64()
//    }
    mingwX64()
    macosX64()
    macosArm64()
    // Blocked on ktor-client support
//    wasmJs {
//        browser()
//        nodejs()
//        d8()
//    }

    sourceSets {
        commonMain {
            dependencies {
                api(kotlin("stdlib-common", "_"))
                api(project(":search-dsls"))
                api("com.jillesvangurp:json-dsl:_")
                api("com.jillesvangurp:kotlinx-serialization-extensions:_")
                api(KotlinX.datetime)
                implementation(Ktor.client.core)
                api(KotlinX.coroutines.core)

                api(KotlinX.serialization.json)
                api(Ktor.client.core)
                api(Ktor.client.auth)
                api(Ktor.client.logging)
                api(Ktor.client.serialization)
                api("io.github.microutils:kotlin-logging:_")
                implementation("io.ktor:ktor-client-logging:_")
                implementation("io.ktor:ktor-serialization-kotlinx:_")
                implementation("io.ktor:ktor-serialization-kotlinx-json:_")
                implementation("io.ktor:ktor-client-content-negotiation:_")

            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common", "_"))
                implementation(kotlin("test-annotations-common", "_"))
                implementation(Testing.kotest.assertions.core)
                implementation(KotlinX.coroutines.test)
                api("io.github.microutils:kotlin-logging:_")
            }
        }
        jvmMain {
            dependencies {
                implementation(Ktor.client.cio)
                api(Ktor.client.java)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5", "_"))
                implementation("ch.qos.logback:logback-classic:_")

                implementation(Testing.junit.jupiter.api)
                implementation(Testing.junit.jupiter.engine)
            }
        }
        jsMain {
            dependencies {
                implementation("io.ktor:ktor-client-js:_")
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test-js", "_"))
            }
        }

        nativeMain {
            dependencies {
                implementation(Ktor.client.curl)
            }
        }

        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                languageVersion = "1.9"
                apiVersion = "1.9"
            }

        }
    }
}


configure<ComposeExtension> {
    buildAdditionalArgs.set(listOf("--force-rm"))
    stopContainers.set(true)
    removeContainers.set(true)
    forceRecreate.set(true)
    val parentDir = project.parent?.projectDir?.path ?: error("parent should exist")
    val composeFile = "$parentDir/docker-compose-$searchEngine.yml"
    dockerComposeWorkingDirectory.set(project.parent!!.projectDir)
    useComposeFiles.set(listOf(composeFile))

    listOf("/usr/bin/docker","/usr/local/bin/docker").firstOrNull {
        File(it).exists()
    }?.let { docker ->
        // works around an issue where the docker
        // command is not found
        // falls back to the default, which may work on
        // some platforms
        dockerExecutable.set(docker)
    }

}

tasks.named("jsNodeTest") {
    // on gh actions jsNodeTest manages to run before tasks of type
    // Test are initialized. So explicitly bring up compose before jsNodeTest fixes
    // that problem
    val isUp = try {
        URI("http://localhost:9999").toURL().openConnection().connect()
        true
    } catch (e: Exception) {
        false
    }
    if (!isUp) {
        dependsOn(
            "composeUp"
        )
    }
}

tasks.withType<Test> {
    val isUp = try {
        URI("http://localhost:9999").toURL().openConnection().connect()
        true
    } catch (e: Exception) {
        false
    }
    if (!isUp) {
        dependsOn(
            "composeUp"
        )
    }
    useJUnitPlatform()
    // run tests in parallel
    systemProperties["junit.jupiter.execution.parallel.enabled"] = "true"
    // executes test classes concurrently
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    // executes tests inside a class concurrently
    systemProperties["junit.jupiter.execution.parallel.mode.classes.default"] = "concurrent"
    systemProperties["junit.jupiter.execution.parallel.config.strategy"] = "dynamic"
    // random order of test class execution
    systemProperties["junit.jupiter.testclass.order.default"] = "org.junit.jupiter.api.ClassOrderer\$Random"

    testLogging.exceptionFormat = FULL
    testLogging.events = setOf(
        FAILED,
        PASSED,
        SKIPPED,
        STANDARD_ERROR,
        STANDARD_OUT
    )
    addTestListener(object : TestListener {
        val failures = mutableListOf<String>()
        override fun beforeSuite(desc: TestDescriptor) {
        }

        override fun afterSuite(desc: TestDescriptor, result: TestResult) {
        }

        override fun beforeTest(desc: TestDescriptor) {
        }

        override fun afterTest(desc: TestDescriptor, result: TestResult) {
            if (result.resultType == TestResult.ResultType.FAILURE) {
                val report =
                    """
                    TESTFAILURE ${desc.className} - ${desc.name}
                    ${
                        result.exception?.let { e ->
                            """
                            ${e::class.simpleName} ${e.message}
                        """.trimIndent()
                        }
                    }
                    -----------------
                    """.trimIndent()
                failures.add(report)
            }
        }
    })
}

