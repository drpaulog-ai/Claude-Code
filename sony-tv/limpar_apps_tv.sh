#!/usr/bin/env bash
# Limpeza de apps numa TV Sony (Android TV / Google TV) via ADB.
# Uso: ./limpar_apps_tv.sh [IP_DA_TV]
set -euo pipefail

TV_IP="${1:-192.168.1.183}"
TARGET="$TV_IP:5555"

# Pacotes que nunca devem ser removidos (o sistema deixa de funcionar).
PROTEGIDOS='^(android|com\.android\.(systemui|settings|providers\..*|shell|packageinstaller|inputdevices|tv\.settings)|com\.google\.android\.(gms|gsf|tvlauncher|apps\.tv\.launcherx|tungsten\.setupwraith|katniss|leanbacklauncher|inputmethod\..*)|com\.sony\.dtv\.(tvx|systemui|settings.*|sonysystemservice|osat.*|devicecontrol.*))$'

# Sugestões habituais de "bloatware" (só são mostradas se estiverem instaladas).
SUGESTOES=(
  com.sony.dtv.sonyselect
  com.google.android.videos
  com.google.android.play.games
  com.google.android.youtube.tvmusic
  com.amazon.amazonvideo.livingroom
  com.netflix.ninja
)

adb_tv() { adb -s "$TARGET" "$@"; }

if ! command -v adb >/dev/null; then
  echo "ERRO: instale o adb (Android platform-tools) primeiro." >&2; exit 1
fi

echo ">> A ligar a $TARGET (aceite o pedido de autorização no ecrã da TV)..."
adb connect "$TARGET" >/dev/null
for _ in {1..30}; do
  [ "$(adb -s "$TARGET" get-state 2>/dev/null)" = device ] && break
  sleep 1
done
[ "$(adb -s "$TARGET" get-state 2>/dev/null)" = device ] || { echo "ERRO: sem ligação/autorização." >&2; exit 1; }

echo ">> Modelo: $(adb_tv shell getprop ro.product.model | tr -d '\r')"
echo ">> Espaço livre em /data:"; adb_tv shell df -h /data | tail -n1

listar() { adb_tv shell pm list packages "$@" | tr -d '\r' | sed 's/^package://' | sort; }

menu() {
  cat <<M

1) Listar apps instaladas por si (terceiros)
2) Desinstalar apps instaladas por si
3) Remover/desativar apps de sistema sugeridas (reversível)
4) Remover uma app de sistema pelo nome do pacote (reversível)
5) Limpar a cache de todas as apps
6) Restaurar uma app de sistema removida
0) Sair
M
}

remover_sistema() {
  local p="$1"
  if [[ "$p" =~ $PROTEGIDOS ]]; then echo "   $p está protegido; não removo."; return; fi
  adb_tv shell pm uninstall -k --user 0 "$p" | tr -d '\r' | sed "s/^/   $p: /"
}

while true; do
  menu; read -rp "Opção: " op
  case "$op" in
    1) listar -3 ;;
    2) mapfile -t apps < <(listar -3)
       [ ${#apps[@]} -eq 0 ] && { echo "Nenhuma app de terceiros."; continue; }
       for p in "${apps[@]}"; do
         read -rp "Desinstalar $p? [s/N] " r
         [[ "$r" =~ ^[sS]$ ]] && adb_tv uninstall "$p" | sed "s/^/   /"
       done ;;
    3) mapfile -t inst < <(listar)
       for p in "${SUGESTOES[@]}"; do
         printf '%s\n' "${inst[@]}" | grep -qx "$p" || continue
         read -rp "Remover $p? [s/N] " r
         [[ "$r" =~ ^[sS]$ ]] && remover_sistema "$p"
       done ;;
    4) read -rp "Pacote: " p; [ -n "$p" ] && remover_sistema "$p" ;;
    5) echo ">> A limpar caches..."; adb_tv shell pm trim-caches 999G && echo "   Feito." ;;
    6) read -rp "Pacote a restaurar: " p
       [ -n "$p" ] && adb_tv shell cmd package install-existing "$p" ;;
    0) break ;;
    *) echo "Opção inválida." ;;
  esac
done

echo ">> Espaço livre em /data:"; adb_tv shell df -h /data | tail -n1
adb disconnect "$TARGET" >/dev/null || true
