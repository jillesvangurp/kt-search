## Gradle

Add the [tryformation](https://tryformation.com) maven repository:

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

Then add the latest version:

```kotlin
implementation("com.jillesvangurp:search-client:1.99.x")
```

Check the [releases](https://github.com/jillesvangurp/kt-search/releases) page for the latest release tag.

Note, we may at some point try to push this to maven-central. For now, please use the maven repository above. All the pre-releases will have the `1.99.x` prefix. Despite this, the project can at this point be considered stable, feature complete, and usable. I'm holding off on labeling this as a 2.0 until I've had a chance to use it in anger on my own projects. Until then, some API refinement may happen once in a while. I will try to minimize breakage between releases.