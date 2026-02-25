package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.AwsCredentialsProvider

actual fun platformAwsCredentialsProvider(profile: String?): AwsCredentialsProvider {
    error(
        "AWS SigV4 credential discovery is not supported on wasmJs CLI.",
    )
}
