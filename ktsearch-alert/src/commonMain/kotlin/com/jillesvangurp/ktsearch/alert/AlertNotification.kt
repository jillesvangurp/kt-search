package com.jillesvangurp.ktsearch.alert

data class AlertNotification(
    val from: String,
    val to: List<String>,
    val subject: String,
    val body: String,
    val contentType: String,
    val cc: List<String>,
    val bcc: List<String>
)