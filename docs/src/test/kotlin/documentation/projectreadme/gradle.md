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

And then the dependency to commonsMain or main:

```kotlin
    // check the latest release tag for the latest version
    implementation("com.jillesvangurp:search-client:2.x.y")
```

**About maven central ...** I've switched maven repositories a couple of times now. Jitpack and multiplatform just doesn't work. Of course I would have liked to get this on maven central. However, after repeated attempts to get that done, I've decided to not sacrifice more time on this. The (lack of) documentation, the Jira bureaucracy, the uninformative errors, the gradle plugin, etc.  just doesn't add up to something that works for a multi module, multi platform project. I'm sure it can be done but I'm not taking more time out my schedule to find out.

If somebody decides to fix a proper, modern solution for hosting packages, I'll consider using it but I'm done with maven central for now. Google buckets work fine for hosting. So does ssh or any old web server. So does aws. It's just maven central that's a huge PITA. 