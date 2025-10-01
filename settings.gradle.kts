
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.60.6"
}

refreshVersions {
}

include("search-dsls")
include("search-client")
include("docs")
include("ktsearch-alert")
rootProject.name = "kt-search"
