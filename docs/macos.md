# YT Downloader pour macOS

Application macOS native (SwiftUI) pour télécharger des vidéos YouTube **avec le son**, dans la qualité de votre choix — de 144p jusqu'au **maximum disponible (4K, 8K, HDR, 60 fps)** — ou seulement l'audio.

C'est l'app d'origine du dépôt. Elle est restée un téléchargeur vidéo : la boîte à outils **Gulp** n'existe que sur
Android (voir la [page d'accueil du dépôt](../README.md) et [`android/`](../android/README.md)). Les deux apps sont
indépendantes ; version macOS : fichier [`VERSION`](../VERSION).

> **Dépôt privé** : depuis octobre 2026, la commande d'installation en une ligne et les mises à jour automatiques
> lisent le code sur GitHub sans compte, ce que le dépôt privé ne permet plus. Clonez le dépôt avec votre compte
> GitHub puis compilez à la main (« Autres options » ci-dessous).

## Fonctionnalités

- **Coller / glisser-déposer** un lien → analyse automatique des qualités disponibles
- **Détection du presse-papiers** : copiez un lien YouTube, revenez dans l'app, un bandeau propose de l'analyser
- **Playlists** : liste des vidéos avec cases à cocher, qualité commune, téléchargement en file
- **Choix de la qualité** sous forme de tuiles : résolution, fps, HDR et taille estimée ; la meilleure qualité est marquée **MAX**
- **Audio + vidéo fusionnés** automatiquement (YouTube sépare les flux au-delà de 720p)
- Formats vidéo : **MP4**, **MKV**, ou **MP4 QuickTime** (H.264, lecture native sur Mac)
- **Audio seul** : M4A (AAC), MP3, Opus (flux d'origine) ou FLAC
- **File d'attente** avec progression, vitesse, temps restant, annulation et nouvelle tentative
- Miniature, métadonnées, chapitres et sous-titres intégrés (réglables)
- Notification à la fin d'un téléchargement, badge sur l'icône du Dock, bouton « Afficher dans le Finder »
- **Cookies du navigateur** (Safari, Chrome, Firefox, Brave, Edge, Arc) pour les vidéos avec limite d'âge
  ou quand YouTube demande « confirm you're not a bot »
- Interface sombre moderne, fenêtre sans barre de titre

Au premier lancement, l'app installe elle-même ses composants dans
`~/Library/Application Support/YTDownloader/bin` :

| Outil | Rôle |
|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | moteur de téléchargement |
| FFmpeg / ffprobe 6.1.1 | fusion audio + vidéo, conversion audio (empreinte vérifiée) |
| [Deno](https://deno.com) | environnement JavaScript requis par yt-dlp pour débloquer toutes les qualités YouTube |

Si vous les avez déjà via Homebrew (`/opt/homebrew/bin`), ils sont réutilisés.

## Installation (la plus simple)

Ouvrez **Terminal** (⌘ Espace → tapez « Terminal » → Entrée), collez cette ligne puis appuyez sur Entrée :

```bash
curl -fsSL https://raw.githubusercontent.com/powermaxv2/IA_ytb/claude/youtube-downloader-mac-51ojpa/install.sh | bash
```

Le script télécharge le code, compile l'app **sur votre Mac**, l'installe dans **Applications**, la lance,
et dépose aussi `YT-Downloader.dmg` dans **Téléchargements** (pour l'installer sur un autre Mac).

- La première fois, macOS peut ouvrir une fenêtre « Installer les outils de développement » :
  cliquez **Installer**, attendez la fin, puis **relancez la même commande**.
- Compilée localement, l'app n'affiche pas d'alerte Gatekeeper.

### Autres options

- **Compiler à la main** (macOS 13+, Xcode ou Command Line Tools) :
  ```bash
  git clone -b claude/youtube-downloader-mac-51ojpa https://github.com/powermaxv2/IA_ytb.git
  cd IA_ytb
  ARCHS=native ./scripts/build_app.sh   # ou sans ARCHS pour un binaire universel (nécessite Xcode)
  open dist                             # YT-Downloader.dmg + YT Downloader.app
  ```
- **GitHub Actions** : le workflow « Build macOS app » produit le `.dmg` en artefact
  (et en Release pour un tag `v*`). L'app téléchargée ainsi n'est pas notarisée :
  premier lancement par **clic droit → Ouvrir**, ou `xattr -cr "/Applications/YT Downloader.app"`.

## Mises à jour automatiques

À partir de la version 1.2.0, l'app vérifie GitHub à chaque lancement (désactivable dans les Réglages).
Quand une nouvelle version est publiée, un bandeau propose **Mettre à jour** : l'app télécharge le code,
le compile sur votre Mac (1 à 3 minutes), remplace `YT Downloader.app` et redémarre.
En cas d'échec, l'ancienne version reste en place.

Menu **YT Downloader → Rechercher des mises à jour…** pour vérifier à la main.

**Publier une nouvelle version** : modifier le code, augmenter le numéro dans `VERSION`,
ajouter une section en haut de `RELEASE_NOTES.md`, puis pousser sur la branche
`claude/youtube-downloader-mac-51ojpa`.

## Sécurité

- **Outils vérifiés** : yt-dlp et Deno sont téléchargés depuis leurs pages GitHub officielles et comparés à
  l'empreinte SHA-256 publiée avec la même release ; un fichier non conforme est refusé.
  FFmpeg 6.1.1 (builds evermeet.cx / osxexperts.net, miroir GitHub `eugeneware/ffmpeg-static`) est vérifié
  contre une empreinte SHA-256 **inscrite dans le code de l'app** : tout fichier modifié est refusé.
  Un FFmpeg installé via Homebrew est utilisé en priorité s'il existe.
- **Contrôle d'intégrité** : l'empreinte de chaque outil est enregistrée (`manifest.json`) et recontrôlée à chaque
  lancement ; un fichier modifié est supprimé, réinstallé, et une alerte s'affiche.
- **yt-dlp isolé** : `--ignore-config`, `--no-plugin-dirs`, environnement réduit (pas de `PYTHONPATH`, `DYLD_*`…),
  dossier des outils accessible uniquement à votre compte (`chmod 700`).
- **Entrées** : seuls les liens `http(s)://` sont acceptés.
- **App** : signée avec le *hardened runtime* d'Apple (pas d'injection de bibliothèques).
- **Mises à jour** : code téléchargé en HTTPS pour un commit précis, jamais de retour à une version antérieure.
  La sécurité repose sur votre compte GitHub : activez la validation en deux étapes (2FA) et ne donnez à personne
  l'accès en écriture au dépôt.

Réglages → **Sécurité** affiche l'état de chaque outil et permet de relancer la vérification.

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

## Avertissement et licence

Voir la [page d'accueil du dépôt](../README.md#youtube--usage-personnel) : usage personnel, respect des droits et
des conditions des sites, aucun contournement de DRM. Données personnelles : [PRIVACY.md](../PRIVACY.md). Licence
GPL-3.0 : [LICENSE](../LICENSE).
