@file:OptIn(ExperimentalWasmDsl::class)
@file:Suppress("DSL_SCOPE_VIOLATION")

import com.avast.gradle.dockercompose.ComposeExtension
import java.net.URI
import java.util.Properties
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
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.docker.compose)
}

repositories {
    mavenCentral()
}

val searchEngine = getStringProperty("searchEngine", "es-8")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    jvm {
        // should work for android as well
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
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
    // iOS targets
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
        }
        nodejs {
        }
        d8 {
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(kotlin("stdlib-common"))
                api(project(":search-dsls"))
                api(libs.json.dsl)
                api(libs.kotlinx.serialization.extensions)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)

                api(libs.kotlin.logging)
                api(libs.kotlinx.serialization.json)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.serialization)
                implementation(libs.ktor.client.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmMain {
            dependencies {
                api(libs.ktor.client.cio)
                api(libs.ktor.client.java)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.logback.classic)

                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.jupiter.engine)
            }
        }
        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        wasmJsMain {
            dependencies {
                implementation(libs.ktor.client.js.wasm.js)
            }
        }

        wasmJsTest {
            dependencies {
                implementation(kotlin("test-wasm-js"))
            }
        }

        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        macosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
            linuxMain {
                dependencies {
                    implementation(libs.ktor.client.curl)
                }
            }
        }

        mingwMain {
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }

        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                // expect-actual-classes
                languageVersion = "2.0"
                apiVersion = "2.0"
            }
        }
    }
}

listOf("iosSimulatorArm64Test","wasmJsTest","wasmJsBrowserTest","wasmJsNodeTest","wasmJsD8Test").forEach {target->
    // skip the test weirdness for now
    tasks.named(target) {
        // requires IOS simulator and tens of GB of other stuff to be installed
        // so keep it disabled
        enabled = false
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

    listOf("/usr/bin/docker", "/usr/local/bin/docker").firstOrNull {
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
    val isUp = kotlin.runCatching {
        URI("http://localhost:9999").toURL().openConnection().connect()
    }.isSuccess
    if (!isUp) {
        dependsOn(
            "composeUp"
        )
    }
}

tasks.withType<Test> {
    val isUp = kotlin.runCatching {
        URI("http://localhost:9999").toURL().openConnection().connect()
    }.isSuccess
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
        override fun beforeSuite(desc: TestDescriptor) = Unit

        override fun afterSuite(desc: TestDescriptor, result: TestResult) = Unit

        override fun beforeTest(desc: TestDescriptor) = Unit

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

