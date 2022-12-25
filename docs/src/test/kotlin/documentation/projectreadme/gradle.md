## Gradle

Add the Jitpack repository:

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

And then add the latest version:

```kotlin
implementation("com.github.jillesvangurp.kt-search:search-client:1.99.18")
```

**Check the [releases](https://github.com/jillesvangurp/kt-search/releases) page** for the latest release tag.

The 1.99.x releases are intended as release candidates for an eventual 2.0 release. At this point the API is stable and the library is feature complete. A 2.0 release will happen very soon now.
