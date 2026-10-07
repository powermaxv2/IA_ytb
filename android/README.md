# YT Downloader pour Android

Version Android (APK) de YT Downloader : télécharge depuis **YouTube**, **Instagram**, **X**, **TikTok**,
**Snapchat**, **Facebook** et n'importe quel lien de vidéo, avec le son, dans la qualité de votre choix. Elle vit dans ce dossier `android/` et ne touche pas à l'app macOS.

## Fonctionnalités

- **Gulp est une boîte à outils** (depuis 1.8.0) : au lancement, la page d'accueil liste les outils, dans les trois
  interfaces. Le premier est le **Téléchargeur vidéo** décrit ci-dessous ; depuis 1.9.0 s'y ajoutent le
  **Convertisseur** et l'**Aperçu** (section « Convertisseur et Aperçu ») ; les cartes « À venir » réservent la
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

## Convertisseur et Aperçu (depuis 1.9.0)

Les deux outils travaillent **hors ligne, sur le téléphone** : aucun fichier n'est envoyé nulle part et aucune
bibliothèque réseau n'est utilisée. On y arrive par la page d'accueil, par **Partager → Convertir avec Gulp** ou
**Aperçu avec Gulp**, ou en **ouvrant** un fichier avec Gulp depuis un gestionnaire de fichiers (seuls les liens
`content://` sont acceptés).

### Convertisseur

| Depuis | Vers |
|---|---|
| Vidéo | MP4 (H.264), MKV (H.265), WebM (VP9), MOV, AVI, GIF animé muet (30 s au plus), ou le son seul |
| Son | MP3, M4A, Opus, OGG, FLAC, WAV |
| Photo | JPG, PNG, WebP, BMP, TIFF ; plusieurs photos (jusqu'à **24**) en un seul **PDF** A4 |
| PDF | une image JPG ou PNG par page (200 pages au plus) |
| Markdown | page HTML autonome |
| CSV / JSON | JSON (liste d'objets) / CSV, dans les deux sens |

La vidéo se règle en résolution (originale, 2160p à 480p) et en qualité (haute, moyenne, légère) ; le son en débit
(320, 192 ou 128 kbit/s). La conversion vidéo et son passe par FFmpeg, déjà intégré à l'APK. La progression
s'affiche en direct, **Annuler** arrête la copie du fichier ou la conversion et efface le fichier partiel. Le résultat est enregistré dans la
Galerie (Films, Musique, Images › Gulp) ou, pour un PDF, un HTML, un JSON ou un CSV, dans
Téléchargements › Gulp ; on peut ensuite l'ouvrir, le partager ou le voir dans l'Aperçu.

Limites :

- **Pas de Word, Excel ni PowerPoint** vers ou depuis PDF : cela demande un moteur de mise en page que l'app
  n'embarque pas ; ces fichiers, ainsi que les EPUB, les archives ZIP et les textes bruts, affichent une explication.
- **Pas de contenu protégé par DRM**.
- **Pas de photos RAW** d'appareil (DNG, CR2, NEF…).
- Un PDF protégé par mot de passe n'est pas converti.
- Une conversion longue peut être **arrêtée par Android si Gulp passe à l'arrière-plan** : gardez l'app ouverte
  jusqu'à la fin.
- Tailles maximales : vidéo ou son 2 Gio, photo 64 Mio, PDF 200 Mio, texte (CSV, JSON, Markdown) 20 Mio ; un
  tableau CSV ou JSON compte au plus 2 000 colonnes et 5 millions de cellules.
- Le son et la vidéo sont reconnus **d'après le contenu du fichier**, pas d'après son nom ni son type : une liste de
  lecture (`.m3u`, `.m3u8`) ou un texte présenté comme du son est refusé.

### Aperçu

Montre un fichier sans le modifier (un ZIP, ou un PDF qu'Android ne sait pas lire en place, est copié dans un
dossier temporaire effacé aussitôt après) : photo (zoom, GIF et WebP animés sous Android 9+),
vidéo et son (avec forme d'onde sur les premières minutes : 10 au plus, souvent moins selon le format), PDF page par page, texte, code avec coloration
(Kotlin, Java, JavaScript, TypeScript, Python, C, Shell, XML/HTML, CSS, JSON, YAML), JSON indenté, Markdown, tableau
CSV et contenu d'une archive **ZIP**. Un ZIP est **seulement listé, jamais extrait** ; les noms dangereux
(`..`, chemins absolus, barres obliques inverses) sont signalés. Android 14 et plus refuse d'ouvrir une archive qui
contient `..` ou un chemin absolu : Gulp lit alors lui-même son sommaire (les noms et les tailles, sans rien
décompresser) et l'indique en tête de liste. La fiche du fichier donne sa nature détectée
d'après son contenu (avec un avertissement si l'extension ne correspond pas), un aperçu hexadécimal et son
empreinte SHA-256. Le bouton **Convertir** envoie le fichier vers le Convertisseur quand c'est possible.

Limites : les textes sont lus sur le premier Mo seulement, le CSV sur ses 500 premières lignes ; les liens d'un
Markdown ne sont pas cliquables, et ni les tableaux ni le barré ne sont rendus ; la lecture vidéo dépend des
codecs du téléphone (un message l'indique sinon) ; un ZIP de plus de 512 Mio n'est pas listé du tout, et seules les
5 000 premières entrées d'un ZIP plus long le sont ; un JSON que l'indentation rendrait plus de quatre fois plus long
s'affiche tel quel.

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

- Convertisseur et Aperçu : fichiers acceptés uniquement via `content://`, copiés dans un dossier de travail
  temporaire effacé après usage et au lancement de l'app ; tailles limitées, images et pages PDF décodées en taille
  bornée, FFmpeg lancé sans shell avec des chemins internes seulement, sur des fichiers dont le contenu est du son ou
  de la vidéo (jamais un texte ni une liste de lecture) et avec une liste fermée de formats d'entrée
  (`-format_whitelist`), ZIP jamais extrait ; les noms de fichiers sont nettoyés (y compris des caractères qui
  inversent le sens d'affichage).
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
│   ├── files/                lecture sûre des fichiers reçus, dossier de travail, détection de type
│   ├── convert/              Convertisseur (FFmpeg, images, PDF, Markdown, CSV/JSON)
│   └── preview/              Aperçu (texte, coloration, ZIP, forme d'onde, PDF)
└── ui/                       écrans Compose et composants animés
    ├── hub/                  accueil et registre des outils (Tools.kt)
    ├── tools/                écrans du Convertisseur et de l'Aperçu
    └── skin/                 les trois interfaces (classic/, paper/, terminal/)
```

Ajouter un outil : une entrée dans `ToolRegistry.tools` (`ui/hub/Tools.kt`) avec son écran composable ; une
entrée sans écran s'affiche comme carte « À venir ».

Version Android : fichier `android/VERSION` (indépendant du `VERSION` de l'app macOS).

## Avertissement

Téléchargez uniquement des contenus dont vous détenez les droits ou dont le téléchargement est autorisé,
et respectez les conditions d'utilisation de YouTube, d'Instagram et des autres sites.
