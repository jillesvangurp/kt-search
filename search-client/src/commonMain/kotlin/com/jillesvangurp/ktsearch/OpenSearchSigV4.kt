package com.jillesvangurp.ktsearch

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** AWS credentials used for SigV4 signing. */
data class AwsCredentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null
)

/** Provider for retrieving AWS credentials, potentially with rotation. */
fun interface AwsCredentialsProvider {
    suspend fun getCredentials(): AwsCredentials
}

/**
 * SigV4 configuration.
 *
 * [service] is optional; if null, the signer auto-detects `aoss` for
 * `*.aoss.amazonaws.com` endpoints and uses `es` otherwise.
 */
data class AwsSigV4Config(
    val region: String,
    val credentialsProvider: AwsCredentialsProvider,
    val service: String? = null,
    val clock: Clock = Clock.System
)

/** Request metadata passed into a [RequestSigner]. */
data class SigningRequest(
    val node: Node,
    val https: Boolean,
    val path: String,
    val method: String,
    val queryParameters: Map<String, List<String>>,
    val headers: Map<String, String> = emptyMap(),
    val payload: String? = null
)

/** Signer that can produce request headers. */
fun interface RequestSigner {
    suspend fun sign(request: SigningRequest): Map<String, String>
}

/**
 * AWS SigV4 implementation for OpenSearch.
 *
 * This signer supports OpenSearch Service (`es`) and OpenSearch Serverless
 * (`aoss`).
 */
class AwsSigV4Signer(
    private val config: AwsSigV4Config
) : RequestSigner {

    override suspend fun sign(request: SigningRequest): Map<String, String> {
        val credentials = config.credentialsProvider.getCredentials()
        val timestamp = config.clock.now().toSigV4Timestamp()
        val datestamp = timestamp.substring(0, 8)
        val service = resolveService(config.service, request.node.host)
        val payloadHash = sha256Hex(request.payload.orEmpty())
        val hostHeader = hostHeader(request)

        val headersToSign = mutableMapOf(
            "host" to hostHeader,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to timestamp
        )

        credentials.sessionToken?.let {
            headersToSign["x-amz-security-token"] = it
        }

        val canonicalRequest = canonicalRequest(
            method = request.method,
            path = canonicalUri(request.path),
            queryParameters = request.queryParameters,
            headers = headersToSign,
            payloadHash = payloadHash
        )
        val scope = "$datestamp/${config.region}/$service/aws4_request"
        val stringToSign = sigV4StringToSign(
            timestamp = timestamp,
            scope = scope,
            canonicalRequest = canonicalRequest
        )
        val signingKey = deriveSigningKey(
            secretAccessKey = credentials.secretAccessKey,
            datestamp = datestamp,
            region = config.region,
            service = service
        )
        val signature = hmacSha256Hex(signingKey, stringToSign)
        val signedHeaders = headersToSign.keys.joinToString(";")
        val authorization = buildString {
            append("AWS4-HMAC-SHA256 Credential=")
            append(credentials.accessKeyId)
            append('/')
            append(scope)
            append(", SignedHeaders=")
            append(signedHeaders)
            append(", Signature=")
            append(signature)
        }

        return buildMap {
            put("Authorization", authorization)
            put("X-Amz-Date", timestamp)
            put("X-Amz-Content-Sha256", payloadHash)
            credentials.sessionToken?.let { put("X-Amz-Security-Token", it) }
        }
    }
}

internal fun resolveService(configuredService: String?, host: String): String {
    configuredService?.takeIf { it.isNotBlank() }?.let { return it }
    return if (host.endsWith(".aoss.amazonaws.com")) "aoss" else "es"
}

internal fun canonicalRequest(
    method: String,
    path: String,
    queryParameters: Map<String, List<String>>,
    headers: Map<String, String>,
    payloadHash: String
): String {
    val canonicalHeaders = headers.entries
        .sortedBy { it.key }
        .joinToString(separator = "\n", postfix = "\n") { (key, value) ->
            "${key.lowercase()}:${value.trim().replace(Regex("\\s+"), " ")}"
        }
    val signedHeaders = headers.keys
        .map { it.lowercase() }
        .sorted()
        .joinToString(";")
    val canonicalQuery = canonicalQueryString(queryParameters)
    return buildString {
        append(method.uppercase())
        append('\n')
        append(path)
        append('\n')
        append(canonicalQuery)
        append('\n')
        append(canonicalHeaders)
        append('\n')
        append(signedHeaders)
        append('\n')
        append(payloadHash)
    }
}

internal fun sigV4StringToSign(
    timestamp: String,
    scope: String,
    canonicalRequest: String
): String {
    return buildString {
        append("AWS4-HMAC-SHA256\n")
        append(timestamp)
        append('\n')
        append(scope)
        append('\n')
        append(sha256Hex(canonicalRequest))
    }
}

internal fun canonicalQueryString(queryParameters: Map<String, List<String>>): String {
    if (queryParameters.isEmpty()) {
        return ""
    }
    return queryParameters
        .entries
        .flatMap { (key, values) ->
            if (values.isEmpty()) {
                listOf(percentEncode(key) to "")
            } else {
                values.map { value -> percentEncode(key) to percentEncode(value) }
            }
        }
        .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
        .joinToString("&") { "${it.first}=${it.second}" }
}

internal fun canonicalUri(path: String): String {
    val normalizedPath = when {
        path.isBlank() -> "/"
        path.startsWith("/") -> path
        else -> "/$path"
    }
    return normalizedPath
        .split("/")
        .joinToString("/") { part -> percentEncode(part) }
        .let { if (it.startsWith("/")) it else "/$it" }
}

internal fun hostHeader(request: SigningRequest): String {
    val port = request.node.port
    if (port == null) {
        return request.node.host
    }
    val defaultPort = if (request.https) 443 else 80
    return if (port == defaultPort) request.node.host else "${request.node.host}:$port"
}

internal fun kotlin.time.Instant.toSigV4Timestamp(): String {
    val utc = toLocalDateTime(TimeZone.UTC)
    return buildString {
        append(utc.year.toString().padStart(4, '0'))
        append(utc.month.number.toString().padStart(2, '0'))
        append(utc.day.toString().padStart(2, '0'))
        append('T')
        append(utc.hour.toString().padStart(2, '0'))
        append(utc.minute.toString().padStart(2, '0'))
        append(utc.second.toString().padStart(2, '0'))
        append('Z')
    }
}

internal fun deriveSigningKey(
    secretAccessKey: String,
    datestamp: String,
    region: String,
    service: String
): ByteArray {
    val kSecret = "AWS4$secretAccessKey".encodeToByteArray()
    val kDate = hmacSha256(kSecret, datestamp.encodeToByteArray())
    val kRegion = hmacSha256(kDate, region.encodeToByteArray())
    val kService = hmacSha256(kRegion, service.encodeToByteArray())
    return hmacSha256(kService, "aws4_request".encodeToByteArray())
}

internal fun percentEncode(value: String): String {
    val bytes = value.encodeToByteArray()
    val out = StringBuilder()
    bytes.forEach { b ->
        val c = b.toInt() and 0xff
        val keep =
            (c in 'a'.code..'z'.code) ||
                (c in 'A'.code..'Z'.code) ||
                (c in '0'.code..'9'.code) ||
                c == '-'.code ||
                c == '_'.code ||
                c == '.'.code ||
                c == '~'.code
        if (keep) {
            out.append(c.toChar())
        } else {
            out.append('%')
            out.append(hexChar((c ushr 4) and 0x0f))
            out.append(hexChar(c and 0x0f))
        }
    }
    return out.toString()
}

internal fun hexChar(value: Int): Char = "0123456789ABCDEF"[value and 0x0f]

internal fun sha256Hex(value: String): String = sha256(value.encodeToByteArray()).toHexLowerCase()

internal fun hmacSha256Hex(key: ByteArray, value: String): String =
    hmacSha256(key, value.encodeToByteArray()).toHexLowerCase()

internal fun ByteArray.toHexLowerCase(): String = buildString(size * 2) {
    this@toHexLowerCase.forEach { b ->
        val v = b.toInt() and 0xff
        append("0123456789abcdef"[v ushr 4])
        append("0123456789abcdef"[v and 0x0f])
    }
}

internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val blockSize = 64
    val normalizedKey = when {
        key.size > blockSize -> sha256(key)
        key.size < blockSize -> key + ByteArray(blockSize - key.size)
        else -> key
    }
    val oKeyPad = ByteArray(blockSize)
    val iKeyPad = ByteArray(blockSize)
    for (i in 0 until blockSize) {
        oKeyPad[i] = (normalizedKey[i].toInt() xor 0x5c).toByte()
        iKeyPad[i] = (normalizedKey[i].toInt() xor 0x36).toByte()
    }
    val inner = sha256(iKeyPad + data)
    return sha256(oKeyPad + inner)
}

internal fun sha256(input: ByteArray): ByteArray {
    val h = intArrayOf(
        0x6a09e667,
        0xbb67ae85.toInt(),
        0x3c6ef372,
        0xa54ff53a.toInt(),
        0x510e527f,
        0x9b05688c.toInt(),
        0x1f83d9ab,
        0x5be0cd19
    )
    val k = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    val bitLength = input.size.toLong() * 8L
    val totalLength = ((input.size + 9 + 63) / 64) * 64
    val padded = ByteArray(totalLength)
    input.copyInto(padded, 0)
    padded[input.size] = 0x80.toByte()
    for (i in 0 until 8) {
        val shift = (7 - i) * 8
        padded[totalLength - 8 + i] = ((bitLength ushr shift) and 0xff).toByte()
    }

    val w = IntArray(64)
    var chunkOffset = 0
    while (chunkOffset < padded.size) {
        var i = 0
        while (i < 16) {
            val j = chunkOffset + i * 4
            w[i] =
                ((padded[j].toInt() and 0xff) shl 24) or
                    ((padded[j + 1].toInt() and 0xff) shl 16) or
                    ((padded[j + 2].toInt() and 0xff) shl 8) or
                    (padded[j + 3].toInt() and 0xff)
            i++
        }
        while (i < 64) {
            val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
            val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
            i++
        }

        var a = h[0]
        var b = h[1]
        var c = h[2]
        var d = h[3]
        var e = h[4]
        var f = h[5]
        var g = h[6]
        var hh = h[7]

        i = 0
        while (i < 64) {
            val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = hh + s1 + ch + k[i] + w[i]
            val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            hh = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
            i++
        }

        h[0] += a
        h[1] += b
        h[2] += c
        h[3] += d
        h[4] += e
        h[5] += f
        h[6] += g
        h[7] += hh
        chunkOffset += 64
    }

    val out = ByteArray(32)
    for (i in h.indices) {
        val v = h[i]
        out[i * 4] = (v ushr 24).toByte()
        out[i * 4 + 1] = (v ushr 16).toByte()
        out[i * 4 + 2] = (v ushr 8).toByte()
        out[i * 4 + 3] = v.toByte()
    }
    return out
}

internal fun rotr(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))
