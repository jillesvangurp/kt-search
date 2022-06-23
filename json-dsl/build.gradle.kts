import org.gradle.api.tasks.testing.logging.TestExceptionFormat.*
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import java.util.Properties

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("org.jetbrains.dokka")
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
    js(BOTH) {
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

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging.exceptionFormat = FULL
    testLogging.events = setOf(
        FAILED,
        PASSED,
        SKIPPED,
        STANDARD_ERROR,
        STANDARD_OUT
    )
    addTestListener(object: TestListener {
        val failures = mutableListOf<String>()
        override fun beforeSuite(desc: TestDescriptor) {
        }

        override fun afterSuite(desc: TestDescriptor, result: TestResult) {
        }

        override fun beforeTest(desc: TestDescriptor) {
        }

        override fun afterTest(desc: TestDescriptor, result: TestResult) {
            if(result.resultType == TestResult.ResultType.FAILURE) {
                val report =
                    """
                    TESTFAILURE ${desc.className} - ${desc.name}
                    ${result.exception?.let { e->
                        """
                            ${e::class.simpleName} ${e.message}
                        """.trimIndent()
                    }}
                    -----------------
                    """.trimIndent()
                failures.add(report)
            }
        }
    })

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
//        repositories {
//            maven {
//                credentials {
//                    username = project.properties["ossrhUsername"]?.toString()
//                    password = project.properties["ossrhPassword"]?.toString()
//                }
//                url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2")
//            }
//        }
        publications.withType<MavenPublication> {
            artifact(dokkaJar)
        }
    }
}

signing {
    sign(publishing.publications)
}