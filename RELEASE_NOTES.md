# Notes de version

## 1.3.1
- FFmpeg est désormais vérifié : version 6.1.1 figée, empreinte SHA-256 inscrite dans l'app.
- Un FFmpeg installé sans vérification par une version précédente est automatiquement remplacé.

## 1.3.0
- Sécurité : yt-dlp et Deno sont vérifiés par empreinte SHA-256 officielle avant installation.
- Contrôle d'intégrité à chaque lancement : un outil modifié est supprimé et réinstallé (alerte affichée).
- yt-dlp isolé : aucun fichier de configuration ni plugin externe, environnement nettoyé.
- Mises à jour épinglées sur un commit précis, sans retour possible à une version antérieure.
- App signée avec le runtime renforcé d'Apple ; seuls les liens https:// sont acceptés.
- Nouvelle section « Sécurité » dans les Réglages.

## 1.2.0
- Mise à jour automatique : l'app vérifie GitHub au lancement et se met à jour en un clic.
- Section « Mises à jour » dans les Réglages et menu « Rechercher des mises à jour… ».

## 1.1.0
- Playlists, détection du lien dans le presse-papiers, cookies du navigateur.
- Taille téléchargée affichée et badge sur l'icône du Dock.

## 1.0.0
- Première version : vidéo + son jusqu'en 8K, audio seul, file de téléchargements.
