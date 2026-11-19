# Limpeza de apps numa TV Sony (Android TV / Google TV) via ADB, para Windows.
# Uso: powershell -ExecutionPolicy Bypass -File .\limpar_apps_tv.ps1 [-TvIp 192.168.1.183]
param([string]$TvIp = '192.168.1.183')
$ErrorActionPreference = 'Stop'
$Target = "${TvIp}:5555"

# Pacotes que nunca devem ser removidos (o sistema deixa de funcionar).
$Protegidos = '^(android|com\.android\.(systemui|settings|providers\..*|shell|packageinstaller|inputdevices|tv\.settings)|com\.google\.android\.(gms|gsf|tvlauncher|apps\.tv\.launcherx|tungsten\.setupwraith|katniss|leanbacklauncher|inputmethod\..*)|com\.sony\.dtv\.(tvx|systemui|settings.*|sonysystemservice|osat.*|devicecontrol.*))$'

# Sugestões habituais de "bloatware" (só são mostradas se estiverem instaladas).
$Sugestoes = @(
    'com.sony.dtv.sonyselect'
    'com.google.android.videos'
    'com.google.android.play.games'
    'com.google.android.youtube.tvmusic'
    'com.amazon.amazonvideo.livingroom'
    'com.netflix.ninja'
)

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error 'Instale o adb primeiro (instalar_adb.ps1).'
}

function Adb-Tv { & adb -s $Target @args }
function Listar([string[]]$Opcoes) {
    Adb-Tv shell pm list packages @Opcoes | ForEach-Object { ($_ -replace '^package:', '').Trim() } | Sort-Object
}
function Remover-Sistema([string]$p) {
    if ($p -match $Protegidos) { Write-Host "   $p está protegido; não removo."; return }
    Adb-Tv shell pm uninstall -k --user 0 $p | ForEach-Object { Write-Host "   ${p}: $_" }
}
function Confirmar([string]$pergunta) { (Read-Host "$pergunta [s/N]") -match '^[sS]$' }

Write-Host ">> A ligar a $Target (aceite o pedido de autorização no ecrã da TV)..."
adb connect $Target | Out-Null
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
    if ((adb -s $Target get-state 2>$null) -eq 'device') { $ok = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $ok) { Write-Error 'Sem ligação/autorização.' }

Write-Host ">> Modelo: $((Adb-Tv shell getprop ro.product.model).Trim())"
Write-Host '>> Espaço livre em /data:'; Adb-Tv shell df -h /data | Select-Object -Last 1

while ($true) {
    Write-Host @'

1) Listar apps instaladas por si (terceiros)
2) Desinstalar apps instaladas por si
3) Remover/desativar apps de sistema sugeridas (reversível)
4) Remover uma app de sistema pelo nome do pacote (reversível)
5) Limpar a cache de todas as apps
6) Restaurar uma app de sistema removida
0) Sair
'@
    switch (Read-Host 'Opção') {
        '1' { Listar @('-3') }
        '2' {
            $apps = @(Listar @('-3'))
            if ($apps.Count -eq 0) { Write-Host 'Nenhuma app de terceiros.'; break }
            foreach ($p in $apps) {
                if (Confirmar "Desinstalar $p?") { Adb-Tv uninstall $p | ForEach-Object { "   $_" } }
            }
        }
        '3' {
            $inst = @(Listar @())
            foreach ($p in $Sugestoes) {
                if ($inst -contains $p -and (Confirmar "Remover $p?")) { Remover-Sistema $p }
            }
        }
        '4' { $p = Read-Host 'Pacote'; if ($p) { Remover-Sistema $p } }
        '5' { Write-Host '>> A limpar caches...'; Adb-Tv shell pm trim-caches 999G; Write-Host '   Feito.' }
        '6' { $p = Read-Host 'Pacote a restaurar'; if ($p) { Adb-Tv shell cmd package install-existing $p } }
        '0' { Write-Host '>> Espaço livre em /data:'; Adb-Tv shell df -h /data | Select-Object -Last 1
              adb disconnect $Target | Out-Null; exit 0 }
        default { Write-Host 'Opção inválida.' }
    }
}
