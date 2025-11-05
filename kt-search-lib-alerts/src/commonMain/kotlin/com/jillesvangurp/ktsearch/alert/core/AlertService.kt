package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.alert.notifications.NotificationContext
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationRegistry
import com.jillesvangurp.ktsearch.alert.notifications.NotificationVariable
import com.jillesvangurp.ktsearch.alert.notifications.putVariable
import com.jillesvangurp.ktsearch.alert.notifications.putVariableIfNotNull
import com.jillesvangurp.ktsearch.alert.rules.AlertRule
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import com.jillesvangurp.ktsearch.alert.rules.CronSchedule
import com.jillesvangurp.ktsearch.alert.rules.RuleAlertStatus
import com.jillesvangurp.ktsearch.alert.rules.RuleCheck
import com.jillesvangurp.ktsearch.alert.rules.RuleFiringCondition
import com.jillesvangurp.ktsearch.alert.rules.RuleNotificationInvocation
import com.jillesvangurp.ktsearch.clusterHealth
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.serializationext.DEFAULT_PRETTY_JSON
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.CoroutineContext
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val logger = KotlinLogging.logger {}

class AlertService(
    private val client: SearchClient,
    private val nowProvider: () -> Instant = { Clock.System.now() },
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
    private var configuration: AlertConfiguration = AlertConfiguration(
        notifications = NotificationRegistry.empty(),
        rules = emptyList(),
        defaultNotificationIds = emptyList(),
        notificationDefaults = NotificationDefaults.DEFAULT
    )
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
        logger.info { "Refreshing rules" }
        val config = configuration
        val definitions = config.rules
        val registry = config.notifications
        val now = nowProvider()
        val failureActions = mutableListOf<suspend () -> Unit>()
        scheduleMutex.withLock {
            val activeIds = mutableSetOf<String>()
            for (definition in definitions) {
                logger.debug { "Refreshing ${definition.id}" }
                val id = resolveRuleId(definition)
                val resolvedNotifications = resolveNotifications(definition)
                val resolvedFailureNotifications = definition.failureNotifications
                try {
                    validateNotifications(registry, definition.name, resolvedNotifications)
                    validateNotifications(registry, definition.name, resolvedFailureNotifications)
                    val existing = getRuleState(id)
                    val materialized = materializeRule(
                        id = id,
                        definition = definition,
                        existing = existing,
                        now = now,
                        notifications = resolvedNotifications,
                        failureNotifications = resolvedFailureNotifications
                    )
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
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    logger.error(t) { "Failed to configure alert rule ${definition.name} ($id)" }
                    scheduledRules.remove(id)?.cancel()
                    val failureTime = nowProvider()
                    updateRuleState(id) { current ->
                        current.copy(
                            updatedAt = failureTime,
                            failureCount = (current.failureCount + 1).coerceAtMost(Int.MAX_VALUE),
                            lastFailureMessage = t.message ?: t::class.simpleName,
                            nextRun = null
                        )
                    }
                    failureActions += suspend {
                        notifyRuleFailure(
                            ruleId = id,
                            ruleName = definition.name,
                            target = definition.target,
                            notifications = resolvedFailureNotifications,
                            fallbackNotifications = resolvedNotifications,
                            error = t,
                            triggeredAt = failureTime,
                            phase = FailurePhase.CONFIGURATION,
                            ruleMessage = definition.message,
                            failureMessage = definition.failureMessage
                        )
                    }
                }
            }
            val removed = scheduledRules.keys - activeIds
            removed.forEach { id ->
                logger.debug { "Removing rule $id" }
                scheduledRules.remove(id)?.cancel()
                removeRuleState(id)
            }
        }
        for (action in failureActions) {
            runCatching { action() }.onFailure { failure ->
                logger.error(failure) { "Failed to send configuration failure notification" }
            }
        }
    }

    fun currentRules(): List<AlertRule> =
        ruleStates.values.toList()

    fun currentConfiguration(): AlertConfiguration = configuration

    private fun validateNotifications(
        registry: NotificationRegistry,
        ruleName: String,
        invocations: List<RuleNotificationInvocation>
    ) {
        invocations.forEach { invocation ->
            runCatching { registry.require(invocation.notificationId) }
                .onFailure { throw IllegalArgumentException("Notification '${invocation.notificationId}' referenced by rule '$ruleName' is not defined", it) }
        }
    }

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

    private fun resolveNotifications(definition: AlertRuleDefinition): List<RuleNotificationInvocation> {
        if (definition.notifications.isNotEmpty()) {
            return definition.notifications
        }
        val defaults = configuration.defaultNotificationIds
        require(defaults.isNotEmpty()) {
            "No notifications configured for rule '${definition.name}' and no default notifications provided"
        }
        return defaults.map { RuleNotificationInvocation.create(it) }
    }

    private fun materializeRule(
        id: String,
        definition: AlertRuleDefinition,
        existing: AlertRule?,
        now: Instant,
        notifications: List<RuleNotificationInvocation>,
        failureNotifications: List<RuleNotificationInvocation>
    ): AlertRule {
        val cron = CronSchedule.parse(definition.cronExpression)
        val repeatIntervalMillis = definition.repeatNotificationIntervalMillis
            ?: configuration.notificationDefaults.repeatNotificationsEvery.inWholeMilliseconds
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
            message = definition.message,
            failureMessage = definition.failureMessage,
            notifications = notifications,
            failureNotifications = failureNotifications,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastRun = existing?.lastRun,
            nextRun = nextRun,
            failureCount = existing?.failureCount ?: 0,
            lastFailureMessage = existing?.lastFailureMessage,
            repeatNotificationIntervalMillis = repeatIntervalMillis,
            firingCondition = definition.firingCondition,
            check = definition.check,
            alertStatus = existing?.alertStatus ?: RuleAlertStatus.UNKNOWN,
            lastNotificationAt = existing?.lastNotificationAt
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
                val evaluation = evaluateRule(latestRule)
                val next = cron.nextAfter(triggeredAt)
                nextRun = next
                if (evaluation.triggered) {
                    val shouldNotify = shouldDispatch(latestRule, triggeredAt)
                    if (shouldNotify) {
                        triggerNotifications(latestRule, evaluation, triggeredAt)
                    }
                    updateRuleState(id) { current ->
                        current.copy(
                            lastRun = triggeredAt,
                            nextRun = next,
                            failureCount = 0,
                            lastFailureMessage = null,
                            updatedAt = triggeredAt,
                            alertStatus = RuleAlertStatus.ALERTING,
                            lastNotificationAt = when {
                                shouldNotify -> triggeredAt
                                else -> current.lastNotificationAt
                            }
                        )
                    }
                } else {
                    updateRuleState(id) { current ->
                        current.copy(
                            lastRun = triggeredAt,
                            nextRun = next,
                            failureCount = 0,
                            lastFailureMessage = null,
                            updatedAt = triggeredAt,
                            alertStatus = RuleAlertStatus.CLEAR,
                            lastNotificationAt = null
                        )
                    }
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
                val updated = getRuleState(id) ?: latestRule
                logger.warn(t) { "Alert rule ${latestRule.id} execution failed" }
                notifyRuleFailure(
                    ruleId = updated.id,
                    ruleName = updated.name,
                    target = updated.target,
                    notifications = updated.failureNotifications,
                    fallbackNotifications = updated.notifications,
                    error = t,
                    triggeredAt = triggeredAt,
                    phase = FailurePhase.EXECUTION,
                    failureCount = updated.failureCount,
                    ruleMessage = updated.message,
                    failureMessage = updated.failureMessage
                )
            }
        }
    }

    private fun shouldDispatch(rule: AlertRule, triggeredAt: Instant): Boolean =
        when (rule.alertStatus) {
            RuleAlertStatus.UNKNOWN,
            RuleAlertStatus.CLEAR -> true
            RuleAlertStatus.ALERTING -> {
                val intervalMillis = rule.repeatNotificationIntervalMillis ?: return false
                val lastNotified = rule.lastNotificationAt ?: return true
                val elapsed = triggeredAt - lastNotified
                elapsed >= intervalMillis.milliseconds
            }
        }

    private suspend fun triggerNotifications(rule: AlertRule, evaluation: RuleEvaluation, triggeredAt: Instant) {
        val registry = configuration.notifications
        val matches = evaluation.matches
        val matchCount = evaluation.matchCount
        val matchesJson = serializeMatches(matches)
        val baseVariables = baseVariables(
            ruleId = rule.id,
            ruleName = rule.name,
            target = rule.target,
            triggeredAt = triggeredAt,
            matchCount = matchCount,
            status = RuleRunStatus.SUCCESS,
            failureCount = null,
            error = null,
            phase = null,
            ruleMessage = rule.message,
            failureMessage = rule.failureMessage,
            matchesJson = matchesJson
        )
        val context = NotificationContext(
            ruleId = rule.id,
            ruleName = rule.name,
            triggeredAt = triggeredAt,
            matchCount = matchCount,
            matches = matches
        )
        val failures = mutableListOf<Throwable>()
        for (invocation in rule.notifications) {
            val definition = registry.get(invocation.notificationId)
            if (definition == null) {
                logger.warn { "Skipping notification '${invocation.notificationId}' for rule ${rule.id} because it is not defined" }
                continue
            }
            val variables = mergedVariables(baseVariables, definition, invocation)
            val result = runCatching { definition.dispatch(variables, context) }
            result.onFailure { failure ->
                failures += failure
                logger.error(failure) { "Notification '${invocation.notificationId}' failed for rule ${rule.id}" }
            }
        }
        if (failures.isNotEmpty()) {
            throw NotificationDeliveryException(rule.id, failures)
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

    private suspend fun evaluateRule(rule: AlertRule): RuleEvaluation =
        when (val check = rule.check) {
            is RuleCheck.Search -> evaluateSearchRule(check, rule.firingCondition)
            is RuleCheck.ClusterStatusCheck -> evaluateClusterStatusRule(check)
        }

    private suspend fun evaluateSearchRule(
        check: RuleCheck.Search,
        firingCondition: RuleFiringCondition
    ): RuleEvaluation {
        val response = client.search(
            target = check.target,
            rawJson = check.queryJson,
            retries = 3,
            retryDelay = 2.seconds
        )
        val matches = response.hits?.hits?.mapNotNull { it.source } ?: emptyList()
        val triggered = firingCondition.shouldTrigger(matches.size)
        val matchesForNotification = if (triggered) matches else emptyList()
        return RuleEvaluation(
            triggered = triggered,
            matches = matchesForNotification,
            matchCount = matchesForNotification.size
        )
    }

    private suspend fun evaluateClusterStatusRule(
        check: RuleCheck.ClusterStatusCheck
    ): RuleEvaluation {
        val health = client.clusterHealth()
        val triggered = health.status != check.expectedStatus
        val payload = buildJsonObject {
            put("expectedStatus", JsonPrimitive(check.expectedStatus.name.lowercase()))
            put("actualStatus", JsonPrimitive(health.status.name.lowercase()))
            put("clusterName", JsonPrimitive(health.clusterName))
            put("timedOut", JsonPrimitive(health.timedOut))
        }
        val matchesForNotification = if (triggered) listOf(payload) else emptyList()
        return RuleEvaluation(
            triggered = triggered,
            matches = matchesForNotification,
            matchCount = matchesForNotification.size
        )
    }

    private data class RuleEvaluation(
        val triggered: Boolean,
        val matches: List<JsonObject>,
        val matchCount: Int
    )

    private fun serializeMatches(matches: List<JsonObject>): String =
        DEFAULT_PRETTY_JSON.encodeToString(ListSerializer(JsonObject.serializer()), matches)

    private suspend fun notifyRuleFailure(
        ruleId: String,
        ruleName: String,
        target: String,
        notifications: List<RuleNotificationInvocation>,
        fallbackNotifications: List<RuleNotificationInvocation>,
        error: Throwable,
        triggeredAt: Instant,
        phase: FailurePhase,
        failureCount: Int? = null,
        ruleMessage: String? = null,
        failureMessage: String? = null
    ) {
        if (!configuration.notificationDefaults.notifyOnFailures) {
            logger.debug { "Failure notifications disabled; skipping failure notification for rule $ruleId" }
            return
        }
        val registry = configuration.notifications
        val invocations = if (notifications.isNotEmpty()) notifications else fallbackNotifications
        val context = NotificationContext(
            ruleId = ruleId,
            ruleName = ruleName,
            triggeredAt = triggeredAt,
            matchCount = 0,
            matches = emptyList()
        )
        val baseVariables = baseVariables(
            ruleId = ruleId,
            ruleName = ruleName,
            target = target,
            triggeredAt = triggeredAt,
            matchCount = 0,
            status = RuleRunStatus.FAILURE,
            failureCount = failureCount,
            error = error,
            phase = phase,
            ruleMessage = ruleMessage,
            failureMessage = failureMessage ?: ruleMessage,
            matchesJson = serializeMatches(emptyList())
        )
        var dispatched = false
        for (invocation in invocations) {
            val definition = registry.get(invocation.notificationId)
            if (definition == null) {
                logger.warn { "Skipping failure notification '${invocation.notificationId}' for rule $ruleId because it is not defined" }
                continue
            }
            val variables = mergedVariables(baseVariables, definition, invocation)
            val result = runCatching { definition.dispatch(variables, context) }
            if (result.isSuccess) {
                dispatched = true
            } else {
                val failure = result.exceptionOrNull() ?: continue
                logger.error(failure) { "Failure notification '${invocation.notificationId}' failed for rule $ruleId" }
            }
        }
        if (!dispatched) {
            logger.error(error) { "Failure notifications unavailable for rule $ruleId" }
        }
    }

    private fun baseVariables(
        ruleId: String,
        ruleName: String,
        target: String,
        triggeredAt: Instant,
        matchCount: Int,
        status: RuleRunStatus,
        failureCount: Int?,
        error: Throwable?,
        phase: FailurePhase?,
        ruleMessage: String?,
        failureMessage: String?,
        matchesJson: String?
    ): MutableMap<String, String> = buildMap {
        putVariable(NotificationVariable.RULE_NAME, ruleName)
        putVariable(NotificationVariable.RULE_ID, ruleId)
        putVariable(NotificationVariable.MATCH_COUNT, matchCount.toString())
        putVariable(NotificationVariable.TIMESTAMP, triggeredAt.toString())
        putVariable(NotificationVariable.TARGET, target)
        putVariable(NotificationVariable.STATUS, status.name)
        putVariableIfNotNull(NotificationVariable.FAILURE_COUNT, failureCount?.toString())
        putVariableIfNotNull(NotificationVariable.RULE_MESSAGE, ruleMessage)
        putVariableIfNotNull(NotificationVariable.FAILURE_MESSAGE, failureMessage)
        putVariableIfNotNull(NotificationVariable.MATCHES_JSON, matchesJson)
        error?.let {
            val simpleName = it::class.simpleName ?: it::class.toString()
            putVariable(NotificationVariable.ERROR_MESSAGE, it.message ?: simpleName)
            putVariable(NotificationVariable.ERROR_TYPE, simpleName)
        }
        phase?.let { putVariable(NotificationVariable.FAILURE_PHASE, it.name) }
    }.toMutableMap()

    private enum class FailurePhase {
        CONFIGURATION,
        EXECUTION
    }

    private enum class RuleRunStatus {
        SUCCESS,
        FAILURE
    }

    private class NotificationDeliveryException(
        ruleId: String,
        causes: List<Throwable>
    ) : Exception("One or more notifications failed for rule $ruleId") {
        init {
            causes.forEach { addSuppressed(it) }
        }
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
