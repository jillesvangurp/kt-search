package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.*
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class ProvisioningTest {
    @Test
    fun kotlinProvisioningSurvivesRollover() = runBlocking {
        withStream { client, name ->
            repeat(2) { provision(client, name, true) }
            verifyLifecycle(client, name)
            verifyRollover(client, name)
        }
    }

    @Test
    fun shellProvisioningSurvivesRerunsAndRollover() = runBlocking {
        withStream { client, name ->
            repeat(2) { script(name, true) }
            verifyLifecycle(client, name)
            verifyRollover(client, name)
        }
    }

    @Test
    fun lifecycleCanBeDisabledInBothProvisioners() = runBlocking {
        for (shell in listOf(false, true)) {
            withStream { client, name ->
                if (shell) script(name, false) else provision(client, name, false)
                client.dataStreamExists(name) shouldBe true
                val settings = client.restClient.get {
                    path(name, "_settings")
                }.parseJsonObject().toString()
                settings shouldNotContain "lifecycle"
                settings shouldNotContain "policy_id"
                val policyPath = if (isOpenSearch(client)) {
                    listOf("_plugins", "_ism", "policies", "$name-ism-policy")
                } else listOf("_ilm", "policy", "$name-ilm-policy")
                val failure = client.restClient.get {
                    path(*policyPath.toTypedArray())
                }.exceptionOrNull() as RestException
                failure.status shouldBe 404
            }
        }
    }

    @Test
    fun regularIndexIsNotADataStream(): Unit = runBlocking {
        val client = SearchClient(KtorRestClient(port = 9990))
        val name = "logback-index-${UUID.randomUUID()}"
        try {
            client.restClient.put { path(name); rawBody("{}") }.getOrThrow()
            client.dataStreamExists(name) shouldBe false
        } finally {
            client.restClient.delete { path(name) }.getOrThrow()
            client.close()
        }
    }

    private suspend fun provision(client: SearchClient, name: String, lifecycle: Boolean) {
        client.manageDataStream(
            name, 1, "1d", 0, 1, 1.days, 7.days, 1, 1, lifecycle
        ) shouldBe true
    }

    private fun script(name: String, lifecycle: Boolean) {
        val script = File("create-ds-and-template.sh").absoluteFile
        val output = File.createTempFile("logback-provisioning-", ".log")
        try {
            val process = ProcessBuilder(
                "bash", script.path, "--es-url", "http://localhost:9990",
                "--data-stream-name", name,
                "--configure-lifecycle", lifecycle.toString(),
                "--hot-roll-over-gb", "1", "--delete-min-age-days", "7"
            ).redirectErrorStream(true).redirectOutput(output).start()
            val exit = process.waitFor()
            check(exit == 0) { output.readText() }
        } finally {
            output.delete()
        }
    }

    private suspend fun isOpenSearch(client: SearchClient) =
        client.engineInfo().version.distribution == "opensearch"

    private suspend fun verifyLifecycle(client: SearchClient, name: String) {
        if (isOpenSearch(client)) {
            val response = client.restClient.get {
                path("_plugins", "_ism", "policies", "$name-ism-policy")
            }.parseJsonObject()
            val states = response.getValue("policy").jsonObject
                .getValue("states").jsonArray
            val rollover = states[0].jsonObject.getValue("actions").jsonArray[0]
                .jsonObject.getValue("rollover").jsonObject
            rollover.getValue("min_primary_shard_size").jsonPrimitive.content shouldBe "1gb"
            rollover.getValue("min_index_age").jsonPrimitive.content shouldBe "1d"
            rollover.getValue("prevent_empty_rollover").jsonPrimitive.boolean shouldBe true
            states.toString() shouldNotContain "shrink"
            for ((state, days) in listOf(0 to 1, 1 to 7)) {
                val age = states[state].jsonObject.getValue("transitions")
                    .jsonArray[0].jsonObject.getValue("conditions").jsonObject
                    .getValue("min_rollover_age").jsonPrimitive.content
                // Kotlin uses milliseconds and the shell uses days.
                age shouldBe if (age.endsWith("ms")) {
                    "${days.days.inWholeMilliseconds}ms"
                } else "${days}d"
            }
            client.restClient.get { path(name, "_settings") }
                .parseJsonObject().toString() shouldNotContain "lifecycle"
        } else {
            client.getIlmPolicy("$name-ilm-policy")
        }
    }

    private suspend fun verifyRollover(client: SearchClient, name: String) {
        suspend fun write(id: String) {
            client.restClient.post {
                path("_bulk")
                rawBody(
                    """{"create":{"_index":"$name","_id":"$id"}}""" + "\n" +
                        """{"@timestamp":"2026-11-23T00:00:00Z","message":"$id"}""" + "\n",
                    "application/x-ndjson"
                )
                parameter("refresh", true)
            }.parseJsonObject().getValue("errors").jsonPrimitive.boolean shouldBe false
        }
        write("before")
        val rollover = client.restClient.post {
            path(name, "_rollover")
            rawBody("{}")
        }.parseJsonObject()
        rollover.getValue("rolled_over").jsonPrimitive.boolean shouldBe true
        write("after")
        client.search(name).total shouldBe 2
        if (isOpenSearch(client)) {
            for (key in listOf("old_index", "new_index")) {
                val index = rollover.getValue(key).jsonPrimitive.content
                eventually(120.seconds) {
                    val explain = client.restClient.get {
                        path("_plugins", "_ism", "explain", index)
                    }.parseJsonObject().getValue(index).jsonObject
                    explain["policy_id"]?.jsonPrimitive?.content shouldBe "$name-ism-policy"
                }
            }
        }
    }

    private suspend fun withStream(block: suspend (SearchClient, String) -> Unit) {
        val client = SearchClient(KtorRestClient(port = 9990))
        val name = "logback-${UUID.randomUUID()}"
        try {
            block(client, name)
        } finally {
            client.restClient.delete { path("_data_stream", name) }
            client.deleteIndexTemplate("$name-template")
            client.deleteComponentTemplate("$name-template-settings")
            client.deleteComponentTemplate("$name-template-mappings")
            if (isOpenSearch(client)) {
                client.restClient.delete {
                    path("_plugins", "_ism", "policies", "$name-ism-policy")
                }
            } else {
                client.restClient.delete { path("_ilm", "policy", "$name-ilm-policy") }
            }
            client.close()
        }
    }
}
