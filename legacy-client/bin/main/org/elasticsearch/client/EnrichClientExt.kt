// Generated by EsKotlinCodeGenPlugin.
//
// Do not modify. This code is regenerated regularly. 
package org.elasticsearch.client

import com.jillesvangurp.eskotlinwrapper.SuspendingActionListener.Companion.suspending
import org.elasticsearch.client.core.AcknowledgedResponse
import org.elasticsearch.client.enrich.DeletePolicyRequest
import org.elasticsearch.client.enrich.ExecutePolicyRequest
import org.elasticsearch.client.enrich.ExecutePolicyResponse
import org.elasticsearch.client.enrich.GetPolicyRequest
import org.elasticsearch.client.enrich.GetPolicyResponse
import org.elasticsearch.client.enrich.PutPolicyRequest
import org.elasticsearch.client.enrich.StatsRequest
import org.elasticsearch.client.enrich.StatsResponse

public suspend fun EnrichClient.statsAsync(request: StatsRequest, requestOptions: RequestOptions =
    RequestOptions.DEFAULT): StatsResponse {
  // generated code block
  return suspending {
      this.statsAsync(request,requestOptions,it)
  }
}

public suspend fun EnrichClient.putPolicyAsync(request: PutPolicyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): AcknowledgedResponse {
  // generated code block
  return suspending {
      this.putPolicyAsync(request,requestOptions,it)
  }
}

public suspend fun EnrichClient.deletePolicyAsync(request: DeletePolicyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): AcknowledgedResponse {
  // generated code block
  return suspending {
      this.deletePolicyAsync(request,requestOptions,it)
  }
}

public suspend fun EnrichClient.getPolicyAsync(request: GetPolicyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): GetPolicyResponse {
  // generated code block
  return suspending {
      this.getPolicyAsync(request,requestOptions,it)
  }
}

public suspend fun EnrichClient.executePolicyAsync(request: ExecutePolicyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): ExecutePolicyResponse {
  // generated code block
  return suspending {
      this.executePolicyAsync(request,requestOptions,it)
  }
}
