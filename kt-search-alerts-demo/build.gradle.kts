plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":kt-search-lib-alerts"))
    implementation(KotlinX.coroutines.core)
}

application {
    mainClass.set("com.jillesvangurp.ktsearch.alert.demo.MainKt")
}
