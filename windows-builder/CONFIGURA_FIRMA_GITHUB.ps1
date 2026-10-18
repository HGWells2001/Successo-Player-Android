# Eseguire da PowerShell con: .\CONFIGURA_FIRMA_GITHUB.ps1
# Imposta una sola volta la firma persistente per le build di GitHub Actions.
# IMPORTANTE: il file .keystore resta SOLO sul proprio computer; GitHub riceve
# una versione cifrata con Actions Secrets, NON un file in un repository pubblico.
param([string]$Repository = "HGWells2001/Successo-Player-Android")

$ErrorActionPreference = "Stop"
$keystore = Join-Path $env:LOCALAPPDATA "SuccessoPlayerBuild\successo_update.keystore"
if (-not (Test-Path $keystore)) {
    throw "Chiave assente: $keystore. Esegui prima COMPILA_SUCCESSO_PLAYER_v24.bat sul PC per creare una firma stabile e poi riprova."
}
$gh = Get-Command gh.exe -ErrorAction SilentlyContinue
if (-not $gh) {
    throw "Installa GitHub CLI (winget install --id GitHub.cli), poi esegui 'gh auth login' e riprova."
}
$keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue
if (-not $keytool -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $candidate) { $keytool = @{ Source = $candidate } }
}
if (-not $keytool) {
    throw "keytool.exe non trovato. Installa Java 17 o aggiungi JAVA_HOME al sistema."
}

# Il builder Windows utilizza la chiave debug persistente, alias e password noti.
# La sicurezza della firma dipende dalla RISERVATEZZA del file keystore.
$alias = "androiddebugkey"
$password = "android"
$info = (& $keytool.Source -list -v -keystore $keystore -storepass $password -alias $alias 2>&1) -join "\`n"
if ($LASTEXITCODE -ne 0) { throw "Impossibile leggere la chiave dal keystore: verifica il builder Windows." }
$match = [regex]::Match($info, 'SHA256:\s*([0-9A-Fa-f:]{59,95})')
if (-not $match.Success) { throw "Impossibile leggere l'impronta del certificato SHA256 da keytool." }
$fingerprint = $match.Groups[1].Value.Replace(":", "").ToLowerInvariant()
$encodedKey = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))

# GitHub CLI cifra localmente i secrets prima dell'invio.
& $gh.Source auth status
if ($LASTEXITCODE -ne 0) { throw "Accedi a GitHub con 'gh auth login' e riprova." }

$temp = Join-Path $env:TEMP ("successo-signing-" + [guid]::NewGuid().ToString("N") + ".env")
try {
    $lines = @(
        "SUCCESSO_KEYSTORE_BASE64=$encodedKey",
        "SUCCESSO_KEYSTORE_PASSWORD=$password",
        "SUCCESSO_KEY_ALIAS=$alias",
        "SUCCESSO_KEY_PASSWORD=$password",
        "SUCCESSO_CERT_SHA256=$fingerprint"
    )
    [IO.File]::WriteAllLines($temp, $lines, [Text.UTF8Encoding]::new($false))
    & $gh.Source secret set -R $Repository -f $temp
    if ($LASTEXITCODE -ne 0) { throw "Il caricamento dei GitHub Secrets non e' riuscito." }
} finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}

Write-Host "Firma permanente configurata correttamente su GitHub." -ForegroundColor Green
Write-Host "Impronta SHA256: $fingerprint"
Write-Host "Il backup fondamentale e': $keystore" -ForegroundColor Yellow
Write-Host "Esegui la build firmata in GitHub Actions: https://github.com/$Repository/actions/workflows/build-apk.yml"
