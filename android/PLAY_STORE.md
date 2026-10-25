# Version Google Play de Gulp (branche `play-store`)

Cette branche est la version de Gulp publiée sur Google Play. Elle est la même app que la branche normale
(`claude/youtube-downloader-mac-51ojpa`), **sans le Téléchargeur vidéo** : Google Play refuse les apps qui
téléchargent des vidéos YouTube.

## Ce qui est différent de la branche normale

| Sujet | Branche normale | Branche `play-store` |
|---|---|---|
| Téléchargeur vidéo (yt-dlp, Python, QuickJS) | Oui | Retiré (code, écrans, runtimes natifs, script yt-dlp) |
| Outils | 44 | 42 (sans Téléchargeur ni Doublons photos) |
| `applicationId` | `com.powermaxv2.ytdownloader` | `com.powermaxv2.gulp` (les deux apps peuvent cohabiter) |
| Mise à jour par GitHub | Oui | Non (Google Play met à jour) : plus de `REQUEST_INSTALL_PACKAGES` |
| Service de téléchargement, notifications de téléchargement | Oui | Retirés |
| Lecture de toute la galerie (`READ_MEDIA_IMAGES`) | Oui (Doublons photos) | Non |
| Alarmes exactes (`USE_EXACT_ALARM`) | Oui | Non : le minuteur utilise `setAlarmClock`, exact sans permission |
| Format de livraison | 1 APK par processeur + universel | 1 bundle `.aab` (Google Play découpe par appareil) |
| Réglages | Téléchargeur, yt-dlp, mises à jour | Apparence, vibrations, confidentialité, à propos |

FFmpeg (outils Convertisseur, Compresseur, Découpeur…) reste, via la bibliothèque `youtubedl-android:ffmpeg`.
Cette bibliothèque embarque aussi le lanceur de yt-dlp : il est exclu à la compilation
(`jniLibs.excludes` pour Python et QuickJS, `res/raw/keep.xml` pour le script).
Vérifiez après un build que le bundle ne contient plus `ytdlp`, `libpython` ni `libqjs` :

```
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -iE "ytdlp|python|qjs"   # ne doit rien afficher
```

## Construire le bundle signé

```
cd android
export ANDROID_HOME=/opt/android-sdk
export ANDROID_KEYSTORE_PATH=… ANDROID_KEYSTORE_PASSWORD=… ANDROID_KEY_ALIAS=… ANDROID_KEY_PASSWORD=…
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

La clé est la clé d'envoi (« upload key ») de Google Play. Avec Play App Signing, Google garde la clé finale.
Le code de version (`versionCode`) vient de `android/VERSION` comme sur la branche normale : il doit augmenter à chaque envoi.

## Faire arriver un nouvel outil de la branche normale

```
git checkout play-store
git merge claude/youtube-downloader-mac-51ojpa
```

Les conflits attendus sont toujours les mêmes : `ui/hub/Tools.kt` (l'entrée du Téléchargeur), `ui/SettingsScreen.kt`,
`ui/MainViewModel.kt`, `App.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`. Gardez la version `play-store` de ces fichiers
et ajoutez seulement ce que le nouvel outil apporte (entrée du registre, catégorie, contrôleur).
Un outil qui demande une permission sensible (lecture de toute la galerie, fichiers, SMS, appels, localisation en arrière-plan…)
doit être examiné avant d'arriver ici : voir `docs/play-store/listing.md`.

## Documents pour la fiche

Dans `docs/play-store/` : politique de confidentialité (FR, EN), textes de la fiche, réponses aux formulaires, checklist.

## Gulp Plus (version monétisable, simulée)

- 14 outils sont « Plus » (`PlusCatalog.premiumIds`), les 28 autres restent gratuits. Toucher un outil Plus ouvre le pop-up
  (paiement unique de 2 €, `PlusProduct`) ; un bouton cœur ouvre le pop-up de don (1, 2, 5 ou 10 €).
- Aucun vrai paiement : `SimulatedBilling` attend 1,7 s puis réussit. Pour brancher Google Play Billing, écrire un `PlayBilling`
  qui implémente `BillingGateway` (produit non consommable `gulp_plus`, dons consommables) et le donner à `MainViewModel.billing`.
- L'état est dans `Prefs.plusUnlocked` / `donatedCents`. Avant la publication, remplacer la simulation (Google exige son propre
  système de facturation pour les achats dans l'app) et retirer le texte « Mode démonstration ».
