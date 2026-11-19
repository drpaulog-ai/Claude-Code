#!/usr/bin/env bash
# Instala o adb (Android platform-tools) em Linux ou macOS.
set -euo pipefail

if command -v adb >/dev/null; then
  echo "adb já instalado: $(adb version | head -n1)"; exit 0
fi

case "$(uname -s)" in
  Darwin)
    if command -v brew >/dev/null; then
      brew install --cask android-platform-tools
    else
      echo "Instale o Homebrew (https://brew.sh) e corra este script outra vez." >&2; exit 1
    fi ;;
  Linux)
    if   command -v apt-get >/dev/null; then sudo apt-get update && sudo apt-get install -y adb
    elif command -v dnf     >/dev/null; then sudo dnf install -y android-tools
    elif command -v pacman  >/dev/null; then sudo pacman -S --noconfirm android-tools
    else echo "Gestor de pacotes não suportado; instale o adb manualmente." >&2; exit 1
    fi ;;
  *) echo "Sistema não suportado; em Windows use instalar_adb.ps1." >&2; exit 1 ;;
esac

adb version | head -n1
