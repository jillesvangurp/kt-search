import java.util.*

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

repositories {
    mavenCentral()
}


// Stub secrets to let the project sync and build without the publication values set up
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null

val overrideKeys=listOf("signing.keyId","signing.password","signing.secretKeyRingFile","sonatypeUsername","sonatypePassword")

overrideKeys.forEach {
    ext[it]=null
}
val localProperties = Properties().apply {
    // override gradle.properties with properties in a file that is ignored in git
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use {
        load(it)
    }
}.also {ps ->
    overrideKeys.forEach {
        if(ps[it] != null) {
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


kotlin {
    jvm {
    }
    js(IR) {
        nodejs {
            testTask {
                useMocha {
                    // javascript is a lot slower than Java, we hit the default timeout of 2000
                    timeout = "20000"
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":json-dsl"))
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

        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}

