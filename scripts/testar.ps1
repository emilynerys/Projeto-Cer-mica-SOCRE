$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'preparar-local.ps1')
. (Join-Path $PSScriptRoot 'ambiente-local.ps1') -Teste
Push-Location $projectRoot
try {
    & '.\mvnw.cmd' -B '-Dspring.profiles.active=test' verify
    if ($LASTEXITCODE -ne 0) { throw 'Existem falhas na verificação. Consulte target/surefire-reports.' }
} finally { Pop-Location }
