import com.avast.gradle.dockercompose.ComposeExtension
import java.io.File
import java.net.URI
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.avast.gradle.docker-compose")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    js {
        browser()
        nodejs {
            testTask(Action {
                useMocha {
                    // javascript is a lot slower than Java, we hit the default timeout of 2000
                    timeout = "20s"
                }
            })
        }
    }

    if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
        fun KotlinNativeTarget.configureLinuxTarget() {
            binaries {
                all {
                    linkerOpts = System.getenv()
                        .getOrDefault("LDFLAGS", "")
                        .split(":")
                        .filter { it.isNotBlank() }
                        .map { "-L$it" }
                        .toMutableList()
                }
            }
        }
        linuxX64 { configureLinuxTarget() }
        linuxArm64 { configureLinuxTarget() }
    }
    mingwX64()
    macosX64()
    macosArm64()
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { }
        nodejs { }
        d8 { }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":search-client"))
                implementation(kotlin("stdlib-common", "_"))
                implementation(KotlinX.coroutines.core)
                implementation(KotlinX.datetime)
                implementation("io.github.oshai:kotlin-logging:_")
                implementation(Ktor.client.core)
            }
        }
       commonTest {
            dependencies {
                implementation(kotlin("test-common", "_"))
                implementation(kotlin("test-annotations-common", "_"))
                implementation(Testing.kotest.assertions.core)
                implementation(KotlinX.coroutines.test)
            }
        }
        jvmMain {
            dependencies {
                implementation(Ktor.client.cio)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5", "_"))
                implementation(Testing.junit.jupiter.api)
                implementation(Testing.junit.jupiter.engine)
                implementation("ch.qos.logback:logback-classic:_")
                implementation(Ktor.client.mock)
            }
        }

        jsMain {
        }
        jsTest {
            dependencies {
                implementation(kotlin("test-js", "_"))
            }
        }

        wasmJsTest {
            dependencies {
                implementation(kotlin("test-wasm-js", "_"))
            }
        }


        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlin.time.ExperimentalTime")
                languageVersion = "2.1"
                apiVersion = "2.1"
            }
        }
    }
}

listOf(
    "iosSimulatorArm64Test",
    "wasmJsTest",
    "wasmJsBrowserTest",
    "wasmJsNodeTest",
    "wasmJsD8Test"
).forEach { target ->
    tasks.named(target) {
        enabled = false
    }
}

configure<ComposeExtension> {
    buildAdditionalArgs.set(listOf("--force-rm"))
    stopContainers.set(true)
    removeContainers.set(true)
    forceRecreate.set(true)
    val parentDir = project.parent?.projectDir?.path ?: error("parent should exist")
    val searchEngine = project.findProperty("searchEngine")?.toString() ?: "es-9"
    val composeFile = "$parentDir/docker-compose-$searchEngine.yml"
    dockerComposeWorkingDirectory.set(project.parent!!.projectDir)
    useComposeFiles.set(listOf(composeFile))
    val dockerExecutablePath = listOf("/usr/bin/docker", "/usr/local/bin/docker").firstOrNull { File(it).exists() }
    if (dockerExecutablePath != null) {
        dockerExecutable.set(dockerExecutablePath)
    }
}

val composeUp by tasks.named("composeUp")

tasks.named("jsNodeTest") {
    val isUp = kotlin.runCatching {
        URI("http://localhost:9999").toURL().openConnection().connect()
    }.isSuccess
    if (!isUp) {
        dependsOn(composeUp)
    }
}

tasks.withType<Test> {
    val isUp = kotlin.runCatching {
        URI("http://localhost:9999").toURL().openConnection().connect()
    }.isSuccess
    if (!isUp) {
        dependsOn(composeUp)
    }
    useJUnitPlatform()
    testLogging.exceptionFormat = FULL
    testLogging.events = setOf(FAILED, PASSED, SKIPPED, STANDARD_ERROR, STANDARD_OUT)
}

if (!listOf("/usr/bin/docker", "/usr/local/bin/docker").any { File(it).exists() }) {
    tasks.matching { it.name.startsWith("compose") }.configureEach {
        enabled = false
    }
}
