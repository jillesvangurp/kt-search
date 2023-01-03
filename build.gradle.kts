import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("multiplatform") apply false
    id("org.jetbrains.dokka") apply false
}

println("project: $path")
println("version: $version")
println("group: $group")

subprojects {
    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinJvmCompile> {
        kotlinOptions {
            jvmTarget = "17"
            languageVersion = "1.7"
        }
    }

    tasks.register("versionCheck") {
        if(rootProject.version == "unspecified") {
            error("call with -Pversion=x.y.z to set a version and make sure it lines up with the current tag")
        }
    }

    tasks.withType<PublishToMavenRepository> {
        dependsOn("versionCheck")
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

//    apply(plugin = "signing")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    afterEvaluate {
        val dokkaJar = tasks.register<Jar>("dokkaJar") {
            from(tasks["dokkaHtml"])
            dependsOn(tasks["dokkaHtml"])
            archiveClassifier.set("javadoc")
        }

        configure<PublishingExtension> {
            // use this for testing configuration changes to publishing
//            repositories {
//                maven {
//                    println("file://$rootDir/localRepo")
//                    url = uri("file://$rootDir/localRepo")
//                }
//            }
            repositories {
                maven {
                    // GOOGLE_APPLICATION_CREDENTIALS env var must be set for this to work
                    // public repository is at https://maven.tryformation.com/releases
                    url = uri("gcs://mvn-public-tryformation/releases")
                    name = "FormationPublic"
                }
            }

            publications {
                create<MavenPublication>("mavenJava") {

                    pom {
                        description.set("Kts extensions for kt-search. Easily script operations for Elasticsearch and Opensearch with .main.kts scripts")
                        name.set(artifactId)
                        url.set("https://github.com/jillesvangurp/kt-search")
                        licenses {
                            license {
                                name.set("MIT")
                                url.set("https://github.com/jillesvangurp/kt-search/LICENSE")
                                distribution.set("repo")
                            }
                        }
                        developers {
                            developer {
                                id.set("jillesvangurp")
                                name.set("Jilles van Gurp")
                            }
                        }
                        scm {
                            url.set("https://github.com/jillesvangurp/kt-search/LICENSE")
                        }
                    }

                }
            }

//            publications.withType<MavenPublication> {
//                artifact(dokkaJar)
//            }
        }
    }
}


