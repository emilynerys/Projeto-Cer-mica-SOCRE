param([switch]$Teste)
$projectRoot = Split-Path -Parent $PSScriptRoot
$secretPath = Join-Path $projectRoot '.local\local-secrets.json'
if (-not (Test-Path -LiteralPath $secretPath)) { throw 'Execute scripts/preparar-local.ps1 primeiro.' }
$localSecrets = Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
$databaseName = if ($Teste) { 'socre_test' } else { 'socre_dev' }
$env:DB_URL = "jdbc:postgresql://127.0.0.1:55432/$databaseName"
$env:DB_USER = 'socre_app'
$env:DB_PASSWORD = $localSecrets.appPassword
$env:DB_MIGRATION_USER = 'socre_owner'
$env:DB_MIGRATION_PASSWORD = $localSecrets.ownerPassword
$env:SOCRE_DEMO_PASSWORD = $localSecrets.demoPassword
$env:SECURE_COOKIE = 'false'
$env:SERVER_ADDRESS = '127.0.0.1'
