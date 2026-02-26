package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.AwsCredentials
import com.jillesvangurp.ktsearch.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider

actual fun platformAwsCredentialsProvider(profile: String?): AwsCredentialsProvider {
    val provider = if (profile.isNullOrBlank()) {
        DefaultCredentialsProvider.create()
    } else {
        ProfileCredentialsProvider.builder().profileName(profile).build()
    }
    return AwsCredentialsProvider {
        val credentials = provider.resolveCredentials()
        val sessionToken = (credentials as? AwsSessionCredentials)?.sessionToken()
        AwsCredentials(
            accessKeyId = credentials.accessKeyId(),
            secretAccessKey = credentials.secretAccessKey(),
            sessionToken = sessionToken,
        )
    }
}
