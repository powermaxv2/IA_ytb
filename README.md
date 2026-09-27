# YT Downloader pour macOS

Application macOS native (SwiftUI) pour télécharger des vidéos YouTube **avec le son**, dans la qualité de votre choix — de 144p jusqu'au **maximum disponible (4K, 8K, HDR, 60 fps)** — ou seulement l'audio.

## Fonctionnalités

- **Coller / glisser-déposer** un lien → analyse automatique des qualités disponibles
- **Choix de la qualité** sous forme de tuiles : résolution, fps, HDR et taille estimée ; la meilleure qualité est marquée **MAX**
- **Audio + vidéo fusionnés** automatiquement (YouTube sépare les flux au-delà de 720p)
- Formats vidéo : **MP4**, **MKV**, ou **MP4 QuickTime** (H.264, lecture native sur Mac)
- **Audio seul** : M4A (AAC), MP3, Opus (flux d'origine) ou FLAC
- **File d'attente** avec progression, vitesse, temps restant, annulation et nouvelle tentative
- Miniature, métadonnées, chapitres et sous-titres intégrés (réglables)
- Notification à la fin d'un téléchargement, bouton « Afficher dans le Finder »
- Interface sombre moderne, fenêtre sans barre de titre

Au premier lancement, l'app installe elle-même ses composants dans
`~/Library/Application Support/YTDownloader/bin` :

| Outil | Rôle |
|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | moteur de téléchargement |
| FFmpeg / ffprobe | fusion audio + vidéo, conversion audio |
| [Deno](https://deno.com) | environnement JavaScript requis par yt-dlp pour débloquer toutes les qualités YouTube |

Si vous les avez déjà via Homebrew (`/opt/homebrew/bin`), ils sont réutilisés.

## Installation

### Option 1 — télécharger l'app compilée

1. Onglet **Actions** du dépôt → dernier build « Build macOS app » → artefact **YT-Downloader-macOS**
   (ou page **Releases** si un tag `v*` a été publié).
2. Dézippez, glissez **YT Downloader.app** dans `/Applications`.
3. L'app n'est pas notarisée par Apple : au premier lancement, faites **clic droit → Ouvrir**, ou exécutez :
   ```bash
   xattr -cr "/Applications/YT Downloader.app"
   ```

### Option 2 — compiler vous-même

Prérequis : macOS 13 ou plus récent, Xcode (ou les Command Line Tools) installé.

```bash
git clone https://github.com/powermaxv2/IA_ytb.git
cd IA_ytb
./scripts/build_app.sh          # crée dist/YT Downloader.app (universel Apple Silicon + Intel)
open dist
```

Pour compiler uniquement pour votre Mac : `ARCHS=arm64 ./scripts/build_app.sh`.
Pour lancer en développement : `swift run` (les notifications sont désactivées hors bundle .app).

## Utilisation

1. Copiez un lien YouTube puis cliquez **Coller** (ou glissez le lien dans la fenêtre).
2. Choisissez **Vidéo + son** ou **Audio seul**.
3. Sélectionnez la qualité (la tuile **MAX** est présélectionnée) et le format.
4. **Télécharger** (⌘↩). Les fichiers arrivent dans `~/Téléchargements` par défaut (modifiable dans les Réglages, ⌘,).

> **Astuce** : au-delà de 1080p, YouTube ne fournit que du VP9 / AV1. QuickTime ne lit pas toujours ces codecs :
> utilisez [IINA](https://iina.io) ou VLC, ou choisissez le format « MP4 · QuickTime » (H.264, souvent limité à 1080p).

> **En cas d'erreur soudaine** (403, « not a bot »…) : Réglages → **Mettre à jour yt-dlp**.

## Structure

```
Sources/YTDownloader/
├── App.swift                 point d'entrée, fenêtre et réglages
├── Core/
│   ├── ToolManager.swift     détection / installation de yt-dlp, FFmpeg, Deno
│   ├── YTDLP.swift           analyse d'une vidéo (yt-dlp -J), messages d'erreur
│   ├── DownloadManager.swift file d'attente, suivi de progression
│   ├── Models.swift          formats, qualités, construction des arguments yt-dlp
│   ├── Shell.swift           exécution de processus (sortie en flux)
│   └── Prefs.swift           préférences utilisateur
└── Views/                    interface SwiftUI
```

## Avertissement

Téléchargez uniquement des contenus dont vous détenez les droits ou dont le téléchargement est autorisé
(vos propres vidéos, licences libres, etc.) et respectez les conditions d'utilisation de YouTube.
