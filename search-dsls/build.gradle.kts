@file:OptIn(ExperimentalWasmDsl::class)
@file:Suppress("DSL_SCOPE_VIOLATION")

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

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
    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    // Blocked on json-dsl and kotlinx-serialization-extensions support
    // iosSimulatorArm64()
    wasmJs {
        browser()
        nodejs()
        d8()
    }
    // not supported by kotest yet
//    wasmWasi()
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.json.dsl)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotest.assertions.core)
            }
        }
        jvmMain {
            dependencies {
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
        }
        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        all {
            languageSettings {
                languageVersion = "1.9"
                apiVersion = "1.9"
            }

            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}

tasks.named("iosSimulatorArm64Test") {
    // requires IOS simulator and tens of GB of other stuff to be installed
    // so keep it disabled
    enabled = false
}


