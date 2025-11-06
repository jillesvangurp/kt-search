package com.jillesvangurp.ktsearch.alert.notifications

enum class NotificationVariable(val key: String) {
    RULE_NAME("ruleName"),
    RULE_ID("ruleId"),
    RULE_MESSAGE("ruleMessage"),
    FAILURE_MESSAGE("failureMessage"),
    MATCH_COUNT("matchCount"),
    SAMPLE_COUNT("sampleCount"),
    TOTAL_MATCH_COUNT("totalMatchCount"),
    TIMESTAMP("timestamp"),
    TARGET("target"),
    STATUS("status"),
    FAILURE_COUNT("failureCount"),
    ERROR_MESSAGE("errorMessage"),
    ERROR_TYPE("errorType"),
    FAILURE_PHASE("failurePhase"),
    MATCHES_JSON("matchesJson"),
    PROBLEM_DETAILS("problemDetails"),
    RESULT_DESCRIPTION("resultDescription")
}

internal fun MutableMap<String, String>.putVariable(variable: NotificationVariable, value: String) {
    this[variable.key] = value
}

internal fun MutableMap<String, String>.putVariableIfNotNull(variable: NotificationVariable, value: String?) {
    if (value != null) {
        putVariable(variable, value)
    }
}
