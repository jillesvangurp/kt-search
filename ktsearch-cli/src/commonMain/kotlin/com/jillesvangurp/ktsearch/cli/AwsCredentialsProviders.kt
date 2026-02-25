package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.AwsCredentialsProvider

/**
 * Resolves an AWS credentials provider for the current platform.
 *
 * If [profile] is null, this should use the platform's default provider chain.
 */
expect fun platformAwsCredentialsProvider(profile: String?): AwsCredentialsProvider
