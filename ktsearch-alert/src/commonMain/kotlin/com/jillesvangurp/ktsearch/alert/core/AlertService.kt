package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.alert.notifications.NotificationContext
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDispatcher
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationRegistry
import com.jillesvangurp.ktsearch.alert.rules.AlertRule
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import com.jillesvangurp.ktsearch.alert.rules.CronSchedule
import com.jillesvangurp.ktsearch.alert.rules.RuleNotificationInvocation
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
import kotlin.random.Random
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

class AlertService(
    private val client: SearchClient,
    private val dispatcher: NotificationDispatcher,
    private val nowProvider: () -> Instant = { currentInstant() },
    dispatcherContext: CoroutineContext = Dispatchers.Default
) {
    private val startMutex = Mutex()
    private val scheduleMutex = Mutex()
    private val rulesMutex = Mutex()
    private val generatedIdsByName = mutableMapOf<String, String>()
    private val scheduledRules = mutableMapOf<String, ScheduledRule>()
    private val ruleStates = mutableMapOf<String, AlertRule>()
    private val coroutineContext: CoroutineContext = dispatcherContext
    private var supervisorJob: Job = SupervisorJob()
    private var scope: CoroutineScope = CoroutineScope(coroutineContext + supervisorJob)
    private var configuration: AlertConfiguration = AlertConfiguration(NotificationRegistry.empty(), emptyList())
    private var started = false

    suspend fun start(configuration: AlertConfiguration) {
        startMutex.withLock {
            if (started) {
                reload(configuration)
                return
            }
            this.configuration = configuration
            resetScope()
            refreshRules()
            started = true
        }
    }

    suspend fun reload(configuration: AlertConfiguration) {
        startMutex.withLock {
            this.configuration = configuration
            if (!started) return
            refreshRules()
        }
    }

    suspend fun start(block: AlertConfigurationBuilder.() -> Unit) {
        start(alertConfiguration(block))
    }

    suspend fun reload(block: AlertConfigurationBuilder.() -> Unit) {
        reload(alertConfiguration(block))
    }

    suspend fun stop() {
        startMutex.withLock {
            if (!started) return
            scheduleMutex.withLock {
                scheduledRules.values.forEach { it.cancel() }
                scheduledRules.clear()
            }
            rulesMutex.withLock {
                ruleStates.clear()
            }
            supervisorJob.cancelAndJoin()
            resetScope()
            started = false
        }
    }

    suspend fun refreshRules() {
        val config = configuration
        val definitions = config.rules
        val registry = config.notifications
        val now = nowProvider()
        scheduleMutex.withLock {
            val activeIds = mutableSetOf<String>()
            for (definition in definitions) {
                val id = resolveRuleId(definition)
                definition.notifications.forEach { invocation ->
                    registry.require(invocation.notificationId)
                }
                val existing = getRuleState(id)
                val materialized = materializeRule(id, definition, existing, now)
                storeRuleState(materialized)
                activeIds += id
                if (!materialized.enabled) {
                    scheduledRules.remove(id)?.cancel()
                    continue
                }
                val hash = materialized.executionHash()
                val existingSchedule = scheduledRules[id]
                if (existingSchedule == null || existingSchedule.hash != hash) {
                    existingSchedule?.cancel()
                    scheduledRules[id] = scheduleRule(materialized)
                } else {
                    existingSchedule.updateRule(materialized)
                }
            }
            val removed = scheduledRules.keys - activeIds
            removed.forEach { id ->
                scheduledRules.remove(id)?.cancel()
                removeRuleState(id)
            }
        }
    }

    fun currentRules(): List<AlertRule> =
        ruleStates.values.toList()

    fun currentConfiguration(): AlertConfiguration = configuration

    private fun scheduleRule(rule: AlertRule): ScheduledRule {
        val cron = CronSchedule.parse(rule.cronExpression)
        val initialNext = rule.nextRun ?: nowProvider()
        return ScheduledRule(rule.id, cron, rule.executionHash(), initialNext)
    }

    private fun resetScope() {
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(coroutineContext + supervisorJob)
    }

    private fun resolveRuleId(definition: AlertRuleDefinition): String {
        val explicit = definition.id
        if (!explicit.isNullOrBlank()) return explicit
        return generatedIdsByName.getOrPut(definition.name) { generateId() }
    }

    private suspend fun materializeRule(
        id: String,
        definition: AlertRuleDefinition,
        existing: AlertRule?,
        now: Instant
    ): AlertRule {
        val cron = CronSchedule.parse(definition.cronExpression)
        val nextRun = when {
            !definition.enabled -> null
            definition.startImmediately -> now
            existing?.nextRun != null -> existing.nextRun
            else -> cron.nextAfter(now)
        }
        return AlertRule(
            id = id,
            name = definition.name,
            enabled = definition.enabled,
            cronExpression = definition.cronExpression,
            target = definition.target,
            queryJson = definition.queryJson,
            notifications = definition.notifications,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastRun = existing?.lastRun,
            nextRun = nextRun,
            failureCount = existing?.failureCount ?: 0,
            lastFailureMessage = existing?.lastFailureMessage
        )
    }

    private suspend fun storeRuleState(rule: AlertRule) {
        rulesMutex.withLock {
            ruleStates[rule.id] = rule
        }
    }

    private suspend fun getRuleState(id: String): AlertRule? =
        rulesMutex.withLock { ruleStates[id] }

    private suspend fun updateRuleState(id: String, transform: (AlertRule) -> AlertRule) {
        rulesMutex.withLock {
            val current = ruleStates[id] ?: return
            ruleStates[id] = transform(current)
        }
    }

    private suspend fun removeRuleState(id: String) {
        rulesMutex.withLock {
            ruleStates.remove(id)
        }
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
            if (rule.nextRun != null && rule.nextRun != nextRun) {
                nextRun = rule.nextRun
            }
        }

        suspend fun cancel() {
            job.cancelAndJoin()
        }

        private suspend fun executeRule() {
            val latestRule = getRuleState(id) ?: return
            if (!latestRule.enabled) {
                nextRun = nowProvider()
                return
            }
            val triggeredAt = nowProvider()
            try {
                val matches = performSearch(latestRule)
                if (matches.isNotEmpty()) {
                    triggerNotifications(latestRule, matches, triggeredAt)
                }
                val next = cron.nextAfter(triggeredAt)
                nextRun = next
                updateRuleState(id) { current ->
                    current.copy(
                        lastRun = triggeredAt,
                        nextRun = next,
                        failureCount = 0,
                        lastFailureMessage = null,
                        updatedAt = triggeredAt
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val next = cron.nextAfter(triggeredAt)
                nextRun = next
                updateRuleState(id) { current ->
                    current.copy(
                        lastRun = triggeredAt,
                        nextRun = next,
                        failureCount = (current.failureCount + 1).coerceAtMost(Int.MAX_VALUE),
                        lastFailureMessage = t.message ?: t::class.simpleName,
                        updatedAt = triggeredAt
                    )
                }
                logger.warn(t) { "Alert rule ${latestRule.id} execution failed" }
            }
        }
    }

    private suspend fun triggerNotifications(rule: AlertRule, matches: List<JsonObject>, triggeredAt: Instant) {
        val registry = configuration.notifications
        val baseVariables = mutableMapOf(
            "ruleName" to rule.name,
            "ruleId" to rule.id,
            "matchCount" to matches.size.toString(),
            "timestamp" to triggeredAt.toString(),
            "target" to rule.target
        )
        val context = NotificationContext(
            ruleId = rule.id,
            ruleName = rule.name,
            triggeredAt = triggeredAt,
            matchCount = matches.size,
            matches = matches
        )
        for (invocation in rule.notifications) {
            val definition = registry.get(invocation.notificationId)
            if (definition == null) {
                logger.warn { "Skipping notification '${invocation.notificationId}' for rule ${rule.id} because it is not defined" }
                continue
            }
            val variables = mergedVariables(baseVariables, definition, invocation)
            dispatcher.dispatch(definition, variables, context)
        }
    }

    private fun mergedVariables(
        baseVariables: Map<String, String>,
        definition: NotificationDefinition,
        invocation: RuleNotificationInvocation
    ): Map<String, String> =
        buildMap {
            putAll(baseVariables)
            putAll(definition.defaultVariables)
            putAll(invocation.variables)
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

    companion object {
        private fun generateId(): String {
            val bytes = Random.nextBytes(16)
            return bytes.joinToString(separator = "") { byte ->
                ((byte.toInt() and 0xff) + 0x100).toString(16).substring(1)
            }
        }
    }
}
