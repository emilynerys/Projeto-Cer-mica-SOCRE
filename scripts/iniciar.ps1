param([switch]$SemCompilar)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'preparar-local.ps1')
. (Join-Path $PSScriptRoot 'ambiente-local.ps1')
Push-Location $projectRoot
try {
    if (-not $SemCompilar) {
        & '.\mvnw.cmd' -B -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'A compilação falhou.' }
    }
    $jarPath = Join-Path $projectRoot 'target\producao-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) { throw 'JAR não encontrado. Execute sem -SemCompilar.' }
    $pidPath = Join-Path $projectRoot '.local\app.pid'
    if (Test-Path -LiteralPath $pidPath) {
        $existing = Get-Process -Id ([int](Get-Content -LiteralPath $pidPath)) -ErrorAction SilentlyContinue
        if ($existing) { throw 'O processo registrado ainda está ativo. Use scripts/parar.ps1 antes de iniciar novamente.' }
    }
    $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', ('"' + $jarPath + '"'), '--spring.profiles.active=dev') -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $projectRoot '.local\app.log') -RedirectStandardError (Join-Path $projectRoot '.local\app-error.log') -WindowStyle Hidden -PassThru
    $process.Id | Set-Content -LiteralPath $pidPath
    $appPort = if ($env:PORT) { $env:PORT } else { '8080' }
    $appUrl = "http://127.0.0.1:$appPort"
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) { throw 'A aplicação encerrou durante a inicialização. Consulte .local/app.log e .local/app-error.log.' }
        try {
            $response = Invoke-WebRequest -Uri "$appUrl/login" -TimeoutSec 2 -UseBasicParsing
            if ($response.StatusCode -eq 200 -and $response.Content.Contains('SOCRE')) { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Não foi possível confirmar a inicialização. Consulte .local/app.log antes de tentar novamente.' }
    Write-Host "Aplicação disponível em $appUrl (PID $($process.Id))."
    Write-Host 'Consulte .local/ACESSO_LOCAL.md para as contas de demonstração.'
} finally { Pop-Location }
