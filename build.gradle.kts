buildscript {
    repositories {
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.jillesvangurp")
                includeGroup("com.github.jillesvangurp.es-kotlin-codegen-plugin")
            }
        }
    }
    dependencies {
        classpath("com.github.jillesvangurp:es-kotlin-codegen-plugin:_")
    }
}

plugins {
    kotlin("multiplatform") apply false
    id("org.jetbrains.dokka") apply false
//    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

println("project: $path")
println("version: $version")
println("group: $group")

subprojects {
    version = project.property("libraryVersion") as String

    repositories {
        mavenCentral()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile> {
        kotlinOptions {
            jvmTarget = "11"
            languageVersion = "1.6"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        testLogging.events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
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
    }

    apply(plugin = "signing")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    afterEvaluate {
        val dokkaJar = tasks.register<Jar>("dokkaJar") {
            from(tasks["dokkaHtml"])
            dependsOn(tasks["dokkaHtml"])
            archiveClassifier.set("javadoc")
        }

        configure<PublishingExtension> {
            repositories {
                maven {
                    println("file://$rootDir/localRepo")
                    url = uri("file://$rootDir/localRepo")
                }
            }

            publications.withType<MavenPublication> {
                artifact(dokkaJar)
            }
        }
    }
}


