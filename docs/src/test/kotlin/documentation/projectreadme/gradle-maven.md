## Gradle

Kt-search is published to the FORMATION maven repository. 

Add the `maven.tryformation.com` repository:

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.tryformation.com/releases") {
        content {
            includeGroup("com.jillesvangurp")
        }
    }
}
```
And then add the dependency like this:

```kotlin
    // check the latest release tag for the latest version
    implementation("com.jillesvangurp:search-client:2.x.y")
```
Note, several of the search-client dependencies for ktor client are marked as implementation. This means you have to explicitly add those on your side. This is intentional as some people may want to use their own rest client with the kt-search search client.

If you use the KtorRestClient that comes with kt-search you need to add the relevant ktor dependencies for the latest ktor client 3.x:

```kotlin
implementation("io.ktor:ktor-client-core:3.x.y")
implementation("io.ktor:ktor-client-auth:3.x.y")
implementation("io.ktor:ktor-client-logging:3.x.y")
implementation("io.ktor:ktor-client-serialization:3.x.y")
implementation("io.ktor:ktor-client-json:3.x.y")
```

## Maven

If you have maven based kotlin project targeting jvm and can't use kotlin multiplatform dependency, you will need to **append '-jvm' to the artifacts**.

Add the `maven.tryformation.com` repository:

```xml
<repositories>
    <repository>
        <id>try-formation</id>
        <name>kt search repository</name>
        <url>https://maven.tryformation.com/releases</url>
    </repository>
</repositories>
```

And then add dependencies for jvm targets:

```xml
<dependencies>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>search-client-jvm</artifactId>
        <version>2.x.y</version>
    </dependency>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>search-dsls-jvm</artifactId>
        <version>2.x.y</version>
    </dependency>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>json-dsl-jvm</artifactId>
        <version>3.x.y</version>
    </dependency>
</dependencies>
```
**Note:** The `json-dsl` is moved to separate repository. To find the latest version number, check releases: https://github.com/jillesvangurp/json-dsl/releases
