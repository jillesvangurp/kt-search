package com.jillesvangurp.ktsearch.alert.notifications

enum class NotificationVariable(val key: String) {
    RULE_NAME("ruleName"),
    RULE_ID("ruleId"),
    MATCH_COUNT("matchCount"),
    TIMESTAMP("timestamp"),
    TARGET("target"),
    STATUS("status"),
    FAILURE_COUNT("failureCount"),
    ERROR_MESSAGE("errorMessage"),
    ERROR_TYPE("errorType"),
    FAILURE_PHASE("failurePhase")
}

internal fun MutableMap<String, String>.putVariable(variable: NotificationVariable, value: String) {
    this[variable.key] = value
}

internal fun MutableMap<String, String>.putVariableIfNotNull(variable: NotificationVariable, value: String?) {
    if (value != null) {
        putVariable(variable, value)
    }
}
