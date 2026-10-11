# Composants tiers

Gulp (Android) et YT Downloader (macOS) s'appuient sur les logiciels libres ci-dessous. Ce fichier liste leurs
auteurs et leurs licences, comme ces licences le demandent. Les textes complets sont dans les liens indiqués
(et dans [`LICENSE`](LICENSE) pour la GPL-3.0, [`LICENSES/OFL-1.1.txt`](LICENSES/OFL-1.1.txt) pour les polices).

## Intégrés à l'APK Android

| Composant | Version | Licence | Source |
|---|---|---|---|
| youtubedl-android (`library`, `ffmpeg`) | 0.18.1 | GPL-3.0 | https://github.com/JunkFood02/youtubedl-android |
| yt-dlp | inclus dans youtubedl-android, mis à jour depuis GitHub | Unlicense | https://github.com/yt-dlp/yt-dlp |
| Python | 3.8 (inclus dans youtubedl-android) | Python Software Foundation License | https://www.python.org/downloads/source/ |
| FFmpeg | build Termux (inclus dans youtubedl-android), compilé avec `--enable-gpl --enable-version3` | GPL-3.0 | https://ffmpeg.org/download.html · recette : https://github.com/termux/termux-packages/tree/master/packages/ffmpeg |
| x264, x265 (via FFmpeg) | build Termux | GPL-2.0-or-later | https://code.videolan.org/videolan/x264 · https://bitbucket.org/multicoreware/x265_git |
| QuickJS | inclus dans youtubedl-android | MIT | https://bellard.org/quickjs/ |
| Jetpack Compose (BOM 2024.12.01), AndroidX Core, Activity, Lifecycle | voir `android/app/build.gradle.kts` | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Kotlin standard library, kotlinx.coroutines 1.9.0 | | Apache-2.0 | https://github.com/JetBrains/kotlin · https://github.com/Kotlin/kotlinx.coroutines |
| Coil | 2.7.0 | Apache-2.0 | https://github.com/coil-kt/coil |
| commonmark-java | 0.24.0 | BSD-2-Clause, texte : [`LICENSES/commonmark-java-BSD-2-Clause.txt`](LICENSES/commonmark-java-BSD-2-Clause.txt) | Copyright (c) 2015, Atlassian Pty Ltd · https://github.com/commonmark/commonmark-java |
| ZXing (`core`) | 3.5.3 | Apache-2.0 | Copyright ZXing authors · https://github.com/zxing/zxing |
| AndroidX CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`) | 1.4.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/camera |
| Bricolage Grotesque | | SIL OFL 1.1 | Copyright 2022 The Bricolage Grotesque Project Authors (https://github.com/ateliertriay/bricolage) |
| JetBrains Mono | | SIL OFL 1.1 | Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono) |
| Archivo Black | | SIL OFL 1.1 | Copyright 2017 The Archivo Black Project Authors (https://github.com/Omnibus-Type/ArchivoBlack) |

### Code source des binaires embarqués

L'APK contient des binaires compilés (Python, FFmpeg, x264, x265, QuickJS) fournis par youtubedl-android.
Leur code source correspondant est disponible auprès de leurs projets d'origine, aux adresses ci-dessus, et les
instructions de compilation sont dans le dépôt youtubedl-android (`BUILD_PYTHON.md`, `BUILD_FFMPEG.md`).
Si vous ne parvenez pas à obtenir le code source correspondant à une version publiée de Gulp, ouvrez une issue
sur ce dépôt : il vous sera fourni, pendant au moins trois ans après la dernière publication de cette version.

## Téléchargés par l'app macOS sur votre Mac

L'app macOS n'embarque aucun de ces outils : elle les télécharge au premier lancement depuis leurs sources
officielles et vérifie leur empreinte SHA-256.

| Composant | Licence | Source |
|---|---|---|
| yt-dlp (`yt-dlp_macos`) | Unlicense (les composants qu'il inclut gardent leurs licences) | https://github.com/yt-dlp/yt-dlp |
| FFmpeg / ffprobe 6.1.1 (builds evermeet.cx et osxexperts.net, miroir `eugeneware/ffmpeg-static`) | GPL | https://ffmpeg.org · https://github.com/eugeneware/ffmpeg-static |
| Deno | MIT | https://github.com/denoland/deno |

## Outils de compilation présents dans le dépôt

| Composant | Licence |
|---|---|
| Gradle Wrapper (`android/gradle/wrapper/`) | Apache-2.0 |

## Marques

YouTube est une marque de Google LLC. Instagram et Facebook sont des marques de Meta Platforms, Inc.
X est une marque de X Corp. TikTok est une marque de ByteDance Ltd. Snapchat est une marque de Snap Inc.
BeReal est une marque de BeReal SAS. Ce projet n'est ni affilié à ces sociétés, ni approuvé ou soutenu par elles.
