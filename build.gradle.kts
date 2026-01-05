import com.avast.gradle.dockercompose.ComposeExtension
import java.io.File
import java.net.URI
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask

plugins {
    kotlin("multiplatform") apply false
    id("org.jetbrains.dokka") apply false
    id("com.avast.gradle.docker-compose") apply false
}

println("project: $path")
println("version: $version")
println("group: $group")

abstract class ComposeUpLockService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    override fun close() = Unit
}

val composeUpLock = gradle.sharedServices.registerIfAbsent(
    "composeUpLock",
    ComposeUpLockService::class
) {
    maxParallelUsages.set(1)
}

fun findDockerExecutablePath(): String? =
    listOf("/usr/bin/docker", "/usr/local/bin/docker", "/opt/homebrew/bin/docker")
        .firstOrNull { File(it).exists() }

fun Project.extraString(name: String): String? =
    if (extensions.extraProperties.has(name)) {
        extensions.extraProperties.get(name)?.toString()
    } else {
        null
    }

fun Project.extraBoolean(name: String): Boolean? =
    if (extensions.extraProperties.has(name)) {
        extensions.extraProperties.get(name)?.toString()?.toBoolean()
    } else {
        null
    }

fun Project.composeSearchEngine(): String =
    extraString("composeSearchEngine")
        ?: findProperty("searchEngine")?.toString()
        ?: "es-9"

fun Project.composeSkipTestsWithoutDocker(): Boolean =
    extraBoolean("composeSkipTestsWithoutDocker") ?: false

fun Project.composeDisableWhenDockerMissing(): Boolean =
    extraBoolean("composeDisableWhenDockerMissing") ?: true

fun isSearchUp(): Boolean = kotlin.runCatching {
    URI("http://localhost:9999").toURL().openConnection().connect()
}.isSuccess

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

    plugins.withId("com.avast.gradle.docker-compose") {
        val dockerExecutablePath = findDockerExecutablePath()
        val dockerAvailable = dockerExecutablePath != null
        val searchEngine = composeSearchEngine()

        configure<ComposeExtension> {
            buildAdditionalArgs.set(listOf("--force-rm"))
            stopContainers.set(true)
            removeContainers.set(true)
            forceRecreate.set(true)
            val composeFile = "${rootProject.projectDir}/docker-compose-$searchEngine.yml"
            dockerComposeWorkingDirectory.set(rootProject.projectDir)
            useComposeFiles.set(listOf(composeFile))

            if (dockerExecutablePath != null) {
                dockerExecutable.set(dockerExecutablePath)
            }
        }

        tasks.matching { it.name == "composeUp" }.configureEach {
            usesService(composeUpLock)
            onlyIf { !isSearchUp() }
        }

        if (!dockerAvailable && composeDisableWhenDockerMissing()) {
            tasks.matching { it.name.startsWith("compose") }.configureEach {
                enabled = false
            }
        }

        tasks.matching { it.name == "jsNodeTest" }.configureEach {
            if (!isSearchUp()) {
                dependsOn("composeUp")
            }
        }

        tasks.withType<Test>().configureEach {
            if (!isSearchUp()) {
                if (dockerAvailable) {
                    dependsOn("composeUp")
                } else if (composeSkipTestsWithoutDocker()) {
                    logger.lifecycle(
                        "Skipping tests because Elasticsearch is not running " +
                            "and Docker is unavailable."
                    )
                    enabled = false
                }
            }
        }
    }

    if (name == "docs") {
        tasks.withType<Test>().configureEach {
            if (!isSearchUp()) {
                dependsOn(":search-client:composeUp")
            }
        }
    }

    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    afterEvaluate {
        val dokkaGeneratePublicationHtml = tasks.named<DokkaGeneratePublicationTask>("dokkaGeneratePublicationHtml")

        tasks.register<Jar>("dokkaJar") {
            from(dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
            dependsOn(dokkaGeneratePublicationHtml)
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
