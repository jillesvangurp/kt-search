plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
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
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common", "_"))
                implementation("com.github.jillesvangurp:json-dsl:_")

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
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}

