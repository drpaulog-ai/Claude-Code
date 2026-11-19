# Instala o adb (Android platform-tools) em Windows.
# Uso: powershell -ExecutionPolicy Bypass -File .\instalar_adb.ps1
$ErrorActionPreference = 'Stop'

if (Get-Command adb -ErrorAction SilentlyContinue) {
    Write-Host "adb já instalado: $((adb version)[0])"; exit 0
}

$destino = Join-Path $env:LOCALAPPDATA 'platform-tools'
$zip     = Join-Path $env:TEMP 'platform-tools.zip'
$url     = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'

Write-Host ">> A descarregar $url"
Invoke-WebRequest -Uri $url -OutFile $zip
Expand-Archive -Path $zip -DestinationPath $env:LOCALAPPDATA -Force
Remove-Item $zip

# Acrescenta ao PATH do utilizador (vale para novas janelas) e à sessão atual.
$path = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($path -notlike "*$destino*") {
    [Environment]::SetEnvironmentVariable('Path', "$path;$destino", 'User')
}
$env:Path += ";$destino"

Write-Host ">> Instalado: $((adb version)[0])"
