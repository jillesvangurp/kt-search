import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.gradleup.shadow")
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io") {
        content {
            includeGroup("com.github.jillesvangurp")
        }
    }
}

val enableNativeTargets = true
val isLinuxHost = OperatingSystem.current().isLinux
val isMacHost = OperatingSystem.current().isMacOsX
val enableLinuxTargetsOnMac = providers
    .gradleProperty("ktsearch.enableLinuxTargetsOnMac")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val enableWasmCli = providers
    .gradleProperty("ktsearch.enableWasmCli")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val linuxOnlyNativeTargets = providers
    .gradleProperty("ktsearch.linuxOnlyNativeTargets")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val enableLinuxX64Target = isLinuxHost || (isMacHost && enableLinuxTargetsOnMac)
val enableLinuxArm64Target = isLinuxHost

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    if (enableNativeTargets) {
        if (enableLinuxX64Target) {
            linuxX64 {
                binaries {
                    executable {
                        baseName = "ktsearch"
                        entryPoint = "com.jillesvangurp.ktsearch.cli.main"
                    }
                }
            }
        }
        if (enableLinuxArm64Target) {
            linuxArm64 {
                binaries {
                    executable {
                        baseName = "ktsearch"
                        entryPoint = "com.jillesvangurp.ktsearch.cli.main"
                    }
                }
            }
        }
        if (!linuxOnlyNativeTargets) {
            mingwX64 {
                binaries {
                    executable {
                        baseName = "ktsearch"
                        entryPoint = "com.jillesvangurp.ktsearch.cli.main"
                    }
                }
            }

            if (isMacHost) {
                macosX64 {
                    binaries {
                        executable {
                            baseName = "ktsearch"
                            entryPoint = "com.jillesvangurp.ktsearch.cli.main"
                        }
                    }
                }
                macosArm64 {
                    binaries {
                        executable {
                            baseName = "ktsearch"
                            entryPoint = "com.jillesvangurp.ktsearch.cli.main"
                        }
                    }
                }
            }
        }
    }

    if (enableWasmCli) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            nodejs()
            binaries.executable()
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":search-client"))
                implementation(KotlinX.coroutines.core)
                implementation("com.github.ajalt.clikt:clikt:_")
                implementation("com.github.ajalt.mordant:mordant:_")
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
                implementation(kotlin("stdlib", "_"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5", "_"))
                implementation(Testing.junit.jupiter.api)
                implementation(Testing.junit.jupiter.engine)
                implementation("com.github.jillesvangurp:kotlin4example:_")
            }
        }
        nativeMain {
            dependencies {
                implementation("com.squareup.okio:okio:_")
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

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<ShadowJar>("jvmFatJar") {
    group = "build"
    description = "Builds runnable JVM fat jar for ktsearch-cli"
    archiveBaseName.set("ktsearch-cli")
    archiveClassifier.set("all")

    val jvmMainCompilation = kotlin.targets
        .getByName("jvm")
        .compilations
        .getByName("main")

    from(jvmMainCompilation.output)
    configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    manifest {
        attributes["Main-Class"] = "com.jillesvangurp.ktsearch.cli.MainKt"
    }
    dependsOn("jvmJar")
}
