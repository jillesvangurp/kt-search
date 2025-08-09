@file:Suppress("DSL_SCOPE_VIOLATION")

import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.WARNING
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.versions)
}

println("project: $path")
println("version: $version")
println("group: $group")

allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.tryformation.com/releases") {
            content {
                includeGroup("com.jillesvangurp")
            }
        }
    }
}

subprojects {

    tasks.register("versionCheck") {
        doLast {
            if (rootProject.version == "unspecified") {
                error("call with -Pversion=x.y.z to set a version and make sure it lines up with the current tag")
            }
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

    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    afterEvaluate {
        tasks.register<Jar>("dokkaJar") {
            from(tasks["dokkaHtml"])
            dependsOn(tasks["dokkaHtml"])
            archiveClassifier.set("javadoc")
        }

        configure<PublishingExtension> {
            publications {
                withType<MavenPublication> {
                    pom {
                        name.set("KtSearch")
                        url.set("https://github.com/jillesvangurp/kt-search")

                        licenses {
                            license {
                                name.set("MIT License")
                                url.set("https://github.com/jillesvangurp/kt-search/blob/master/LICENSE")
                            }
                        }

                        developers {
                            developer {
                                id.set("jillesvangurp")
                                name.set("Jilles van Gurp")
                                email.set("jilles@no-reply.github.com")
                            }
                        }

                        scm {
                            connection.set("scm:git:git://github.com/jillesvangurp/kt-search.git")
                            developerConnection.set("scm:git:ssh://github.com:jillesvangurp/kt-search.git")
                            url.set("https://github.com/jillesvangurp/kt-search")
                        }
                    }
                }
            }

            repositories {
                maven {
                    // GOOGLE_APPLICATION_CREDENTIALS env var must be set for this to work
                    // public repository is at https://maven.tryformation.com/releases
                    url = uri("gcs://mvn-public-tryformation/releases")
                    name = "FormationPublic"
                }
            }
        }
    }
}


