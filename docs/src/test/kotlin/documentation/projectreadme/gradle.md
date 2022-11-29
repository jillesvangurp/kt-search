## Gradle

Add the [tryformation](https://tryformation.com) maven repository:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.jillesvangurp.kt-search")
        }
    }
}
```

Then add the latest version:

```kotlin
implementation("com.github.jillesvangurp.kt-search:search-client:1.99.14")
```

**Check the [releases](https://github.com/jillesvangurp/kt-search/releases) page** for the latest release tag.

Note, we may at some point try to push this to maven-central. For now, please use Jitpack. All the pre-releases will have the `1.99.x` prefix. Despite this, the project can at this point be considered stable, feature complete, and usable. I'm holding off on labeling this as a 2.0 until I've had a chance to use it in anger on my own projects. Until then, some API refinement may happen once in a while. I will try to minimize breakage between releases.