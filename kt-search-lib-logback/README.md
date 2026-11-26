# KT Search Logback Appender

Optional JVM module for bulk indexing Logback events to Elasticsearch and
OpenSearch using KT Search. It is built, tested, and released with KT Search.
Its version always matches the other KT Search modules; it requires Java 17
or newer. Setup and usage are documented here, separately from the main
KT Search manual.

## Migration from the standalone appender

Replace `com.jillesvangurp:kt-search-logback-appender` with
`com.jillesvangurp:kt-search-lib-logback`. Use the same KT Search release
version for this module and any explicit `search-client` or `search-dsls`
dependencies. Remove the old dependency, including transitive copies, so
both artifacts do not supply the same classes on the runtime classpath.

The package `com.jillesvangurp.ktsearchlogback`, appender class name, and
Logback XML settings are unchanged. Kotlin, serialization, Ktor, and
coroutines now use the shared KT Search build versions. Upgrade the modules
together to avoid mixing incompatible Kotlin APIs and runtime classes.

The appender uses the provided `LogMessage` as a model class. This is an opinionated representation of log messages as produced by Logback. It passes on MDC variable, allows you pass on Logback context variables, and represents exception stack traces in a way that makes searching on exception classes or messages really easy.

Features

- Can be easily configured to work with elastic cloud
- Supports OpenSearch 3 with ISM rollover and retention policies.
- Supports various ways to provide MDC and other context to along with the log messages. This makes it easy to create dashboards in e.g. Kibana and break things down by server, data center, instance type, and other meta data.

We've been using this appender for several years at [FORMATION](https://tryformation.com). We have a cheap Elastic Cloud logging cluster and our API server (Spring Boot) uses this plugin to send logging events there.

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
// Use the same release version as your other KT Search modules.
val ktSearchVersion = "x.y.z"
implementation(
    "com.jillesvangurp:kt-search-lib-logback:$ktSearchVersion"
)
```

## Usage

Before using the appender, create the data stream and templates once. The appender writes documents but does not provision cluster resources for you at runtime.

The helper script creates the data stream, component templates, index template, mappings, and optionally an Elasticsearch ILM or OpenSearch ISM policy:

From the `kt-search-lib-logback` directory:

```bash
./create-ds-and-template.sh \
  --es-url http://localhost:9200 \
  --data-stream-name applogs
```

The script detects Elasticsearch or OpenSearch from the cluster root response.
The same command works with OpenSearch 3, tested against 3.10.1. Legacy
OpenSearch versions are outside the appender's provisioning test coverage.
Install `curl` and `jq` before running the script. You can run it again to
update templates and policies without recreating the data stream.

Lifecycle management is enabled by default. Use `--configure-lifecycle false`
to skip policy setup. For a fresh stream, this leaves lifecycle management
disabled.
The older `--configure-ilm` flag remains an alias. The Kotlin
`SearchClient.manageDataStream` helper also detects the server; its existing
`configureIlm` argument controls lifecycle setup for both engines.

| Setting | Elasticsearch ILM | OpenSearch ISM |
| --- | --- | --- |
| Rollover | Primary shard size or index age | Primary shard size or index age |
| Warm state | Shrink and force merge | Force merge |
| Warm/delete age | Time since rollover | Time since rollover |
| Policy attachment | `index.lifecycle.name` | ISM template for new backing indexes |

ISM skips empty-index rollover, matching Elasticsearch's default behavior.

OpenSearch's ISM shrink action does not preserve data stream membership
([upstream issue](https://github.com/opensearch-project/index-management/issues/1201)).
The helper omits shrink on OpenSearch; `warmShrinkShards` and
`--warm-shrink-shards` apply only to Elasticsearch. Both engines retain all
backing indexes in the stream until their deletion policy runs.

ISM attaches policies asynchronously. The ISM template covers initial and
future backing indexes, including those created by rollover. For existing
backing indexes that are unmanaged or use another policy, attach or change
their policy explicitly with the ISM API. Updates to the same policy follow
ISM's normal update rules. Disabling lifecycle setup does not remove existing
ISM policy templates or detach existing ILM/ISM policies.

For OpenSearch, the provisioning identity needs template, data stream, and
ISM policy management permissions. The runtime appender only needs bulk
create access to the provisioned stream.

Run the appender tests locally with the current OpenSearch fixture:

```bash
./gradlew :kt-search-lib-logback:composeUp -PsearchEngine=os-3
./gradlew :kt-search-lib-logback:test -PsearchEngine=os-3
./gradlew :kt-search-lib-logback:composeDown -PsearchEngine=os-3
```

After provisioning, add the appender to your Logback configuration.

```xml
<?xml version="1.0"?>
<configuration debug="false">
    <contextName>test-loggers</contextName>

    <!-- will get added to the context and logged along with everything-->
    <variable scope="context" name="environment" value="tests" />
    <variable scope="context" name="host" value="${HOSTNAME}" />

    <contextListener class="ch.qos.logback.classic.jul.LevelChangePropagator">
        <resetJUL>true</resetJUL>
    </contextListener>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d %-5level %logger - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="ASYNC-STDOUT"
        class="ch.qos.logback.classic.AsyncAppender" queueSize="2048">
        <appender-ref ref="STDOUT"/>
    </appender>

    <appender name="es-out"
    class="com.jillesvangurp.ktsearchlogback.KtSearchLogBackAppender">
        <host>localhost</host>
        <port>9200</port>
        <dataStreamName>applogs</dataStreamName>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC-STDOUT"/>
        <appender-ref ref="es-out"/>
    </root>
</configuration>
```

For a remote or Elastic Cloud cluster, configure the connection explicitly:

```xml
<appender name="es-out"
    class="com.jillesvangurp.ktsearchlogback.KtSearchLogBackAppender">
    <host>your-deployment.es.region.provider.cloud.es.io</host>
    <port>443</port>
    <ssl>true</ssl>
    <userName>elastic</userName>
    <password>${ELASTIC_PASSWORD}</password>
    <dataStreamName>applogs</dataStreamName>
</appender>
```

## Settings

The plugin can be configured via the normal mechanisms provided by Logback.

This is the list of writable properties and their defaults. Mostly, the properties are pretty self explanatory. You can override any of these with the appropriate xml tag in your logback configuration.

```kotlin
// you can override all the public properties via the logback xml config

/** Enables detailed logging for debugging the appender. */
var verbose = false
/** Enables logging of Elasticsearch requests for diagnosis. */
var logElasticSearchCalls = false
var host: String = "localhost"
var port: Int = 9200
var userName: String? = null
var password: String? = null
var ssl: Boolean = false

/** maximum time to wait until flushing messages to Elasticsearch */
var flushSeconds: Int = 5
/** maximum bulk request page size before flushing. */
var bulkMaxPageSize: Int = 200
var queueCapacity: Int = 1000
var maxRetries: Int = 3
var retryDelayMillis: Long = 250
var requestTimeoutMillis: Long = 10000
var shutdownTimeoutMillis: Long = 10000

var dataStreamName = "applogs"
var contextVariableFilterRe = ""

/** Comma-separated MDC fields (without the prefix) to coerce to Long. */
var coerceMdcFieldsToLong = ""
var coerceMdcFieldsToDouble = ""
```

## Delivery and shutdown

Logging callers snapshot and enqueue events without waiting for the network.
One worker sends batches when they reach `bulkMaxPageSize` or the oldest
batched event reaches `flushSeconds`. Client initialization also runs on the
worker, so events can queue as soon as Logback starts the appender.

The queue holds up to `queueCapacity` events. Overflow drops the oldest
queued event, increments `statistics.droppedCount`, and reports loss through
Logback's status manager. Internal diagnostics bypass the appender; Ktor's
HTTP diagnostic logger is also excluded to prevent feedback.

Connection errors, request timeouts, and HTTP 408/429/500/502/503/504 are
retried up to `maxRetries` times after the initial attempt. Backoff starts at
`retryDelayMillis`, doubles on each retry, and is capped at five seconds.
Partial responses retry only transiently failed items. Permanent failures
and exhausted retries increment `statistics.failedCount`.

Retries keep the same document ID. A conflict after a retry is treated as
an acknowledgement of the earlier create. Delivery remains best effort:
the buffer is in memory, and an ambiguous request across a data stream
rollover can still produce duplicates. It is not an audit log or durable
message queue.

Stopping closes the queue, drains accepted events, and closes the client.
`shutdownTimeoutMillis` bounds the total wait, including requests and retries.
Events discarded or left unconfirmed at that deadline count as dropped.
Stopping twice is safe; restarting creates a new worker and keeps the
appender's cumulative counters.

Messages use Logback's formatted text and original event timestamp. MDC and
context values are copied before the logging call returns. Exception causes
retain their individual messages, including causes without a message.
Invalid numeric MDC values, including non-finite doubles, become JSON null.

## Verified HTTPS connections

Set `ssl=true` and use a dedicated service username and password. The JVM
client validates the server certificate chain and hostname using normal Java
TLS settings. For a private CA, supply a truststore before the JVM starts,
for example with `-Djavax.net.ssl.trustStore=/run/search/ca.p12` and
`-Djavax.net.ssl.trustStoreType=PKCS12`. Mount private files read-only and
supply credentials through your deployment secret mechanism. Configure trust
before Logback starts, including early application startup logging; do not
replace hostname verification or install a trust-all manager.

The runnable JVM CLI (`:ktsearch-cli:jvmFatJar`) uses the same truststore
settings for operational checks against HTTPS endpoints. This trust setup
does not change any logging cluster or its credentials automatically.

## Spring Boot and Prometheus metrics

Keep the appender in `logback.xml`; it starts before Spring and records
counters immediately. Spring only needs to bind the existing appender to
its metrics registry. With Actuator and Micrometer already configured, add:

```kotlin
import com.jillesvangurp.ktsearchlogback.KtSearchLogbackMetrics
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LoggingMetricsConfig {
    @Bean
    fun ktSearchLogbackMetrics() = KtSearchLogbackMetrics()
}
```

This fits the FORMATION API's existing Micrometer/Prometheus setup alongside
its `MetricsConfig`. No second appender or Spring-managed client is needed.
Use a release containing this binder before adding the bean to the API.

Micrometer is an optional compile-time dependency of this library. Spring
Boot supplies its managed version at runtime; applications that do not use
metrics do not need to add Micrometer. The binder compiles against 1.16.4,
the current API's baseline. For other applications, add `micrometer-core`
and call `KtSearchLogbackMetrics().bindTo(registry)` after Logback initializes.

| Meter | Kind | Meaning |
| --- | --- | --- |
| `ktsearch.logback.events.received` | Counter | Events offered, including dropped events |
| `ktsearch.logback.events.indexed` | Counter | Events acknowledged by the cluster |
| `ktsearch.logback.events.failed` | Counter | Permanent failures and exhausted retries |
| `ktsearch.logback.events.dropped` | Counter | Overflow and unconfirmed shutdown events |
| `ktsearch.logback.events.retried` | Counter | Event retry attempts |
| `ktsearch.logback.events.pending` | Gauge | Events queued or in flight |
| `ktsearch.logback.active` | Gauge | Indexing worker active: 1 or 0 |

All meters have an `appender` tag, for example `appender="es-out"`. Prometheus
exports counters with underscores and a `_total` suffix, such as
`ktsearch_logback_events_dropped_total`. Early startup events are included.
The binder finds appenders behind async wrappers and deduplicates references
from different loggers. It does not start or stop the appender. Recreate the
binder if you replace the logging context or its appender instances.

## Indexed Fields

The default index template created by the helper script maps these fields:

- `@timestamp`
- `message`
- `thread`
- `level`
- `logger`
- `contextName`
- `mdc.*`
- `context.*`
- `exceptionList.className`
- `exceptionList.message`

String fields inside `mdc` and `context` are mapped dynamically as keywords so they can be used directly in filters and aggregations.

## Elastic Cloud Privileges Needed

The appender only writes documents. If you create templates/policies/data streams outside the appender, grant those management permissions to that provisioning user or script.

For the runtime appender user, grant at least:

Cluster:
- monitor

Index:

Grant these permissions on the `applogs*` prefix

- create
- index
- create_doc
- create_index
- write
- view_index_metadata

## Development

Run from the KT Search repository root. Tests use the shared Elasticsearch
instance on port 9990:

```bash
./gradlew composeUp
./gradlew :kt-search-lib-logback:test
./gradlew composeDown
```

The root `check` task runs the appender tests. The root `publish` task
publishes `com.jillesvangurp:kt-search-lib-logback` with the shared
`-Pversion` value and a dependency on the matching search client. It uses
the same Maven repository as the other modules. No separate appender
release is needed.

API documentation is generated with:

```bash
./gradlew :kt-search-lib-logback:dokkaGeneratePublicationHtml
```
