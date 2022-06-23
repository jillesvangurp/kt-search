@file:Suppress("UNUSED_VARIABLE")

import com.avast.gradle.dockercompose.ComposeExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import java.net.URI
import java.net.URL
import java.util.Properties

// Stub secrets to let the project sync and build without the publication values set up
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null

val overrideKeys=listOf("signing.keyId","signing.password","signing.secretKeyRingFile","ossrhUsername","ossrhPassword")

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


plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.avast.gradle.docker-compose")
    id("maven-publish")
    signing
}

repositories {
    mavenCentral()
}

// publishing
apply(plugin = "maven-publish")
apply(plugin = "org.jetbrains.dokka")

version = project.property("libraryVersion") as String
println("project: $path")
println("version: $version")
println("group: $group")


val searchEngine = getStringProperty("searchEngine", "es-7")

kotlin {
    jvm {
    }
    js(IR) {
        nodejs {
            testTask {
                useMocha {
                    // javascript is a lot slower than Java, we hit the default timeout of 2000
                    timeout = "30s"
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common", "_"))
                implementation(project(":search-dsls"))
                implementation(project(":json-dsl"))
                implementation(KotlinX.datetime)
                implementation(Ktor.client.core)
                implementation(KotlinX.coroutines.core)

                implementation(KotlinX.serialization.json)
                implementation(Ktor.client.core)
                implementation(Ktor.client.logging)
                implementation(Ktor.client.serialization)
                implementation("io.ktor:ktor-client-logging:_")
                implementation("io.ktor:ktor-serialization-kotlinx:_")
                implementation("io.ktor:ktor-serialization-kotlinx-json:_")
                implementation("io.ktor:ktor-client-content-negotiation:_")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common", "_"))
                implementation(kotlin("test-annotations-common", "_"))
                implementation(Testing.kotest.assertions.core)
                implementation(KotlinX.coroutines.test)
                implementation("io.github.microutils:kotlin-logging:_")
            }
        }
        val jvmMain by existing {
            dependencies {
                implementation(Ktor.client.cio)
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
                implementation("io.ktor:ktor-client-js:_")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js", "_"))
            }
        }

        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}


configure<ComposeExtension> {
    buildAdditionalArgs.set(listOf("--force-rm"))
    stopContainers.set(true)
    removeContainers.set(true)
    forceRecreate.set(true)
    useComposeFiles.set(listOf("../docker-compose-$searchEngine.yml"))
}

tasks.named("jsNodeTest") {
    // on gh actions jsNodeTest manages to run before tasks of type
    // Test are initialized. So explicitly bring up compose before jsNodeTest fixes
    // that problem
    val isUp = try {
        URL("http://localhost:9999").openConnection().connect()
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
        URL("http://localhost:9999").openConnection().connect()
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
//    if(!isUp) {
    // called by docs
    //        this.finalizedBy("composeDown")
//    }
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = "11"
        languageVersion = "1.6"
    }
}

afterEvaluate {
    val dokkaJar = tasks.register<Jar>("dokkaJar") {
        from(tasks["dokkaHtml"])
        dependsOn(tasks["dokkaHtml"])
        archiveClassifier.set("javadoc")
    }

    configure<PublishingExtension> {
        repositories {
            maven {
                credentials {
                    username = project.properties["ossrhUsername"]?.toString()
                    password = project.properties["ossrhPassword"]?.toString()
                }
                url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            }
        }
        publications.withType<MavenPublication> {
            artifact(dokkaJar)
        }
    }
}

signing {
    sign(publishing.publications)

}