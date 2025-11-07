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
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
    private val failureNotificationHistory = mutableMapOf<String, Instant>()
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
    private var startupResumeAt: Instant? = null

    suspend fun start(configuration: AlertConfiguration) {
        startMutex.withLock {
            if (started) {
                reload(configuration)
                return
            }
            startupResumeAt = nowProvider() + 1.minutes
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
            startupResumeAt = null
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
                val id = definition.id
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
                            nextRun = null,
                            alertStatus = RuleAlertStatus.UNKNOWN,
                            lastNotificationAt = null
                        )
                    }
                    failureActions += suspend {
                        val state = getRuleState(id)
                        dispatchFailureNotifications(
                            ruleState = state,
                            ruleId = id,
                            ruleName = definition.name ?: definition.id,
                            target = definition.target,
                            notifications = resolvedFailureNotifications,
                            fallbackNotifications = resolvedNotifications,
                            error = t,
                            triggeredAt = failureTime,
                            phase = FailurePhase.CONFIGURATION,
                            failureCount = state?.failureCount,
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
        ruleName: String?,
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
        return ScheduledRule(
            id = rule.id,
            ruleName = rule.name,
            cronExpression = rule.cronExpression,
            cron = cron,
            hash = rule.executionHash(),
            initialNext = applyStartupDelay(initialNext)
        )
    }

    private fun resetScope() {
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(coroutineContext + supervisorJob)
    }

    private fun applyStartupDelay(candidate: Instant): Instant {
        val guard = startupResumeAt ?: return candidate
        val now = nowProvider()
        if (now >= guard) {
            startupResumeAt = null
            return candidate
        }
        return if (candidate < guard) guard else candidate
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
            name = definition.name ?: definition.id,
            enabled = definition.enabled,
            cronExpression = definition.cronExpression,
            target = definition.target,
            queryJson = when (val check = definition.check) {
                is RuleCheck.Search -> check.queryJson
                else -> null
            },
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
            firingCondition = when (definition) {
                is AlertRuleDefinition.Search -> definition.firingCondition
                is AlertRuleDefinition.ClusterStatusRule -> null
                else -> null
            },
            check = definition.check,
            alertStatus = existing?.alertStatus ?: RuleAlertStatus.UNKNOWN,
            lastNotificationAt = existing?.lastNotificationAt,
            lastFailureNotificationAt = existing?.lastFailureNotificationAt
        )
    }

    private suspend fun storeRuleState(rule: AlertRule) {
        val adjusted = rule.nextRun?.let { rule.copy(nextRun = applyStartupDelay(it)) } ?: rule
        rulesMutex.withLock {
            ruleStates[rule.id] = adjusted
            val lastFailureAt = adjusted.lastFailureNotificationAt
            if (lastFailureAt != null) {
                failureNotificationHistory[rule.id] = lastFailureAt
            } else {
                failureNotificationHistory.remove(rule.id)
            }
        }
    }

    private suspend fun getRuleState(id: String): AlertRule? =
        rulesMutex.withLock { ruleStates[id] }

    private suspend fun updateRuleState(id: String, transform: (AlertRule) -> AlertRule) {
        rulesMutex.withLock {
            val current = ruleStates[id] ?: return
            val updated = transform(current)
            ruleStates[id] = updated
            val lastFailureAt = updated.lastFailureNotificationAt
            if (lastFailureAt != null) {
                failureNotificationHistory[id] = lastFailureAt
            } else {
                failureNotificationHistory.remove(id)
            }
        }
    }

    private suspend fun removeRuleState(id: String) {
        rulesMutex.withLock {
            ruleStates.remove(id)
            failureNotificationHistory.remove(id)
        }
    }

    private inner class ScheduledRule(
        val id: String,
        private val ruleName: String,
        private val cronExpression: String,
        val cron: CronSchedule,
        var hash: Int,
        initialNext: Instant?
    ) {
        private var nextRun: Instant = initialNext ?: applyStartupDelay(cron.nextAfter(nowProvider()))
        private val logLabel = "$id ($ruleName)"
        private val job: Job = scope.launch {
            while (isActive) {
                val now = nowProvider()
                val delayDuration = nextRun - now
                logger.debug {
                    "Rule $logLabel checking schedule at $now; next run at $nextRun (${describeDelay(delayDuration)})"
                }
                if (delayDuration.isPositive()) {
                    delay(delayDuration)
                    continue
                }

                executeRule()
            }
        }

        init {
            logger.info { "Scheduled rule $logLabel with cron '$cronExpression'; next run at $nextRun" }
        }

        fun updateRule(rule: AlertRule) {
            val newHash = rule.executionHash()
            if (hash != newHash) {
                logger.debug { "Rule $logLabel configuration hash updated (old=$hash, new=$newHash)" }
            }
            hash = newHash
            if (rule.nextRun != null && rule.nextRun != nextRun) {
                nextRun = applyStartupDelay(rule.nextRun)
                logger.info { "Rule $logLabel rescheduled; next run at $nextRun" }
            } else {
                logger.debug { "Rule $logLabel updated without schedule change; next run stays $nextRun" }
            }
        }

        suspend fun cancel() {
            logger.info { "Cancelling scheduler for rule $logLabel" }
            job.cancelAndJoin()
        }

        private suspend fun executeRule() {
            val latestRule = getRuleState(id) ?: run {
                logger.debug { "Rule $logLabel has no materialized state; skipping execution" }
                nextRun = applyStartupDelay(nowProvider())
                return
            }
            if (!latestRule.enabled) {
                logger.debug { "Rule ${latestRule.id} disabled; postponing execution" }
                nextRun = applyStartupDelay(nowProvider())
                return
            }
            val triggeredAt = nowProvider()
            logger.debug {
                "Executing rule ${latestRule.id} (${latestRule.name}) targeting ${latestRule.target} at $triggeredAt"
            }
            try {
                val evaluation = evaluateRule(latestRule)
                val next = applyStartupDelay(cron.nextAfter(triggeredAt))
                nextRun = next
                logger.debug {
                    val total = evaluation.totalMatchCount ?: evaluation.matchCount.toLong()
                    val status = if (evaluation.triggered) "TRIGGERED" else "CLEAR"
                    "Rule ${latestRule.id} completed execution with status $status; matches=${evaluation.matchCount}, total=$total; next run at $next"
                }
                if (evaluation.triggered) {
                    val dispatchDecision = notificationDispatchDecision(latestRule, triggeredAt)
                    val matchSummary = buildString {
                        append("${evaluation.matchCount} matches")
                        evaluation.totalMatchCount?.let { total ->
                            if (total.toLong() != evaluation.matchCount.toLong()) {
                                append(" ($total total)")
                            }
                        }
                        evaluation.resultDescription?.takeIf { it.isNotBlank() }?.let { description ->
                            append("; ")
                            append(description)
                        }
                    }
                    if (dispatchDecision.shouldNotify) {
                        logger.info {
                            "Rule ${latestRule.id} triggered at $triggeredAt with $matchSummary; dispatching notifications (${dispatchDecision.reason})"
                        }
                        triggerNotifications(latestRule, evaluation, triggeredAt)
                    } else {
                        logger.info {
                            "Rule ${latestRule.id} triggered at $triggeredAt with $matchSummary; skipping notifications (${dispatchDecision.reason})"
                        }
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
                                dispatchDecision.shouldNotify -> triggeredAt
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
                            lastNotificationAt = null,
                            lastFailureNotificationAt = null
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val next = applyStartupDelay(cron.nextAfter(triggeredAt))
                nextRun = next
                updateRuleState(id) { current ->
                    current.copy(
                        lastRun = triggeredAt,
                        nextRun = next,
                        failureCount = (current.failureCount + 1).coerceAtMost(Int.MAX_VALUE),
                        lastFailureMessage = t.message ?: t::class.simpleName,
                        updatedAt = triggeredAt,
                        alertStatus = RuleAlertStatus.UNKNOWN,
                        lastNotificationAt = null
                    )
                }
                val updated = getRuleState(id) ?: latestRule
                logger.warn(t) { "Alert rule ${latestRule.id} execution failed" }
                dispatchFailureNotifications(
                    ruleState = updated,
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

        private fun describeDelay(delay: Duration): String {
            val millis = delay.inWholeMilliseconds
            return when {
                millis > 0 -> "in ${millis}ms"
                millis < 0 -> "overdue by ${-millis}ms"
                else -> "now"
            }
        }
    }

    private fun notificationDispatchDecision(rule: AlertRule, triggeredAt: Instant): NotificationDispatchDecision {
        return when (rule.alertStatus) {
            RuleAlertStatus.UNKNOWN -> NotificationDispatchDecision(
                shouldNotify = true,
                reason = "previous status unknown; notifying immediately"
            )
            RuleAlertStatus.CLEAR -> NotificationDispatchDecision(
                shouldNotify = true,
                reason = "rule recovered previously; sending fresh notification"
            )
            RuleAlertStatus.ALERTING -> {
                val intervalMillis = rule.repeatNotificationIntervalMillis
                if (intervalMillis == null) {
                    NotificationDispatchDecision(
                        shouldNotify = false,
                        reason = "rule already alerting and repeat notifications disabled (repeatNotificationIntervalMillis not set)"
                    )
                } else {
                    val lastNotified = rule.lastNotificationAt
                    if (lastNotified == null) {
                        NotificationDispatchDecision(
                            shouldNotify = true,
                            reason = "rule already alerting but no prior notification recorded; notifying now"
                        )
                    } else {
                        val interval = intervalMillis.milliseconds
                        val elapsed = triggeredAt - lastNotified
                        if (elapsed >= interval) {
                            NotificationDispatchDecision(
                                shouldNotify = true,
                                reason = "repeat interval ${interval.inWholeSeconds}s (${intervalMillis}ms) elapsed; last notified at $lastNotified"
                            )
                        } else {
                            val nextEligibleAt = lastNotified + interval
                            val remaining = nextEligibleAt - triggeredAt
                            NotificationDispatchDecision(
                                shouldNotify = false,
                                reason = "rule already alerting; repeat interval ${interval.inWholeSeconds}s (${intervalMillis}ms) not elapsed (last notified at $lastNotified, next allowed at $nextEligibleAt, ${remaining.inWholeSeconds}s remaining)"
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun dispatchFailureNotifications(
        ruleState: AlertRule?,
        ruleId: String,
        ruleName: String,
        target: String,
        notifications: List<RuleNotificationInvocation>,
        fallbackNotifications: List<RuleNotificationInvocation>,
        error: Throwable,
        triggeredAt: Instant,
        phase: FailurePhase,
        failureCount: Int?,
        ruleMessage: String?,
        failureMessage: String?
    ) {
        val decision = failureNotificationDispatchDecision(ruleId, ruleState, triggeredAt)
        if (!decision.shouldNotify) {
            logger.info {
                "Failure notifications for rule $ruleId skipped at $triggeredAt (${decision.reason})"
            }
            return
        }
        logger.info {
            "Dispatching failure notifications for rule $ruleId at $triggeredAt (${decision.reason})"
        }
        notifyRuleFailure(
            ruleId = ruleId,
            ruleName = ruleName,
            target = target,
            notifications = notifications,
            fallbackNotifications = fallbackNotifications,
            error = error,
            triggeredAt = triggeredAt,
            phase = phase,
            failureCount = failureCount,
            ruleMessage = ruleMessage,
            failureMessage = failureMessage
        )
        if (ruleState != null) {
            updateRuleState(ruleId) { current ->
                current.copy(lastFailureNotificationAt = triggeredAt)
            }
        } else {
            rulesMutex.withLock {
                failureNotificationHistory[ruleId] = triggeredAt
            }
        }
    }

    private suspend fun failureNotificationDispatchDecision(
        ruleId: String,
        ruleState: AlertRule?,
        triggeredAt: Instant
    ): NotificationDispatchDecision {
        val lastFailureNotified = ruleState?.lastFailureNotificationAt
            ?: rulesMutex.withLock { failureNotificationHistory[ruleId] }
        val repeatsDisabled = ruleState != null && ruleState.repeatNotificationIntervalMillis == null
        if (lastFailureNotified == null) {
            return NotificationDispatchDecision(
                shouldNotify = true,
                reason = "no prior failure notification recorded; notifying now"
            )
        }
        if (repeatsDisabled) {
            return NotificationDispatchDecision(
                shouldNotify = false,
                reason = "repeat notifications disabled for failures (repeatNotificationIntervalMillis not set)"
            )
        }
        val intervalMillis = ruleState?.repeatNotificationIntervalMillis
            ?: configuration.notificationDefaults.repeatNotificationsEvery.inWholeMilliseconds
        val interval = intervalMillis.milliseconds
        val elapsed = triggeredAt - lastFailureNotified
        return if (elapsed >= interval) {
            NotificationDispatchDecision(
                shouldNotify = true,
                reason = "failure repeat interval ${interval.inWholeSeconds}s (${intervalMillis}ms) elapsed; last failure notification at $lastFailureNotified"
            )
        } else {
            val remaining = interval - elapsed
            val nextEligibleAt = lastFailureNotified + interval
            NotificationDispatchDecision(
                shouldNotify = false,
                reason = "rule still failing; failure notifications throttled for another ${remaining.inWholeSeconds}s until $nextEligibleAt"
            )
        }
    }

    private data class NotificationDispatchDecision(
        val shouldNotify: Boolean,
        val reason: String
    )

    private suspend fun triggerNotifications(rule: AlertRule, evaluation: RuleEvaluation, triggeredAt: Instant) {
        val registry = configuration.notifications
        val matches = evaluation.matches
        val matchesJson = serializeMatches(matches)
        val totalMatchCount = evaluation.totalMatchCount ?: evaluation.matchCount.toLong()
        val contextMatchCount = totalMatchCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val baseVariables = baseVariables(
            ruleId = rule.id,
            ruleName = rule.name,
            target = rule.target,
            triggeredAt = triggeredAt,
            matchCount = totalMatchCount,
            sampleCount = evaluation.matchCount,
            totalMatchCount = evaluation.totalMatchCount ?: totalMatchCount,
            status = RuleRunStatus.ALERT,
            failureCount = null,
            error = null,
            phase = null,
            ruleMessage = rule.message,
            failureMessage = rule.failureMessage,
            matchesJson = matchesJson,
            resultDescription = evaluation.resultDescription,
            problemDetails = evaluation.problemDetails
        )
        val context = NotificationContext(
            ruleId = rule.id,
            ruleName = rule.name,
            triggeredAt = triggeredAt,
            matchCount = contextMatchCount,
            sampleCount = evaluation.matchCount,
            matches = matches,
            resultDescription = evaluation.resultDescription,
            totalMatchCount = evaluation.totalMatchCount ?: totalMatchCount,
            problemDetails = evaluation.problemDetails
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
            is RuleCheck.Search -> evaluateSearchRule(check, rule.firingCondition ?: RuleFiringCondition.GreaterThan(0))
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
        val observedTotalMatches = response.hits?.total?.value
        val effectiveTotalMatches = observedTotalMatches ?: matches.size.toLong()
        val evaluationCount = effectiveTotalMatches.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val triggered = firingCondition.shouldTrigger(evaluationCount)
        val matchesForNotification = if (triggered) matches else emptyList()
        val sampleCount = matchesForNotification.size
        val totalLabel = if (effectiveTotalMatches == 1L) "document" else "documents"
        val sampleLabel = if (sampleCount == 1) "document" else "documents"
        val sampleSummary = when {
            sampleCount == 0 -> "no sample documents captured"
            effectiveTotalMatches <= sampleCount.toLong() -> "showing all $sampleCount $sampleLabel"
            else -> "showing $sampleCount $sampleLabel"
        }
        val conditionExpression = firingCondition.describeCondition()
        val conditionOutcome = if (triggered) {
            "condition '$conditionExpression' satisfied"
        } else {
            "condition '$conditionExpression' not satisfied"
        }
        val problemDetails = "Found $effectiveTotalMatches $totalLabel for '${check.target}'; $conditionOutcome; $sampleSummary."
        val resultDescription = if (triggered) {
            "Search alert for '${check.target}' triggered: $conditionOutcome (count=$effectiveTotalMatches $totalLabel)"
        } else {
            "Search alert for '${check.target}' evaluated: $conditionOutcome (count=$effectiveTotalMatches $totalLabel)"
        }
        return RuleEvaluation(
            triggered = triggered,
            matches = matchesForNotification,
            matchCount = sampleCount,
            totalMatchCount = effectiveTotalMatches,
            resultDescription = resultDescription,
            problemDetails = problemDetails
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
        val clusterLabel = check.description.ifBlank { "cluster" }
        val actualStatus = health.status.name.lowercase()
        val expectedStatus = check.expectedStatus.name.lowercase()
        val resultDescription = if (triggered) {
            "$clusterLabel status is $actualStatus (expected $expectedStatus)"
        } else {
            "$clusterLabel status is $actualStatus"
        }
        return RuleEvaluation(
            triggered = triggered,
            matches = matchesForNotification,
            matchCount = matchesForNotification.size,
            totalMatchCount = matchesForNotification.size.toLong(),
            resultDescription = resultDescription,
            problemDetails = buildString {
                append("Cluster '${health.clusterName}' reports $actualStatus status; expected $expectedStatus.")
                if (health.timedOut) {
                    append(" Health API timed out.")
                }
            }
        )
    }

    private data class RuleEvaluation(
        val triggered: Boolean,
        val matches: List<JsonObject>,
        val matchCount: Int,
        val totalMatchCount: Long?,
        val resultDescription: String?,
        val problemDetails: String
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
        val failureDetails = buildString {
            append("Alert failed during ${phase.name.lowercase()} phase")
            failureCount?.let { append(" (failure count $it)") }
            val message = error.message ?: error::class.simpleName
            if (!message.isNullOrBlank()) {
                append(": ")
                append(message)
            }
        }
        val context = NotificationContext(
            ruleId = ruleId,
            ruleName = ruleName,
            triggeredAt = triggeredAt,
            matchCount = 0,
            sampleCount = 0,
            matches = emptyList(),
            resultDescription = failureDetails,
            totalMatchCount = 0L,
            problemDetails = failureDetails
        )
        val baseVariables = baseVariables(
            ruleId = ruleId,
            ruleName = ruleName,
            target = target,
            triggeredAt = triggeredAt,
            matchCount = 0L,
            sampleCount = 0,
            totalMatchCount = 0L,
            status = RuleRunStatus.FAILURE,
            failureCount = failureCount,
            error = error,
            phase = phase,
            ruleMessage = ruleMessage,
            failureMessage = failureMessage ?: ruleMessage,
            matchesJson = serializeMatches(emptyList()),
            resultDescription = failureDetails,
            problemDetails = failureDetails
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
        matchCount: Long,
        sampleCount: Int? = null,
        totalMatchCount: Long? = null,
        status: RuleRunStatus,
        failureCount: Int?,
        error: Throwable?,
        phase: FailurePhase?,
        ruleMessage: String?,
        failureMessage: String?,
        matchesJson: String?,
        resultDescription: String?,
        problemDetails: String?
    ): MutableMap<String, String> = buildMap {
        putVariable(NotificationVariable.RULE_NAME, ruleName)
        putVariable(NotificationVariable.RULE_ID, ruleId)
        val effectiveRuleMessage = ruleMessage?.takeIf { it.isNotBlank() } ?: ruleName
        putVariable(NotificationVariable.RULE_MESSAGE, effectiveRuleMessage)
        val effectiveTotal = totalMatchCount ?: matchCount
        val effectiveSample = sampleCount?.coerceAtLeast(0) ?: run {
            val safe = matchCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            safe
        }
        putVariable(NotificationVariable.MATCH_COUNT, effectiveTotal.toString())
        putVariable(NotificationVariable.TOTAL_MATCH_COUNT, effectiveTotal.toString())
        putVariable(NotificationVariable.SAMPLE_COUNT, effectiveSample.toString())
        putVariable(NotificationVariable.TIMESTAMP, triggeredAt.toString())
        putVariable(NotificationVariable.TARGET, target)
        putVariable(NotificationVariable.STATUS, status.name)
        putVariableIfNotNull(NotificationVariable.FAILURE_COUNT, failureCount?.toString())
        val effectiveFailureMessage = failureMessage?.takeIf { it.isNotBlank() } ?: effectiveRuleMessage
        putVariable(NotificationVariable.FAILURE_MESSAGE, effectiveFailureMessage)
        putVariableIfNotNull(NotificationVariable.MATCHES_JSON, matchesJson)
        putVariableIfNotNull(NotificationVariable.RESULT_DESCRIPTION, resultDescription)
        putVariableIfNotNull(NotificationVariable.PROBLEM_DETAILS, problemDetails)
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
        ALERT,
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
