# YT Downloader pour Android

Version Android (APK) de YT Downloader : télécharge depuis **YouTube**, **Instagram**, **X**, **TikTok**,
**Snapchat**, **Facebook** et n'importe quel lien de vidéo, avec le son, dans la qualité de votre choix. Elle vit dans ce dossier `android/` et ne touche pas à l'app macOS.

## Fonctionnalités

- **Gulp est une boîte à outils** (depuis 1.8.0) : au lancement, la page d'accueil liste les outils, dans les trois
  interfaces. Le premier est le **Téléchargeur vidéo** décrit ci-dessous ; les cartes « À venir » réservent la
  place des suivants. Le logo en haut d'un outil (ou le bouton retour) ramène à l'accueil.
- Trois onglets séparés, **YouTube**, **Instagram** et **Autres**, chacun avec son champ de lien (un lien collé
  dans le mauvais onglet est ouvert dans le bon) ; les **Réglages** s'ouvrent avec la roue crantée en haut à droite
  de chaque écran
- **Partager → YT Downloader** depuis l'app YouTube, Instagram, X, TikTok… (ou coller un lien)
- **YouTube** : vidéos jusqu'en 4K / 8K / HDR / 60 fps, audio seul (M4A, MP3, Opus, FLAC), playlists
- **Instagram** : reels, vidéos, carrousels et **photos** des comptes publics, sans compte (l'app ne se connecte
  pas à Instagram)
- **Autres** : vidéos publiques de **X**, **TikTok**, **Snapchat** (Spotlight) et **Facebook**, et tout lien de vidéo d'un site
  pris en charge par yt-dlp ou d'un fichier vidéo direct (voir ci-dessous)
- Choix de la qualité en tuiles (la meilleure est marquée **MAX**), formats MP4, MP4 · H.264 (lisible partout) ou MKV
- File d'attente en arrière-plan avec notification de progression, annulation, nouvelle tentative
- Fichiers enregistrés dans la Galerie : **Films / Musique / Images › YT Downloader**
- Interface sombre et lisible (depuis 1.5.0) : un gros bouton **Coller le lien**, les étapes « Comment faire » sur
  chaque onglet, des tuiles de qualité, des réglages en liste ; accent rouge (YouTube), violet-rose (Instagram) ou
  cyan-indigo (Autres) selon l'onglet, animations discrètes et vibrations légères
- **Trois interfaces au choix** dans **Réglages › Interface**, appliquées tout de suite : **Classique** (sombre, accent
  par onglet), **Papier** (clair, typographie magazine) et **Terminal** (console vert fluo). Polices embarquées sous
  licence libre OFL : Bricolage Grotesque, JetBrains Mono et Archivo Black

## Installer l'APK

1. Ouvrez la page **Releases** du dépôt sur le téléphone et téléchargez
   `YT-Downloader-Android-arm64-v8a.apk` (presque tous les téléphones récents)
   ou `YT-Downloader-Android-universal.apk` en cas de doute.
2. Ouvrez le fichier, autorisez « Installer des applis inconnues » pour votre navigateur, puis **Installer**.

Les APK sont produits par le workflow GitHub Actions **Build Android app** : à chaque modification de `android/`
ils sont gardés en artefact du workflow, et un tag `android-vX.Y.Z` publie la Release (voir « Publier une version »).

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
Les secrets ne servent qu'aux tags de version : les builds de branche et de pull request sont toujours signés
avec la clé de debug du CI (gardée en cache), pour que la clé de release ne soit jamais exposée à du code non
publié. Sans ces secrets, un tag de version échoue : une release est toujours signée avec la clé de release. Facultatif : la variable de dépôt
`ANDROID_SIGNER_SHA256` (empreinte SHA-256 du certificat des APK déjà installés) fait échouer une release signée
avec une autre clé, que les téléphones refuseraient.

Depuis la version qui suit la 1.5.1, l'app est signée avec une nouvelle clé (certificat SHA-256
`3c68a495…96f4f5`) : la mise à jour depuis la 1.5.1 ou une version plus ancienne demande de désinstaller l'app
une fois, puis d'installer le nouvel APK. Les suivantes s'installent ensuite par-dessus.

## Mises à jour automatiques (depuis 1.3.0)

L'app cherche les nouvelles versions dans les **Releases** GitHub nommées `android-vX.Y.Z` : à l'ouverture
(au plus toutes les 5 min) et toutes les 6 h en arrière-plan. Une nouvelle version s'annonce par une notification
et par un bandeau en haut des écrans ; **Mettre à jour** télécharge l'APK du téléphone
(`YT-Downloader-X.Y.Z-<abi>.apk`, sinon `-universal`), vérifie son empreinte SHA-256 et qu'il est signé avec la même
clé que l'app installée, puis l'installe avec l'installateur d'Android. La première fois, Android demande d'autoriser
YT Downloader à installer des applis ; à partir d'Android 12, les mises à jour suivantes n'ont souvent plus besoin
de confirmation. Réglages → **Mises à jour de l'app** : recherche manuelle et notification activable.

Publier une version : augmenter `android/VERSION`, écrire les notes dans `android/release-notes/X.Y.Z.md`
(en français, affichées dans l'app et la notification), commiter, pousser, puis poser le tag :

```bash
git tag android-v1.5.2 && git push origin android-v1.5.2
```

Le workflow vérifie que le tag correspond à `android/VERSION`, compile avec la clé de release, contrôle la
signature et publie la release `android-vX.Y.Z` avec les APK `YT-Downloader-X.Y.Z-<abi>.apk` que l'app reconnaît.

Sans GitHub Actions, `./scripts/publish-release.sh notes.md` fait la même chose depuis une machine qui a le SDK
Android, `gh` et la clé des APK déjà installés.

## Compiler soi-même

JDK 17 et SDK Android (API 35) requis :

```bash
cd android
./gradlew assembleRelease
ls app/build/outputs/apk/release/
```

## Instagram

Depuis la 1.4.0, l'app **ne se connecte plus à Instagram** : Instagram a verrouillé un compte dont l'app utilisait la
session. Les publications publiques sont lues comme par un visiteur non connecté, via yt-dlp : reels, vidéos, photos
et carrousels (photos et vidéos à cocher avant le téléchargement). Les stories et les publications des comptes privés
ne sont pas accessibles. La session enregistrée par l'écran de connexion des versions précédentes (cookies du
navigateur intégré) est effacée du téléphone au premier lancement de la 1.4.0.

Instagram limite parfois les visiteurs non connectés : en cas d'erreur, mettre yt-dlp à jour (Réglages) puis
réessayer plus tard. Les liens `instagram.com/share/…` sont suivis jusqu'à la publication.

## Autres réseaux et liens de vidéos

L'onglet **Autres** accepte les liens `x.com` / `twitter.com`, `tiktok.com` (y compris `vm.tiktok.com`),
`snapchat.com/spotlight/…`, `facebook.com` (vidéos, reels, liens de partage `facebook.com/share/…` et `fb.watch`) et
tout autre lien `https://` : yt-dlp cherche la vidéo dans la page (des centaines de sites) ou télécharge directement
un fichier `.mp4`, `.webm`… Aucun compte n'est demandé.

Seules les qualités qui ont du son sont proposées : sur Facebook, les flux vidéo sans son sont écartés et la vidéo est
téléchargée en **Max** (souvent 720p ou 1080p). Les compteurs que Facebook ajoute devant les titres
(« 9.8K views · 341 reactions | … ») sont retirés du titre et du nom du fichier.

Limites :

- **BeReal** : l'app ne publie aucun lien public vers une publication (`bere.al/<nom>` ne mène qu'à un profil) et
  yt-dlp n'a pas d'extracteur BeReal. Un lien BeReal affiche donc une explication au lieu de télécharger.
- **Facebook** : seules les vidéos publiques sont lues ; celles réservées aux amis ou à un groupe privé, et celles que
  Facebook ne montre qu'aux personnes connectées, affichent une explication (pas de connexion Facebook dans l'app).
- **X** : seules les vidéos (et GIF) sont téléchargées, pas les photos ; les posts protégés ou réservés aux adultes
  demandent un compte, non pris en charge.
- **TikTok** : les diaporamas de photos ne sont pas pris en charge.
- **Snapchat** : seuls les liens Spotlight publics fonctionnent (pas les Stories ni les Snaps privés) ; la miniature
  n'est pas intégrée au fichier (yt-dlp refuse l'extension de l'image Snapchat, l'app relance donc sans elle).
- Un lien direct vers un fichier ne donne pas de choix de qualité : le fichier est téléchargé tel quel (option
  **Max**).

## Sécurité

- yt-dlp, Python, FFmpeg et QuickJS sont intégrés à l'APK ([youtubedl-android](https://github.com/JunkFood02/youtubedl-android)).
- Mises à jour de yt-dlp depuis sa page GitHub officielle, **vérifiées par SHA-256** (`SHA2-256SUMS`) ;
  empreinte recontrôlée à chaque lancement, un fichier modifié est supprimé.
- yt-dlp isolé : `--ignore-config`, `--no-plugin-dirs` ; seuls les liens `https://` sont acceptés.
- Aucune connexion à un compte (Instagram ou autre) : seuls les contenus publics sont lus.
- Pas de sauvegarde Android des données de l'app (`allowBackup=false`).
- Mise à jour de l'app : APK venant uniquement des releases de ce dépôt, empreinte SHA-256 annoncée par GitHub
  obligatoire, même nom de paquet, version plus récente et même certificat de signature que l'app installée.
  Seule la notification de Gulp (alias d'activité non exporté) peut lancer une mise à jour.

## Structure

```
android/app/src/main/java/com/powermaxv2/ytdownloader/
├── App.kt, MainActivity.kt   démarrage, liens partagés
├── core/                     moteur yt-dlp, file de téléchargements, Instagram, mises à jour, export Galerie
└── ui/                       écrans Compose et composants animés
    ├── hub/                  accueil et registre des outils (Tools.kt)
    └── skin/                 les trois interfaces (classic/, paper/, terminal/)
```

Ajouter un outil : une entrée dans `ToolRegistry.tools` (`ui/hub/Tools.kt`) avec son écran composable ; une
entrée sans écran s'affiche comme carte « À venir ».

Version Android : fichier `android/VERSION` (indépendant du `VERSION` de l'app macOS).

## Avertissement

Téléchargez uniquement des contenus dont vous détenez les droits ou dont le téléchargement est autorisé,
et respectez les conditions d'utilisation de YouTube, d'Instagram et des autres sites.
