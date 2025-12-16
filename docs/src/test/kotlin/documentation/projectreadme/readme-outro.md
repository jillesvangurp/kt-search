## Petstore Demo Project

![petstore demo](petstore-demo/petstore-demo.webp)

If you are looking for a quick way to get an impression of what a fully integrated search 
would look like with kt-search, a good starting point is the [petstore demo](petstore-demo) project included in this project.

## Setting up a development environment

Any recent version of Intellij should be able to import this project as is. 
This project uses docker for testing and to avoid having the tests create a 
mess in your existing elasticsearch cluster, it uses a different port than
the default Elasticsearch port.

If you want to save some time while developing, it helps to start docker manually. Otherwise you have to wait for the container to stop and start every time you run a test.

```bash
docker-compose -f docker-compose-es-8.yml up -d
```

For additional details, refer to the build file.

## Compatibility

The integration tests on GitHub Actions use a **matrix build** that tests everything against Elasticsearch 7.x, 8.x & 9.x and Opensearch 1.x, 2.x & 3.x.

It may work fine with earlier Elasticsearch versions as well. But we don't actively test this and the tests are known to not pass with Elasticsearch 6 due to some changes in the mapping dsl. You may be able to work around some of this, however.

There is an annotation that is used to restrict APIs when needed. E.g. `search-after` support was added in Opensearch 2.x but is missing in 1.x:

```kotlin
@VariantRestriction(SearchEngineVariant.ES7,SearchEngineVariant.ES8)
suspend fun SearchClient.searchAfter(target: String, keepAlive: Duration, query: SearchDSL): Pair<SearchResponse,Flow<SearchResponse.Hit>> {
    validateEngine("search_after does not work on OS1",
        SearchEngineVariant.ES7, 
        SearchEngineVariant.ES8,
        SearchEngineVariant.ES9,
        SearchEngineVariant.OS2,
        SearchEngineVariant.OS3,
        )

    // ...
}
```

The annotation is informational only for now. In our tests, we use `onlyon` to prevent tests from
failing on unsupported engines:

```kotlin
onlyOn("doesn't work on Opensearch",
    SearchEngineVariant.ES7,
    SearchEngineVariant.ES8,
    SearchEngineVariant.ES9,
)
```

## Kotlin & Multiplatform compatibility

New releases of this library generally update dependencies to their current versions. There currently is no LTS release of Kotlin. Generally this library should work with recent stable releases of Kotlin. At this point, that means Kotlin 2.0 or higher.

Also, because this is a multiplatform project, you should be aware that several of the platforms are experimental. Because of this, we try to track the latest stable releases of Kotlin. IOS, Linux, Wasm, etc. are expected to generally work but are not something that we actively use ourselves and something that at this point is not stable on the Kotlin side yet. Also, there are some issues with WASM and Karma not playing nice. I've disabled tests for that. The IOS simulator tests are disabled for the same reason.

If you try and find issues, use the issue tracker please.

## Module Overview

This repository contains several kotlin modules that each may be used independently.

| Module          | Description                                                                                                                                    |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `search-dsls`   | DSLs for search and mappings based on `json-dsl`.                                                                                              |
| `search-client` | Multiplatform REST client for Elasticsearch 7.x, 8.x & 9.x and Opensearch 1.x, 2.x & 3.x. This is what you would want to use in your projects. |
| `docs`          | Contains the code that generates the [manual](https://jillesvangurp.github.io/kt-search/manual/) and this readme..                             |
| `petstore-demo` | Demo Spring Boot application and kt-search. A practical example of how you can implement search, breakdowns, dashboards, ETL, etc. in your applications. |
| `kt-search-lib-alerts` | Experimental alerting core library built on top of kt-search. Not production ready yet.                                                         |
| `kt-search-alerts-demo` | JVM demo application that wires the alerting library into a runnable sample for local experimentation.                                            |

The `search-client` module is the main module of this library that users will want to use. The `search-dsls` module implements most of the DSLs needed for querying, mappings etc. It's what you could use to build your own search client if you can't or don't want to use the `search-client` module.

You can find the documentation h the `docs` module. Note, this uses my `kotlin4example` project to generate markdown documentation. Make sure you understand how this works if you want to make documentation pull requests. It's not that hard to figure out and, if I say so myself, quite nice to use for writing documentation.

The `kt-search-lib-alerts` and `kt-search-alerts-demo` might eventually move out of this project into a separate project. See the [README.md](kt-search-alerts-demo/README.md) and the demo module and it's documentation for more information on how to use that.

```kotlin
fun env(key: String) = System.getenv(key) ?: error("Missing $key")

val httpClient = HttpClient()
val service = AlertService(searchClient)
service.start {
    notifications(
        consoleNotification(
            id = "console-alerts",
            level = ConsoleLevel.INFO,
            message = "{{ruleName}} matched {{matchCount}} documents"
        ),
        slackNotification(
            id = "slack-alerts",
            sender = SlackWebhookSender(httpClient),
            webhookUrl = env("SLACK_WEBHOOK_URL"),
            message = "*{{ruleName}}* matched {{matchCount}} documents",
            channelName = "#ops-alerts",
            username = "Alertbot"
        ),
        emailNotification(
            id = "ops-email",
            sender = SendGridEmailSender(httpClient, SendGridConfig(apiKey = env("SENDGRID_API_KEY"))),
            from = "alerts@example.com",
            to = listOf("oncall@example.com"),
            subject = "{{ruleName}} fired",
            body = "Found {{matchCount}} matches at {{timestamp}}"
        ),
        smsNotification(
            id = "oncall-sms",
            sender = TwilioSmsSender(
                httpClient,
                TwilioConfig(
                    accountSid = env("TWILIO_ACCOUNT_SID"),
                    authToken = env("TWILIO_AUTH_TOKEN"),
                    defaultSenderId = env("TWILIO_FROM")
                )
            ),
            to = listOf(env("ONCALL_NUMBER")),
            senderId = env("TWILIO_FROM"),
            message = "{{ruleName}} matched {{matchCount}}"
        )
    )
    defaultNotifications("console-alerts", "slack-alerts", "ops-email")
    rule(
        AlertRuleDefinition.newRule(
            id = "error-alert",
            name = "Error monitor",
            cronExpression = "*/2 * * * *",
            target = "logs-*",
            notifications = emptyList()
        ) {
            // search-dsl powered query
            match("level", "error")
        }
    )
}
```

- Notifications are regular Kotlin functions; each definition carries the suspending block that delivers the alert, so there's no central dispatcher to configure.
- Slack notifications post to an [incoming webhook URL](https://api.slack.com/messaging/webhooks) and can override the channel/username per message.
- The Twilio sender expects the account SID, auth token, and a default `From` number; each notification can override the sender ID or list multiple recipients.
- Custom notification channels are one `notification(id) { … }` call away—just provide a suspending block that delivers the alert.

With this approach, alerts are versioned alongside your application code, and secrets such as API keys or webhook URLs are supplied at runtime (typically via environment variables).

## Contributing

Pull requests are very welcome! This project runs on community contributions. If you don't like something, suggest changes. Is a feature you need missing from the DSL? Add it. To avoid conflicts or double work, please reach out via the issue tracker for bigger things. I try to be as responsive as I can

Some suggestions of things you could work on:

- Extend the mapping or query DSLs. Our goal is to have coverage of all the common things we and other users need. The extensibility of `JsonDsl` always gives you the option to add whatever is not directly supported by manipulating the underlying map. But creating extension functions that do this properly is not har.
- Add more API support for things in Opensearch/Elasticsearch that are not currently supported. The REST api has dozens of end point other than search. Like the DSL, adding extension functions is easy and using the underlying rest client allows you to customize any requests.
- Work on one of the issues or suggest some new ones.
- Refine the documentation. Add examples. Document missing things.

## Support and Community

Please file issues if you find any or have any suggestions for changes.

Within reason, I can help with simple issues. Beyond that, I offer my services as a consultant as well if you need some more help with getting started or just using Elasticsearch/Opensearch in general with just about any tech stack. I can help with discovery projects, training, architecture analysis, query and mapping optimizations, or just generally help you get the most out of your Elasticsearch/Opensearch setup and your product roadmap.

The best way to reach me is via email if you wish to use my services professionally. Please refer to my [website](https://www.jillesvangurp.com) for that.

I also try to respond quickly to issues. And I also lurk in the amazing [Kotlin](https://kotlinlang.org/community/), [Elastic](https://www.elastic.co/blog/join-our-elastic-stack-workspace-on-slack), and [Search Relevancy](https://opensourceconnections.com/blog/2021/07/06/building-the-search-community-with-relevance-slack/) Slack communities. 

## About this README

This readme is generated using my [kotlin4example](https://github.com/jillesvangurp/kotlin4example) library. I started developing that a few years ago when 
I realized that I was going to have to write a lot of documentation with code examples for kt-search. By now,
both the manual and this readme heavily depend on this and it makes maintaining and adding documentation super easy. 

The way it works is that it provides a dsl for writing markdown that you use to write documentation. It allows you to include runnable code blocks and when it builds the documentation it figures out how to extract those from the kotlin source files and adds them as markdown code snippets. It can also intercept printed output and the return values of the blocks.

If you have projects of your own that need documentation, you might get some value out of using this as well. 
