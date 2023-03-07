## Gradle

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

And then the dependency:

```kotlin
    // check the latest release tag for the current version
    implementation("com.jillesvangurp:search-client:2.0.0-RC-2")
```

**IMPORTANT** We've switched maven repositories a couple of times now. Recently we switched back from jitpack.io to using our own repository. Jitpack is just too flaky for us to depend on and somehow they keep on having regressions with kotlin multi-platform projects.

**This also means the groupId has changed**. It's now `com.jillesvangurp` instead of `com.github.jillesvangurp.kt-search`.

I of course would like to get this on maven central eventually. However, I've had a really hard time getting that working and am giving up on that for now. The issue seems to be that I always hit some weird and very unspecific error and their documentation + plugins just never seem to quite work as advertised. Multi platform, multi module, and kotlin scripting are three things that tend to make things complicated apparently. If anyone wants to support me with this, please reach out. Otherwise use our private repository for now.
