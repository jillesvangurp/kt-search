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
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

nexusPublishing {
    repositories {
//        sonatype {  //only for users registered in Sonatype after 24 Feb 2021
//            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
//            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
//        }
    }
}