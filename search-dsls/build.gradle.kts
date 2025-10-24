@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val enableNativeTargets =
    !(OperatingSystem.current().isLinux && System.getProperty("os.arch") == "aarch64")

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
    if (enableNativeTargets) {
        linuxX64()
        linuxArm64()
        mingwX64()
        macosX64()
        macosArm64()
        iosArm64()
        iosX64()
        iosSimulatorArm64()
    }
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
                implementation(kotlin("stdlib-common", "_"))
                implementation("com.jillesvangurp:json-dsl:_")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common", "_"))
                implementation(kotlin("test-annotations-common", "_"))
                implementation(Testing.kotest.assertions.core)
            }
        }
        jvmMain {
            dependencies {
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
        }
        jsTest {
            dependencies {
                implementation(kotlin("test-js", "_"))
            }
        }

        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlin.time.ExperimentalTime")
                // expect-actual-classes
                languageVersion = "2.1"
                apiVersion = "2.1"
            }

            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
}

if (enableNativeTargets) {
    tasks.named("iosSimulatorArm64Test") {
        // requires IOS simulator and tens of GB of other stuff to be installed
        // so keep it disabled
        enabled = false
    }
}


