param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localDir = Join-Path $projectRoot '.local'
$pgData = Join-Path $localDir 'postgres-data'
$secretPath = Join-Path $localDir 'local-secrets.json'
New-Item -ItemType Directory -Path $localDir -Force | Out-Null
if (Test-Path -LiteralPath $secretPath) {
    $savedConfiguration = Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
    if (-not $PSBoundParameters.ContainsKey('PostgresBin')) { $PostgresBin = $savedConfiguration.postgresBin }
}
if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin 'pg_ctl.exe'))) {
    throw 'PostgreSQL não encontrado. Informe -PostgresBin com a pasta bin da instalação.'
}
if (-not (Test-Path -LiteralPath $secretPath)) {
    $localSecrets = [ordered]@{
        ownerPassword = [Guid]::NewGuid().ToString('N')
        appPassword = [Guid]::NewGuid().ToString('N')
        demoPassword = 'Socre!' + [Guid]::NewGuid().ToString('N').Substring(0, 14)
        postgresBin = $PostgresBin
    }
    $localSecrets | ConvertTo-Json | Set-Content -LiteralPath $secretPath -Encoding utf8
}
$localSecrets = Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
if ($localSecrets.postgresBin -ne $PostgresBin) {
    $localSecrets.postgresBin = $PostgresBin
    $localSecrets | ConvertTo-Json | Set-Content -LiteralPath $secretPath -Encoding utf8
}
if (-not (Test-Path -LiteralPath (Join-Path $pgData 'PG_VERSION'))) {
    $passwordFile = Join-Path $localDir 'init-password.tmp'
    try {
        [IO.File]::WriteAllText($passwordFile, $localSecrets.ownerPassword)
        & (Join-Path $PostgresBin 'initdb.exe') -D $pgData -U socre_owner "--pwfile=$passwordFile" --auth=scram-sha-256 --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw 'Não foi possível inicializar o banco separado do projeto.' }
    } finally {
        if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    }
    @("listen_addresses = '127.0.0.1'", 'port = 55432', 'max_connections = 40') |
        Add-Content -LiteralPath (Join-Path $pgData 'postgresql.conf')
}
& (Join-Path $PostgresBin 'pg_ctl.exe') status -D $pgData | Out-Null
if ($LASTEXITCODE -ne 0) {
    $pgLog = Join-Path $localDir 'postgres.log'
    $pgStart = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @('start','-D', ('"' + $pgData + '"'), '-l', ('"' + $pgLog + '"'), '-w') -WindowStyle Hidden -PassThru
    $pgStart.WaitForExit()
    if ($pgStart.ExitCode -ne 0) { throw "Falha ao iniciar PostgreSQL. Consulte $pgLog. Nenhum serviço instalado foi alterado." }
}
$previousPgPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $localSecrets.ownerPassword
    $psqlExe = Join-Path $PostgresBin 'psql.exe'
    $psqlArgs = @('-h','127.0.0.1','-p','55432','-U','socre_owner','-d','postgres','-v','ON_ERROR_STOP=1')
    $roleExists = & $psqlExe @psqlArgs -tAc "SELECT 1 FROM pg_roles WHERE rolname='socre_app'"
    if ($LASTEXITCODE -ne 0) { throw 'Falha na conexão ao banco do projeto.' }
    if ($roleExists -ne '1') {
        # Password was generated locally as hexadecimal text, never taken from SQL input.
        "CREATE ROLE socre_app LOGIN PASSWORD '$($localSecrets.appPassword)' NOSUPERUSER NOCREATEDB NOCREATEROLE;" | & $psqlExe @psqlArgs
        if ($LASTEXITCODE -ne 0) { throw 'Falha na criação do usuário restrito.' }
    }
    foreach ($databaseName in @('socre_dev','socre_test')) {
        $databaseExists = & $psqlExe @psqlArgs -tAc "SELECT 1 FROM pg_database WHERE datname='$databaseName'"
        if ($databaseExists -ne '1') {
            & $psqlExe @psqlArgs -c "CREATE DATABASE $databaseName OWNER socre_owner;"
            if ($LASTEXITCODE -ne 0) { throw "Falha ao criar $databaseName." }
        }
    }
} finally { $env:PGPASSWORD = $previousPgPassword }
$accessText = @"
# Acesso ao ambiente local de demonstração

Endereço: http://127.0.0.1:8080

| Perfil | E-mail |
| --- | --- |
| Supervisor | supervisor@socre.local |
| Gestor de Produção | gestor@socre.local |
| Gestor Geral | geral@socre.local |
| Administrador | admin@socre.local |

Senha de demonstração das quatro contas: $($localSecrets.demoPassword)

Estas contas são criadas somente no perfil dev. Não usar no piloto real.
Banco local isolado na porta 55432. Este arquivo e as credenciais não entram no Git.
"@
[IO.File]::WriteAllText((Join-Path $localDir 'ACESSO_LOCAL.md'), $accessText)
Write-Host 'Banco local preparado na porta 55432, separado dos bancos já instalados.'
