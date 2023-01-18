@file:Suppress("UNUSED_VARIABLE")

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
    }
    js(IR) {
        nodejs {
            testTask {
                useMocha {
                    // javascript is a lot slower than Java, we hit the default timeout of 2000
                    timeout = "20s"
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common", "_"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common", "_"))
                implementation(kotlin("test-annotations-common", "_"))
                implementation(Testing.kotest.assertions.core)
            }
        }
        val jvmMain by existing {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5", "_"))
                implementation("ch.qos.logback:logback-classic:_")

                implementation(Testing.junit.jupiter.api)
                implementation(Testing.junit.jupiter.engine)
            }
        }
        val jsMain by existing {
            dependencies {
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js", "_"))
            }
        }
    }
}
