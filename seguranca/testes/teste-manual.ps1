# =====================================================================
# FrioVale - Teste manual de ANTES e DEPOIS dos dois controles (2FA e IDOR)
# Versao PowerShell (Windows PowerShell 5.1 ou PowerShell 7) do teste-manual.sh.
# Mesmo script para as duas fases: ele detecta se o login ainda entrega o
# token so com a senha (ANTES) ou se exige o segundo fator (DEPOIS).
#
# Uso (na pasta seguranca\testes):
#   powershell -ExecutionPolicy Bypass -File .\teste-manual.ps1 -ApiUrl http://localhost:8080 -MissionCode CODIGO_MISSAO
#
# Use APENAS usuarios sinteticos e o backend da propria equipe.
# OBS: a logica foi validada com a versao .sh; esta versao .ps1 nao pode ser
# executada no ambiente onde foi escrita - rode uma vez antes da apresentacao.
# =====================================================================
param(
    [string]$ApiUrl = "http://localhost:8080",
    [string]$MissionCode = $(if ($env:MISSION_CODE) { $env:MISSION_CODE } else { "CODIGO_MISSAO" }),
    [string]$OutDir = "..\evidencias"
)

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$OutFile = Join-Path $OutDir ("{0}_manual_{1}.txt" -f $MissionCode, $stamp)
Start-Transcript -Path $OutFile -Force | Out-Null

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Token = "", [string]$Body = "")
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{ Uri = "$ApiUrl$Path"; Method = $Method; Headers = $headers; ContentType = "application/json"; UseBasicParsing = $true }
    if ($Body) { $params["Body"] = $Body }
    try {
        $r = Invoke-WebRequest @params
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = [string]$r.Content }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        $text = ""
        if ($_.ErrorDetails) { $text = $_.ErrorDetails.Message }
        return [pscustomobject]@{ Status = $status; Body = $text }
    }
}

function Get-Json([string]$text) {
    try { return $text | ConvertFrom-Json } catch { return $null }
}

function Check([string]$Description, [int]$Status, [int[]]$Expected) {
    $ok = if ($Expected -contains $Status) { "OK" } else { "FALHOU" }
    "{0,-62} HTTP {1} (esperado {2}) -> {3}" -f $Description, $Status, ($Expected -join "|"), $ok | Write-Host
}

function Read-Plain([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

function Login-Body([string]$Email, [string]$Password) {
    return (@{ email = $Email; password = $Password } | ConvertTo-Json -Compress)
}

# Login completo (senha + codigo do app). Devolve o token de acesso.
function Get-AccessToken([string]$Label) {
    $email = Read-Host -Prompt "E-mail do $Label"
    $pass = Read-Plain "Senha do $Label"
    $r = Invoke-Api -Method POST -Path "/api/auth/login" -Body (Login-Body $email $pass)
    $b = Get-Json $r.Body
    if ($b -and $b.token) { Write-Host "[$Label] login devolveu o token de acesso so com a senha (sistema SEM 2FA)"; return $b.token }
    if ($b -and $b.enrollmentRequired) { Write-Host "[$Label] ainda nao cadastrou o 2FA. Entre uma vez pela tela web e escaneie o QR Code."; Stop-Transcript | Out-Null; exit 1 }
    $code = Read-Host -Prompt "Codigo atual do aplicativo autenticador do $Label"
    $v = Invoke-Api -Method POST -Path "/api/auth/2fa/verify" -Token $b.mfaToken -Body (@{ code = $code } | ConvertTo-Json -Compress)
    $vb = Get-Json $v.Body
    if (-not ($vb -and $vb.token)) { Write-Host "[$Label] codigo recusado: HTTP $($v.Status)"; Stop-Transcript | Out-Null; exit 1 }
    return $vb.token
}

$line = "-" * 60
Write-Host "FrioVale - teste manual | codigo de missao: $MissionCode | API: $ApiUrl | $(Get-Date -Format s)"
Write-Host $line; Write-Host "CONTROLE 1 - AUTENTICACAO EM DOIS FATORES"; Write-Host $line

$emailA = Read-Host -Prompt "E-mail do usuario A"
$passA = Read-Plain "Senha do usuario A"
$r = Invoke-Api -Method POST -Path "/api/auth/login" -Body (Login-Body $emailA $passA)
$b = Get-Json $r.Body

if ($b -and $b.token) {
    Write-Host "ANTES: o login com senha correta devolveu o TOKEN DE ACESSO (HTTP $($r.Status))"
    $t = Invoke-Api -Method GET -Path "/api/trips" -Token $b.token
    Write-Host "ANTES: GET /api/trips so com a senha -> HTTP $($t.Status) (acesso liberado sem 2o fator)"
    $accessA = $b.token
} else {
    Check "1. Senha correta sem OTP: login devolve so o mfaToken" $r.Status @(200)
    $mfa = $b.mfaToken
    $t = Invoke-Api -Method GET -Path "/api/trips" -Token $mfa
    Check "   Contorno: mfaToken em rota protegida" $t.Status @(403)
    if ($b.enrollmentRequired) { Write-Host "Cadastre o 2FA pela tela web e rode de novo."; Stop-Transcript | Out-Null; exit 1 }

    $t = Invoke-Api -Method POST -Path "/api/auth/2fa/verify" -Token $mfa -Body '{"code":"000000"}'
    Check "2. OTP incorreto (000000)" $t.Status @(401)

    $old = Read-Host -Prompt "Opcional - digite um codigo ANOTADO ha mais de 2 minutos (Enter para pular)"
    if ($old) {
        $t = Invoke-Api -Method POST -Path "/api/auth/2fa/verify" -Token $mfa -Body (@{ code = $old } | ConvertTo-Json -Compress)
        Check "3. OTP expirado" $t.Status @(401)
    }

    $code = Read-Host -Prompt "Digite o codigo ATUAL do aplicativo autenticador"
    $t = Invoke-Api -Method POST -Path "/api/auth/2fa/verify" -Token $mfa -Body (@{ code = $code } | ConvertTo-Json -Compress)
    Check "4. OTP valido" $t.Status @(200)
    $accessA = (Get-Json $t.Body).token

    $mfa2 = (Get-Json (Invoke-Api -Method POST -Path "/api/auth/login" -Body (Login-Body $emailA $passA)).Body).mfaToken
    $t = Invoke-Api -Method POST -Path "/api/auth/2fa/verify" -Token $mfa2 -Body (@{ code = $code } | ConvertTo-Json -Compress)
    Check "5. Reutilizar o mesmo OTP (replay)" $t.Status @(401)

    $rec = Read-Host -Prompt "Opcional - codigo de recuperacao NAO usado (Enter para pular)"
    if ($rec) {
        $t = Invoke-Api -Method POST -Path "/api/auth/2fa/recovery" -Token $mfa2 -Body (@{ recoveryCode = $rec } | ConvertTo-Json -Compress)
        Check "6. Codigo de recuperacao - 1o uso" $t.Status @(200)
        $mfa3 = (Get-Json (Invoke-Api -Method POST -Path "/api/auth/login" -Body (Login-Body $emailA $passA)).Body).mfaToken
        $t = Invoke-Api -Method POST -Path "/api/auth/2fa/recovery" -Token $mfa3 -Body (@{ recoveryCode = $rec } | ConvertTo-Json -Compress)
        Check "7. Mesmo codigo de recuperacao de novo" $t.Status @(401)
    }
}

Write-Host $line; Write-Host "CONTROLE 2 - PROTECAO CONTRA IDOR (A tenta usar os IDs de B)"; Write-Host $line
$accessB = Get-AccessToken "usuario B (dono do dispositivo B)"
$tripsB = @(Get-Json (Invoke-Api -Method GET -Path "/api/trips" -Token $accessB).Body)
if ($tripsB.Count -eq 0 -or -not $tripsB[0].id) { Write-Host "O usuario B precisa ter uma viagem atribuida."; Stop-Transcript | Out-Null; exit 1 }
$tripB = $tripsB[0].id
$contB = $tripsB[0].containerId
Write-Host "Dispositivo do usuario B: viagem $tripB, conteiner $contB"

$attacks = @(
    @("GET",  "/api/trips/$tripB/telemetry",             "",  "le as leituras do dispositivo B"),
    @("GET",  "/api/containers/$contB",                  "",  "consulta o conteiner B"),
    @("GET",  "/api/thingspeak/logs?containerId=$contB", "",  "le os logs brutos do dispositivo B"),
    @("GET",  "/api/reports/trips/$tripB/pdf",           "",  "baixa o PDF da viagem B"),
    @("PUT",  "/api/trips/$tripB", '{"status":"IN_TRANSIT","destination":"Alterado por A"}', "altera a configuracao da viagem B"),
    @("POST", "/api/ia/trips/$tripB/reprocess",          "",  "comando: reprocessar IA da viagem B"),
    @("POST", "/api/impacts/sync?containerId=$contB&results=1", "", "comando: sincronizar o dispositivo B")
)
foreach ($a in $attacks) {
    $t = Invoke-Api -Method $a[0] -Path $a[1] -Token $accessA -Body $a[2]
    Check ("A: " + $a[3]) $t.Status @(403, 404)
}

$tripsA = @(Get-Json (Invoke-Api -Method GET -Path "/api/trips" -Token $accessA).Body)
if (@($tripsA | Where-Object { $_.id -eq $tripB }).Count -gt 0) {
    Write-Host "A: lista de viagens contem a viagem B -> VULNERAVEL"
} else {
    Write-Host "A: lista de viagens NAO contem a viagem B -> OK"
}
$t = Invoke-Api -Method GET -Path "/api/trips/$tripB/telemetry" -Token $accessB
Check "B: le as leituras do PROPRIO dispositivo" $t.Status @(200)

Write-Host $line
Write-Host "Resultado salvo em: $OutFile"
Write-Host "No log do backend procure por: [$MissionCode] OTP BLOQUEADO  e  [$MissionCode] IDOR BLOQUEADO"
Stop-Transcript | Out-Null
