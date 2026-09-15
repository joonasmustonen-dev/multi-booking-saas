param(
    [string]$KeycloakUrl = 'http://localhost:8081',
    [string]$Realm = 'booking',
    [string]$FrontendClientId = 'booking-frontend',
    [string]$BackendAudience = 'booking-backend',
    [Parameter(Mandatory = $true)][System.Management.Automation.PSCredential]$Credential
)

$ErrorActionPreference = 'Stop'
$target = [uri]$KeycloakUrl
if ($target.Scheme -ne 'https' -and -not $target.IsLoopback) {
    throw 'Use HTTPS when configuring a remote identity provider.'
}

$tokenResponse = Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" -Body @{
    grant_type = 'password'
    client_id = 'admin-cli'
    username = $Credential.UserName
    password = $Credential.GetNetworkCredential().Password
}
$headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }
$realmPath = [uri]::EscapeDataString($Realm)
$clientQuery = [uri]::EscapeDataString($FrontendClientId)
$clients = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$realmPath/clients?clientId=$clientQuery" -Headers $headers
if ($clients.Count -ne 1) { throw 'Expected exactly one matching frontend client.' }

$mapperUrl = "$KeycloakUrl/admin/realms/$realmPath/clients/$($clients[0].id)/protocol-mappers/models"
$mappers = Invoke-RestMethod -Uri $mapperUrl -Headers $headers
$existing = $mappers | Where-Object { $_.name -eq 'booking-backend-audience' } | Select-Object -First 1
$mapper = @{
    name = 'booking-backend-audience'
    protocol = 'openid-connect'
    protocolMapper = 'oidc-audience-mapper'
    consentRequired = $false
    config = @{
        'included.custom.audience' = $BackendAudience
        'access.token.claim' = 'true'
        'id.token.claim' = 'false'
        'userinfo.token.claim' = 'false'
    }
}
if ($existing) {
    $mapper.id = [string]$existing.id
    Invoke-RestMethod -Method Put -Uri "$mapperUrl/$($existing.id)" -Headers $headers -ContentType 'application/json' -Body ($mapper | ConvertTo-Json -Depth 5) | Out-Null
} else {
    Invoke-RestMethod -Method Post -Uri $mapperUrl -Headers $headers -ContentType 'application/json' -Body ($mapper | ConvertTo-Json -Depth 5) | Out-Null
}

$roles = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$realmPath/roles" -Headers $headers
if (-not ($roles | Where-Object { $_.name -eq 'PLATFORM_ADMIN' })) {
    Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/admin/realms/$realmPath/roles" -Headers $headers -ContentType 'application/json' -Body '{"name":"PLATFORM_ADMIN"}' | Out-Null
}

Write-Output 'Backend audience mapper configured. Refresh the session or sign in again. No administrator roles were granted to users.'
