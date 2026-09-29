# YT Downloader pour Android

Version Android (APK) de YT Downloader : télécharge depuis **YouTube** et **Instagram**, avec le son,
dans la qualité de votre choix. Elle vit dans ce dossier `android/` et ne touche pas à l'app macOS.

## Fonctionnalités

- Deux onglets séparés, **YouTube** et **Instagram**, chacun avec son champ de lien (un lien collé dans le
  mauvais onglet est ouvert dans le bon)
- **Partager → YT Downloader** depuis l'app YouTube ou Instagram (ou coller un lien)
- **YouTube** : vidéos jusqu'en 4K / 8K / HDR / 60 fps, audio seul (M4A, MP3, Opus, FLAC), playlists
- **Instagram** : reels, vidéos, carrousels ; **photos**, stories et publications privées des comptes suivis
  avec un compte connecté
- Choix de la qualité en tuiles (la meilleure est marquée **MAX**), formats MP4, MP4 · H.264 (lisible partout) ou MKV
- File d'attente en arrière-plan avec notification de progression, annulation, nouvelle tentative
- Fichiers enregistrés dans la Galerie : **Films / Musique / Images › YT Downloader**
- Interface animée : fond vivant, accent rouge (YouTube) ou violet-rose (Instagram) selon l'onglet,
  tuiles en cascade, barre de progression animée, effet de réussite, vibrations légères

## Installer l'APK

1. Ouvrez la page **Releases** du dépôt sur le téléphone et téléchargez
   `YT-Downloader-Android-arm64-v8a.apk` (presque tous les téléphones récents)
   ou `YT-Downloader-Android-universal.apk` en cas de doute.
2. Ouvrez le fichier, autorisez « Installer des applis inconnues » pour votre navigateur, puis **Installer**.

Les APK sont produits par le workflow GitHub Actions **Build Android app** à chaque modification de `android/`
(artefact du workflow + Release `android-vX.Y.Z`).

### Signature (mises à jour par-dessus l'ancienne version)

Android n'accepte une mise à jour que si elle est signée avec la même clé. Pour une signature permanente,
ajoutez ces secrets au dépôt (Settings → Secrets and variables → Actions) :

| Secret | Valeur |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | le fichier `.jks` encodé en base64 (`base64 -w0 release.jks`) |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `ANDROID_KEY_ALIAS` | alias de la clé |
| `ANDROID_KEY_PASSWORD` | mot de passe de la clé |

Créer une clé : `keytool -genkeypair -v -keystore release.jks -alias ytd -keyalg RSA -keysize 4096 -validity 36500`.
Sans ces secrets, la clé de debug du CI est utilisée (gardée en cache) : si l'installation par-dessus échoue,
désinstallez l'ancienne version puis installez la nouvelle.

## Compiler soi-même

JDK 17 et SDK Android (API 35) requis :

```bash
cd android
./gradlew assembleRelease
ls app/build/outputs/apk/release/
```

## Instagram

La connexion (onglet **Instagram** → **Se connecter à Instagram**) se fait sur instagram.com dans l'app : le mot
de passe n'est jamais lu, seuls les cookies de session sont gardés dans l'espace privé de l'app. Elle est nécessaire
pour les photos, les carrousels complets, les stories et les publications privées des comptes que vous suivez.
Les reels publics fonctionnent souvent sans connexion.

Avec un compte connecté, les publications (y compris privées) sont lues via l'API web d'Instagram : un carrousel
affiche toutes ses photos et vidéos, à cocher avant le téléchargement. L'onglet indique le compte connecté
(@nom) et signale une session expirée ; les liens `instagram.com/share/…` sont suivis jusqu'à la publication.

## Sécurité

- yt-dlp, Python, FFmpeg et QuickJS sont intégrés à l'APK ([youtubedl-android](https://github.com/JunkFood02/youtubedl-android)).
- Mises à jour de yt-dlp depuis sa page GitHub officielle, **vérifiées par SHA-256** (`SHA2-256SUMS`) ;
  empreinte recontrôlée à chaque lancement, un fichier modifié est supprimé.
- yt-dlp isolé : `--ignore-config`, `--no-plugin-dirs` ; seuls les liens `https://` sont acceptés.
- Pas de sauvegarde Android des données de l'app (`allowBackup=false`).

## Structure

```
android/app/src/main/java/com/powermaxv2/ytdownloader/
├── App.kt, MainActivity.kt   démarrage, liens partagés
├── core/                     moteur yt-dlp, file de téléchargements, Instagram, export Galerie
└── ui/                       écrans Compose et composants animés
```

Version Android : fichier `android/VERSION` (indépendant du `VERSION` de l'app macOS).

## Avertissement

Téléchargez uniquement des contenus dont vous détenez les droits ou dont le téléchargement est autorisé,
et respectez les conditions d'utilisation de YouTube et d'Instagram.
