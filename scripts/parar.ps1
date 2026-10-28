param([switch]$Banco)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$pidPath = Join-Path $projectRoot '.local\app.pid'
if (Test-Path -LiteralPath $pidPath) {
    $appProcessId = [int](Get-Content -LiteralPath $pidPath)
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$appProcessId"
    $expectedJar = Join-Path $projectRoot 'target\producao-0.1.0-SNAPSHOT.jar'
    if ($processInfo -and $processInfo.CommandLine.Contains($expectedJar)) {
        Stop-Process -Id $appProcessId
        Write-Host 'Aplicação SOCRE local encerrada.'
    } elseif ($processInfo) { throw 'O PID pertence a outro processo. Nenhum processo foi encerrado.' }
    Remove-Item -LiteralPath $pidPath
}
if ($Banco) {
    $localSecrets = Get-Content -LiteralPath (Join-Path $projectRoot '.local\local-secrets.json') -Raw | ConvertFrom-Json
    $pgData = (Resolve-Path -LiteralPath (Join-Path $projectRoot '.local\postgres-data')).Path
    if (-not $pgData.StartsWith($projectRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Diretório do banco fora do projeto.' }
    & (Join-Path $localSecrets.postgresBin 'pg_ctl.exe') stop -D $pgData -m fast
}
