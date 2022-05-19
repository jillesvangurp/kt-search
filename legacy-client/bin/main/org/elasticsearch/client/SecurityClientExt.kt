// Generated by EsKotlinCodeGenPlugin.
//
// Do not modify. This code is regenerated regularly. 
package org.elasticsearch.client

import com.jillesvangurp.eskotlinwrapper.SuspendingActionListener.Companion.suspending
import org.elasticsearch.client.security.AuthenticateResponse
import org.elasticsearch.client.security.ClearApiKeyCacheRequest
import org.elasticsearch.client.security.ClearPrivilegesCacheRequest
import org.elasticsearch.client.security.ClearPrivilegesCacheResponse
import org.elasticsearch.client.security.ClearRealmCacheRequest
import org.elasticsearch.client.security.ClearRealmCacheResponse
import org.elasticsearch.client.security.ClearRolesCacheRequest
import org.elasticsearch.client.security.ClearRolesCacheResponse
import org.elasticsearch.client.security.ClearSecurityCacheResponse
import org.elasticsearch.client.security.ClearServiceAccountTokenCacheRequest
import org.elasticsearch.client.security.CreateApiKeyRequest
import org.elasticsearch.client.security.CreateApiKeyResponse
import org.elasticsearch.client.security.CreateServiceAccountTokenRequest
import org.elasticsearch.client.security.CreateServiceAccountTokenResponse
import org.elasticsearch.client.security.CreateTokenRequest
import org.elasticsearch.client.security.CreateTokenResponse
import org.elasticsearch.client.security.DelegatePkiAuthenticationRequest
import org.elasticsearch.client.security.DelegatePkiAuthenticationResponse
import org.elasticsearch.client.security.DeletePrivilegesRequest
import org.elasticsearch.client.security.DeletePrivilegesResponse
import org.elasticsearch.client.security.DeleteRoleMappingRequest
import org.elasticsearch.client.security.DeleteRoleMappingResponse
import org.elasticsearch.client.security.DeleteRoleRequest
import org.elasticsearch.client.security.DeleteRoleResponse
import org.elasticsearch.client.security.DeleteServiceAccountTokenRequest
import org.elasticsearch.client.security.DeleteServiceAccountTokenResponse
import org.elasticsearch.client.security.DeleteUserRequest
import org.elasticsearch.client.security.DeleteUserResponse
import org.elasticsearch.client.security.GetApiKeyRequest
import org.elasticsearch.client.security.GetApiKeyResponse
import org.elasticsearch.client.security.GetBuiltinPrivilegesResponse
import org.elasticsearch.client.security.GetPrivilegesRequest
import org.elasticsearch.client.security.GetPrivilegesResponse
import org.elasticsearch.client.security.GetRoleMappingsRequest
import org.elasticsearch.client.security.GetRoleMappingsResponse
import org.elasticsearch.client.security.GetRolesRequest
import org.elasticsearch.client.security.GetRolesResponse
import org.elasticsearch.client.security.GetServiceAccountCredentialsRequest
import org.elasticsearch.client.security.GetServiceAccountCredentialsResponse
import org.elasticsearch.client.security.GetServiceAccountsRequest
import org.elasticsearch.client.security.GetServiceAccountsResponse
import org.elasticsearch.client.security.GetSslCertificatesResponse
import org.elasticsearch.client.security.GetUserPrivilegesResponse
import org.elasticsearch.client.security.GetUsersRequest
import org.elasticsearch.client.security.GetUsersResponse
import org.elasticsearch.client.security.GrantApiKeyRequest
import org.elasticsearch.client.security.HasPrivilegesRequest
import org.elasticsearch.client.security.HasPrivilegesResponse
import org.elasticsearch.client.security.InvalidateApiKeyRequest
import org.elasticsearch.client.security.InvalidateApiKeyResponse
import org.elasticsearch.client.security.InvalidateTokenRequest
import org.elasticsearch.client.security.InvalidateTokenResponse
import org.elasticsearch.client.security.PutPrivilegesRequest
import org.elasticsearch.client.security.PutPrivilegesResponse
import org.elasticsearch.client.security.PutRoleMappingRequest
import org.elasticsearch.client.security.PutRoleMappingResponse
import org.elasticsearch.client.security.PutRoleRequest
import org.elasticsearch.client.security.PutRoleResponse
import org.elasticsearch.client.security.PutUserRequest
import org.elasticsearch.client.security.PutUserResponse
import org.elasticsearch.client.security.QueryApiKeyRequest
import org.elasticsearch.client.security.QueryApiKeyResponse

public suspend fun SecurityClient.getSslCertificatesAsync(requestOptions: RequestOptions =
    RequestOptions.DEFAULT): GetSslCertificatesResponse {
  // generated code block
  return suspending {
      this.getSslCertificatesAsync(requestOptions,it)
  }
}

public suspend fun SecurityClient.getRolesAsync(request: GetRolesRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): GetRolesResponse {
  // generated code block
  return suspending {
      this.getRolesAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.putRoleAsync(request: PutRoleRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): PutRoleResponse {
  // generated code block
  return suspending {
      this.putRoleAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.deleteRoleMappingAsync(request: DeleteRoleMappingRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): DeleteRoleMappingResponse {
  // generated code block
  return suspending {
      this.deleteRoleMappingAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.deleteRoleAsync(request: DeleteRoleRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): DeleteRoleResponse {
  // generated code block
  return suspending {
      this.deleteRoleAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.createTokenAsync(request: CreateTokenRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): CreateTokenResponse {
  // generated code block
  return suspending {
      this.createTokenAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.invalidateTokenAsync(request: InvalidateTokenRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): InvalidateTokenResponse {
  // generated code block
  return suspending {
      this.invalidateTokenAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.getBuiltinPrivilegesAsync(requestOptions: RequestOptions =
    RequestOptions.DEFAULT): GetBuiltinPrivilegesResponse {
  // generated code block
  return suspending {
      this.getBuiltinPrivilegesAsync(requestOptions,it)
  }
}

public suspend fun SecurityClient.getPrivilegesAsync(request: GetPrivilegesRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): GetPrivilegesResponse {
  // generated code block
  return suspending {
      this.getPrivilegesAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.putPrivilegesAsync(request: PutPrivilegesRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): PutPrivilegesResponse {
  // generated code block
  return suspending {
      this.putPrivilegesAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.deletePrivilegesAsync(request: DeletePrivilegesRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): DeletePrivilegesResponse {
  // generated code block
  return suspending {
      this.deletePrivilegesAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.createApiKeyAsync(request: CreateApiKeyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): CreateApiKeyResponse {
  // generated code block
  return suspending {
      this.createApiKeyAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.getApiKeyAsync(request: GetApiKeyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): GetApiKeyResponse {
  // generated code block
  return suspending {
      this.getApiKeyAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.invalidateApiKeyAsync(request: InvalidateApiKeyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): InvalidateApiKeyResponse {
  // generated code block
  return suspending {
      this.invalidateApiKeyAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.grantApiKeyAsync(request: GrantApiKeyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): CreateApiKeyResponse {
  // generated code block
  return suspending {
      this.grantApiKeyAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.queryApiKeyAsync(request: QueryApiKeyRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): QueryApiKeyResponse {
  // generated code block
  return suspending {
      this.queryApiKeyAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.authenticateAsync(requestOptions: RequestOptions =
    RequestOptions.DEFAULT): AuthenticateResponse {
  // generated code block
  return suspending {
      this.authenticateAsync(requestOptions,it)
  }
}

public suspend fun SecurityClient.hasPrivilegesAsync(request: HasPrivilegesRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): HasPrivilegesResponse {
  // generated code block
  return suspending {
      this.hasPrivilegesAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.getUserPrivilegesAsync(requestOptions: RequestOptions =
    RequestOptions.DEFAULT): GetUserPrivilegesResponse {
  // generated code block
  return suspending {
      this.getUserPrivilegesAsync(requestOptions,it)
  }
}

public suspend fun SecurityClient.clearRealmCacheAsync(request: ClearRealmCacheRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): ClearRealmCacheResponse {
  // generated code block
  return suspending {
      this.clearRealmCacheAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.clearRolesCacheAsync(request: ClearRolesCacheRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): ClearRolesCacheResponse {
  // generated code block
  return suspending {
      this.clearRolesCacheAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.clearPrivilegesCacheAsync(request: ClearPrivilegesCacheRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): ClearPrivilegesCacheResponse {
  // generated code block
  return suspending {
      this.clearPrivilegesCacheAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.clearApiKeyCacheAsync(request: ClearApiKeyCacheRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): ClearSecurityCacheResponse {
  // generated code block
  return suspending {
      this.clearApiKeyCacheAsync(request,requestOptions,it)
  }
}

public suspend
    fun SecurityClient.clearServiceAccountTokenCacheAsync(request: ClearServiceAccountTokenCacheRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): ClearSecurityCacheResponse {
  // generated code block
  return suspending {
      this.clearServiceAccountTokenCacheAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.getUsersAsync(request: GetUsersRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): GetUsersResponse {
  // generated code block
  return suspending {
      this.getUsersAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.putUserAsync(request: PutUserRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): PutUserResponse {
  // generated code block
  return suspending {
      this.putUserAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.deleteUserAsync(request: DeleteUserRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): DeleteUserResponse {
  // generated code block
  return suspending {
      this.deleteUserAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.putRoleMappingAsync(request: PutRoleMappingRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): PutRoleMappingResponse {
  // generated code block
  return suspending {
      this.putRoleMappingAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.getRoleMappingsAsync(request: GetRoleMappingsRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): GetRoleMappingsResponse {
  // generated code block
  return suspending {
      this.getRoleMappingsAsync(request,requestOptions,it)
  }
}

public suspend fun SecurityClient.getServiceAccountsAsync(request: GetServiceAccountsRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): GetServiceAccountsResponse {
  // generated code block
  return suspending {
      this.getServiceAccountsAsync(request,requestOptions,it)
  }
}

public suspend
    fun SecurityClient.createServiceAccountTokenAsync(request: CreateServiceAccountTokenRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): CreateServiceAccountTokenResponse {
  // generated code block
  return suspending {
      this.createServiceAccountTokenAsync(request,requestOptions,it)
  }
}

public suspend
    fun SecurityClient.deleteServiceAccountTokenAsync(request: DeleteServiceAccountTokenRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): DeleteServiceAccountTokenResponse {
  // generated code block
  return suspending {
      this.deleteServiceAccountTokenAsync(request,requestOptions,it)
  }
}

public suspend
    fun SecurityClient.getServiceAccountCredentialsAsync(request: GetServiceAccountCredentialsRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): GetServiceAccountCredentialsResponse {
  // generated code block
  return suspending {
      this.getServiceAccountCredentialsAsync(request,requestOptions,it)
  }
}

public suspend
    fun SecurityClient.delegatePkiAuthenticationAsync(request: DelegatePkiAuthenticationRequest,
    requestOptions: RequestOptions = RequestOptions.DEFAULT): DelegatePkiAuthenticationResponse {
  // generated code block
  return suspending {
      this.delegatePkiAuthenticationAsync(request,requestOptions,it)
  }
}
