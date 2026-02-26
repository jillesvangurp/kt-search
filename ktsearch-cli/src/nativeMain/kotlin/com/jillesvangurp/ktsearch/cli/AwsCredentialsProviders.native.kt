package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.AwsCredentials
import com.jillesvangurp.ktsearch.AwsCredentialsProvider

actual fun platformAwsCredentialsProvider(profile: String?): AwsCredentialsProvider {
    return AwsCredentialsProvider {
        envCredentials()?.let { return@AwsCredentialsProvider it }

        val profileName = profile
            ?: platformGetEnv("AWS_PROFILE")
            ?: "default"
        profileCredentials(profileName)?.let { return@AwsCredentialsProvider it }

        error(
            "AWS credentials not found. Use AWS_ACCESS_KEY_ID/" +
                "AWS_SECRET_ACCESS_KEY or a shared profile.",
        )
    }
}

private fun envCredentials(): AwsCredentials? {
    val accessKeyId = platformGetEnv("AWS_ACCESS_KEY_ID") ?: return null
    val secretAccessKey = platformGetEnv("AWS_SECRET_ACCESS_KEY") ?: return null
    return AwsCredentials(
        accessKeyId = accessKeyId,
        secretAccessKey = secretAccessKey,
        sessionToken = platformGetEnv("AWS_SESSION_TOKEN"),
    )
}

private fun profileCredentials(profileName: String): AwsCredentials? {
    val credentialsPath = platformGetEnv("AWS_SHARED_CREDENTIALS_FILE")
        ?: "~/.aws/credentials"
    val expandedCredentialsPath = expandHome(credentialsPath)
    if (!platformFileExists(expandedCredentialsPath)) {
        return null
    }
    val sections = parseIniSections(platformReadUtf8File(expandedCredentialsPath))
    val section = sections[profileName] ?: return null
    if (section.containsKey("role_arn")) {
        error(
            "Profile '$profileName' uses role-based credentials. " +
                "Use the JVM CLI for role/provider-chain support.",
        )
    }
    val accessKeyId = section["aws_access_key_id"] ?: return null
    val secretAccessKey = section["aws_secret_access_key"] ?: return null
    return AwsCredentials(
        accessKeyId = accessKeyId,
        secretAccessKey = secretAccessKey,
        sessionToken = section["aws_session_token"],
    )
}

private fun expandHome(path: String): String {
    if (!path.startsWith("~/")) {
        return path
    }
    val home = platformGetEnv("HOME") ?: return path
    return "$home/${path.removePrefix("~/")}"
}

private fun parseIniSections(content: String): Map<String, Map<String, String>> {
    val sections = mutableMapOf<String, MutableMap<String, String>>()
    var currentSection: String? = null
    content.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
            return@forEach
        }
        if (line.startsWith("[") && line.endsWith("]")) {
            currentSection = line.substring(1, line.length - 1).trim()
            sections.getOrPut(currentSection!!) { mutableMapOf() }
            return@forEach
        }
        val section = currentSection ?: return@forEach
        val equalsIndex = line.indexOf('=')
        if (equalsIndex <= 0 || equalsIndex == line.length - 1) {
            return@forEach
        }
        val key = line.substring(0, equalsIndex).trim().lowercase()
        val value = line.substring(equalsIndex + 1).trim()
        sections.getOrPut(section) { mutableMapOf() }[key] = value
    }
    return sections
}
