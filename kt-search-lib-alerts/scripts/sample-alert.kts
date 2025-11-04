//#!/usr/bin/env kotlin
//
///**
// * Sample alerting script for kt-search.
// *
// * The dynamic version `+` pulls the latest published release. Pin it to an explicit version
// * if you need repeatable builds—check the kt-search releases on GitHub for the current version.
// *
// * Developing locally? First run `./gradlew :kt-search-lib-alerts:publishToMavenLocal`, then swap the
// * `@file:DependsOn` line for:
// *
// *     @file:Repository("file:///home/you/.m2/repository")
// *     @file:DependsOn("com.jillesvangurp:kt-search-lib-alerts:<local-version>")
// *
// * Replace the path and `<local-version>` with your actual Maven cache location and the version
// * number Gradle just published.
// *
// * New to Kotlin scripting? Install a recent Java and Kotlin toolchain. With SDKMAN!:
// *
// *     curl -s "https://get.sdkman.io" | bash
// *     sdk install java 21.0.4-tem
// *     sdk install kotlin
// *
// * After that, you can execute this file with `kotlin -script sample-alert.kts`.
// */
//@file:Repository("https://repo1.maven.org/maven2/")
//@file:Repository("https://maven.tryformation.com/releases")
//@file:DependsOn("com.jillesvangurp:kt-search-lib-alerts:+")
//
//import com.jillesvangurp.ktsearch.KtorRestClient
//import com.jillesvangurp.ktsearch.SearchClient
//import com.jillesvangurp.ktsearch.alert.core.AlertService
//import com.jillesvangurp.ktsearch.alert.notifications.ConsoleLevel
//import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
//import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
//import com.jillesvangurp.ktsearch.alert.rules.RuleNotificationInvocation
//import com.jillesvangurp.searchdsls.querydsl.match
//import kotlinx.coroutines.awaitCancellation
//import kotlinx.coroutines.runBlocking
//
//fun env(key: String, default: String): String = System.getenv(key) ?: default
//
//val elasticHost = env("ELASTIC_HOST", "localhost")
//val elasticPort = env("ELASTIC_PORT", "9200").toInt()
//val alertTarget = env("ALERT_TARGET", "logs-*")
//val environment = env("ENVIRONMENT", "prod")
//
//val client = SearchClient(
//    KtorRestClient(
//        host = elasticHost,
//        port = elasticPort,
//        logging = false
//    )
//)
//
//runBlocking {
//    val alerts = AlertService(client)
//
//    alerts.start {
//        notifications(
//            consoleNotification(
//                id = "console-alerts",
//                level = ConsoleLevel.INFO,
//                message = "[{{timestamp}}] {{ruleName}} matched {{matchCount}} documents in $environment"
//            )
//        )
//        defaultNotifications("console-alerts")
//
//        rule(
//            AlertRuleDefinition.newRule(
//                id = "error-alert",
//                name = "Error monitor",
//                cronExpression = "*/5 * * * *",
//                target = alertTarget,
//                notifications = RuleNotificationInvocation.many("console-alerts"),
//                startImmediately = true
//            ) {
//                match("level", "error")
//            }
//        )
//    }
//
//    println("Alert service running against $alertTarget on $elasticHost:$elasticPort. Press Ctrl+C to stop.")
//
//    try {
//        awaitCancellation()
//    } finally {
//        alerts.stop()
//        client.close()
//    }
//}
