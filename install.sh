#!/usr/bin/env bash
# Installe YT Downloader sur ce Mac (compilation locale, aucune alerte Gatekeeper).
#
#   curl -fsSL https://raw.githubusercontent.com/powermaxv2/IA_ytb/claude/youtube-downloader-mac-51ojpa/install.sh | bash
#
set -euo pipefail

REPO="powermaxv2/IA_ytb"
BRANCH="${YTD_BRANCH:-claude/youtube-downloader-mac-51ojpa}"
APP_NAME="YT Downloader"

say()  { printf "\n\033[1;35m▶︎ %s\033[0m\n" "$1"; }
fail() { printf "\n\033[1;31m✖ %s\033[0m\n" "$1" >&2; exit 1; }

[[ "$(uname -s)" == "Darwin" ]] || fail "Ce script fonctionne uniquement sur macOS."

MACOS_MAJOR="$(sw_vers -productVersion | cut -d. -f1)"
(( MACOS_MAJOR >= 13 )) || fail "macOS 13 (Ventura) ou plus récent est requis (vous avez $(sw_vers -productVersion))."

say "Vérification des outils de développement Apple…"
if ! xcode-select -p >/dev/null 2>&1 || ! command -v swift >/dev/null 2>&1; then
  xcode-select --install >/dev/null 2>&1 || true
  fail "Les « Command Line Tools » d'Apple sont nécessaires.
   Une fenêtre vient de s'ouvrir : cliquez sur « Installer » et attendez la fin (5 à 15 min).
   Puis relancez exactement la même commande."
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

say "Téléchargement du code source…"
# Pin the exact commit so what is built is what was resolved (HTTPS only, GitHub only).
COMMIT="$(curl -fsSL --proto '=https' -H "Accept: application/vnd.github.sha" \
  "https://api.github.com/repos/${REPO}/commits/${BRANCH}" 2>/dev/null || true)"
if [[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
  echo "  Commit : $COMMIT"
  REF="$COMMIT"
else
  REF="refs/heads/${BRANCH}"
fi
curl -fsSL --proto '=https' "https://codeload.github.com/${REPO}/tar.gz/${REF}" | tar -xz -C "$WORK"
SRC="$(find "$WORK" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
[[ -n "$SRC" ]] || fail "Archive du code source introuvable."

say "Compilation (1 à 3 minutes)…"
cd "$SRC"
ARCHS=native UPDATE_BRANCH="$BRANCH" ./scripts/build_app.sh

say "Installation dans Applications…"
DEST="/Applications"
if [[ ! -w "$DEST" ]]; then
  DEST="$HOME/Applications"
  mkdir -p "$DEST"
fi
osascript -e "tell application \"$APP_NAME\" to quit" >/dev/null 2>&1 || true
rm -rf "$DEST/$APP_NAME.app"
cp -R "dist/$APP_NAME.app" "$DEST/"
xattr -cr "$DEST/$APP_NAME.app" 2>/dev/null || true

# Copie du .dmg dans Téléchargements (pratique pour l'installer sur un autre Mac).
cp -f "dist/YT-Downloader.dmg" "$HOME/Downloads/" 2>/dev/null || true

printf "\n\033[1;32m✔ %s est installé dans %s\033[0m\n" "$APP_NAME" "$DEST"
printf "  Image disque : ~/Downloads/YT-Downloader.dmg\n\n"
open "$DEST/$APP_NAME.app"
