package com.jillesvangurp.ktsearch

import com.jillesvangurp.serializationext.DEFAULT_JSON
import kotlinx.serialization.json.Json

/**
 * OpenSearch client with AWS SigV4 support.
 *
 * This wraps [SearchClient] and configures [KtorRestClient] with a SigV4
 * [requestSigner][AwsSigV4Signer].
 */
class OpenSearchClient(
    val searchClient: SearchClient
) {
    constructor(
        nodes: Array<Node>,
        region: String,
        service: String? = null,
        credentialsProvider: AwsCredentialsProvider,
        https: Boolean = true,
        logging: Boolean = false,
        nodeSelector: NodeSelector = RoundRobinNodeSelector(nodes),
        json: Json = DEFAULT_JSON
    ) : this(
        SearchClient(
            restClient = KtorRestClient(
                nodes = nodes,
                https = https,
                logging = logging,
                nodeSelector = nodeSelector,
                requestSigner = AwsSigV4Signer(
                    AwsSigV4Config(
                        region = region,
                        service = service,
                        credentialsProvider = credentialsProvider
                    )
                )
            ),
            json = json
        )
    )

    constructor(
        host: String,
        region: String,
        port: Int = 443,
        service: String? = null,
        credentialsProvider: AwsCredentialsProvider,
        https: Boolean = true,
        logging: Boolean = false,
        json: Json = DEFAULT_JSON
    ) : this(
        nodes = arrayOf(Node(host, port)),
        region = region,
        service = service,
        credentialsProvider = credentialsProvider,
        https = https,
        logging = logging,
        json = json
    )

    fun close() = searchClient.close()
}
