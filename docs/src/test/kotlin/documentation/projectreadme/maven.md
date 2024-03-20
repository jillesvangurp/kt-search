## Maven

If you have maven based kotlin project targeting jvm and can't use kotlin multiplatform dependency, you would need to add jvm targeting artifacts.  

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

And then add dependencies to jvm targets:

```xml
<dependencies>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>search-client-jvm</artifactId>
        <version>2.1.25</version>
    </dependency>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>search-dsls-jvm</artifactId>
        <version>2.1.25</version>
    </dependency>
    <dependency>
        <groupId>com.jillesvangurp</groupId>
        <artifactId>json-dsl-jvm</artifactId>
        <version>3.0.0</version>
    </dependency>
</dependencies>
```
**Note:** The `json-dsl` is moved to separate repository. To find the latest version number, check releases: https://github.com/jillesvangurp/json-dsl/releases
