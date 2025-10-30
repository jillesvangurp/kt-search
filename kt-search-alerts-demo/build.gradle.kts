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
    implementation("ch.qos.logback:logback-classic:_")
    implementation("org.slf4j:slf4j-api:_")
    implementation("org.slf4j:jcl-over-slf4j:_")
    implementation("org.slf4j:log4j-over-slf4j:_")
    implementation("org.slf4j:jul-to-slf4j:_")
    implementation("org.apache.logging.log4j:log4j-to-slf4j:_") // es seems to insist on log4j2
}

application {
    mainClass.set("com.jillesvangurp.ktsearch.alert.demo.MainKt")
}
