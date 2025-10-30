package com.jillesvangurp.ktsearch.alert.notifications

internal fun renderTemplate(template: String, context: Map<String, String>): String =
    context.entries.fold(template) { acc, (key, value) ->
        acc.replace("{{${key}}}", value)
    }
