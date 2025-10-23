package com.jillesvangurp.ktsearch.alert

import com.jillesvangurp.ktsearch.RestException
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.repository.IndexRepository
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.matchAll
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

class AlertRuleRepository internal constructor(
    private val repository: IndexRepository<AlertRule>,
    private val nowProvider: () -> Instant = { currentInstant() }
) {
    private val ensureMutex = Mutex()
    private var ensured = false

    suspend fun ensureIndex() {
        if (ensured) return
        ensureMutex.withLock {
            if (ensured) return
            createIndexIfNeeded()
            ensured = true
        }
    }

    suspend fun list(size: Int = 1000): List<AlertRule> {
        ensureIndex()
        return repository.searchDocuments(size = size) {
            query = matchAll()
        }
    }

    suspend fun get(id: String): AlertRule? {
        ensureIndex()
        return repository.getDocument(id)
    }

    suspend fun upsert(definition: AlertRuleDefinition): AlertRule {
        ensureIndex()
        val now = nowProvider()
        val existing = definition.id?.let { repository.getDocument(it) }
        val id = definition.id ?: existing?.id ?: generateId()
        val createdAt = existing?.createdAt ?: now
        val cron = CronSchedule.parse(definition.cronExpression)
        val nextRun = when {
            !definition.enabled -> null
            definition.startImmediately -> now
            existing?.nextRun != null -> existing.nextRun
            else -> cron.nextAfter(now)
        }
        val updated = AlertRule(
            id = id,
            name = definition.name,
            enabled = definition.enabled,
            cronExpression = definition.cronExpression,
            target = definition.target,
            queryJson = definition.queryJson,
            emailTemplate = definition.emailTemplate,
            createdAt = createdAt,
            updatedAt = now,
            lastRun = existing?.lastRun,
            nextRun = nextRun,
            failureCount = existing?.failureCount ?: 0,
            lastFailureMessage = existing?.lastFailureMessage
        )
        repository.index(updated, id = id)
        return updated
    }

    suspend fun delete(id: String) {
        ensureIndex()
        runCatching { repository.delete(id) }
    }

    suspend fun markExecutionSuccess(id: String, triggeredAt: Instant, nextRun: Instant) {
        ensureIndex()
        runCatching {
            repository.update(id) { existing ->
                existing.copy(
                    lastRun = triggeredAt,
                    nextRun = nextRun,
                    failureCount = 0,
                    lastFailureMessage = null,
                    updatedAt = triggeredAt
                )
            }
        }
    }

    suspend fun markExecutionFailure(id: String, triggeredAt: Instant, nextRun: Instant, error: Throwable) {
        ensureIndex()
        runCatching {
            repository.update(id) { existing ->
                existing.copy(
                    lastRun = triggeredAt,
                    nextRun = nextRun,
                    failureCount = (existing.failureCount + 1).coerceAtMost(Int.MAX_VALUE),
                    lastFailureMessage = error.message ?: error::class.simpleName,
                    updatedAt = triggeredAt
                )
            }
        }
    }

    suspend fun pause(id: String, at: Instant = nowProvider()) {
        ensureIndex()
        runCatching {
            repository.update(id) { existing ->
                if (existing.nextRun == null) existing else existing.copy(nextRun = null, updatedAt = at)
            }
        }
    }

    private suspend fun createIndexIfNeeded() {
        val indexName = repository.indexNameOrWriteAlias
        try {
            repository.createIndex(indexName) {
                mappings(dynamicEnabled = false) {
                    keyword(AlertRule::id)
                    keyword(AlertRule::name)
                    keyword(AlertRule::target)
                    keyword(AlertRule::cronExpression)
                    bool(AlertRule::enabled)
                    date(AlertRule::createdAt)
                    date(AlertRule::updatedAt)
                    date(AlertRule::lastRun)
                    date(AlertRule::nextRun)
                    number<Int>(AlertRule::failureCount)
                    objField(AlertRule::emailTemplate) {
                        keyword(EmailTemplate::from)
                        keyword(EmailTemplate::contentType)
                        keyword(EmailTemplate::to)
                        keyword(EmailTemplate::cc)
                        keyword(EmailTemplate::bcc)
                    }
                }
            }
        } catch (e: RestException) {
            if (e.status != 400) {
                throw e
            }
        }
    }

    companion object {
        fun create(
            client: SearchClient,
            indexWriteAlias: String,
            indexReadAlias: String = indexWriteAlias,
            nowProvider: () -> Instant = { currentInstant() }
        ): AlertRuleRepository {
            val repo = client.repository(indexWriteAlias, AlertRule.serializer(), indexReadAlias)
            return AlertRuleRepository(repo, nowProvider)
        }

        fun generateId(): String {
            val bytes = Random.nextBytes(16)
            return bytes.joinToString(separator = "") { byte ->
                ((byte.toInt() and 0xff) + 0x100).toString(16).substring(1)
            }
        }
    }
}
