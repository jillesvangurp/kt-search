package com.jillesvangurp.ktsearch.alert

import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.search
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import kotlinx.serialization.json.JsonObject

class KtsearchAlert(
    private val client: SearchClient,
    private val sendPlugin: AlertSendPlugin,
    private val indexWriteAlias: String,
    private val indexReadAlias: String = indexWriteAlias,
    private val nowProvider: () -> Instant = { currentInstant() },
    dispatcher: CoroutineContext = Dispatchers.Default
) {
    private val logger = KotlinLogging.logger {}
    private val coroutineContext: CoroutineContext = dispatcher
    private var supervisorJob: Job = SupervisorJob()
    private var scope: CoroutineScope = CoroutineScope(coroutineContext + supervisorJob)
    private val repository = AlertRuleRepository.create(client, indexWriteAlias, indexReadAlias, nowProvider)
    private val scheduledRules = mutableMapOf<String, ScheduledRule>()
    private val scheduleMutex = Mutex()
    private val startMutex = Mutex()
    private var started = false

    suspend fun start() {
        startMutex.withLock {
            if (started) return
            resetScope()
            repository.ensureIndex()
            refreshRules()
            started = true
        }
    }

    suspend fun stop() {
        startMutex.withLock {
            if (!started) return
            scheduleMutex.withLock {
                scheduledRules.values.forEach { it.cancel() }
                scheduledRules.clear()
            }
            supervisorJob.cancelAndJoin()
            resetScope()
            started = false
        }
    }

    suspend fun refreshRules() {
        repository.ensureIndex()
        val rules = repository.list()
        scheduleMutex.withLock {
            val activeIds = mutableSetOf<String>()
            for (rule in rules) {
                activeIds += rule.id
                val existing = scheduledRules[rule.id]
                if (!rule.enabled) {
                    existing?.cancel()
                    scheduledRules.remove(rule.id)
                    if (rule.nextRun != null) {
                        repository.pause(rule.id)
                    }
                    continue
                }
                val hash = rule.executionHash()
                if (existing == null || existing.hash != hash) {
                    existing?.cancel()
                    scheduledRules[rule.id] = scheduleRule(rule)
                } else {
                    existing.updateRule(rule)
                }
            }
            val removed = scheduledRules.keys - activeIds
            removed.forEach { id ->
                scheduledRules.remove(id)?.cancel()
            }
        }
    }

    suspend fun upsertRules(block: AlertRulesDsl.() -> Unit): List<AlertRule> {
        val definitions = alertRules(block)
        return definitions.map { repository.upsert(it) }.also {
            refreshRules()
        }
    }

    suspend fun deleteRule(id: String) {
        repository.delete(id)
        scheduleMutex.withLock {
            scheduledRules.remove(id)?.cancel()
        }
    }

    suspend fun listRules(): List<AlertRule> = repository.list()

    private fun scheduleRule(rule: AlertRule): ScheduledRule {
        val cron = CronSchedule.parse(rule.cronExpression)
        val initialNext = rule.nextRun ?: nowProvider()
        return ScheduledRule(rule.id, cron, rule.executionHash(), initialNext)
    }

    private inner class ScheduledRule(
        val id: String,
        val cron: CronSchedule,
        var hash: Int,
        initialNext: Instant?
    ) {
        private var nextRun: Instant = initialNext ?: cron.nextAfter(nowProvider())
        private val job: Job = scope.launch {
            while (isActive) {
                val now = nowProvider()
                val delayDuration = nextRun - now
                if (delayDuration.isPositive()) {
                    delay(delayDuration)
                    continue
                }
                executeRule()
            }
        }

        fun updateRule(rule: AlertRule) {
            hash = rule.executionHash()
            if (rule.nextRun != null && rule.nextRun!! != nextRun) {
                nextRun = rule.nextRun
            }
        }

        suspend fun cancel() {
            job.cancelAndJoin()
        }

        private suspend fun executeRule() {
            val latestRule = repository.get(id) ?: return
            if (!latestRule.enabled) {
                repository.pause(id)
                return
            }
            val triggeredAt = nowProvider()
            try {
                val matches = performSearch(latestRule)
                if (matches.isNotEmpty()) {
                    val rendered = latestRule.emailTemplate.render(latestRule, matches, triggeredAt)
                    val context = AlertSendContext(latestRule, matches, triggeredAt)
                    retrySuspend("send-alert-${latestRule.id}", 3, 2.seconds) {
                        sendPlugin.send(rendered, context)
                    }
                }
                val next = cron.nextAfter(triggeredAt)
                nextRun = next
                repository.markExecutionSuccess(id, triggeredAt, next)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val next = cron.nextAfter(triggeredAt)
                nextRun = next
                repository.markExecutionFailure(id, triggeredAt, next, t)
                logger.warn(t) { "Alert rule ${latestRule.id} execution failed" }
            }
        }
    }

    private suspend fun performSearch(rule: AlertRule): List<JsonObject> {
        val response = client.search(
            target = rule.target,
            rawJson = rule.queryJson,
            retries = 3,
            retryDelay = 2.seconds
        )
        return response.hits?.hits?.mapNotNull { it.source } ?: emptyList()
    }

    private fun resetScope() {
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(coroutineContext + supervisorJob)
    }
}
