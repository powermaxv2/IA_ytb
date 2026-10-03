# Sécurité

## Signaler une vulnérabilité

Merci de **ne pas** ouvrir d'issue publique pour une faille de sécurité. Utilisez le signalement privé de GitHub :
onglet **Security** du dépôt → **Report a vulnerability**
(https://github.com/powermaxv2/IA_ytb/security/advisories/new).

Indiquez la version concernée (Android : Réglages ; macOS : menu de l'app), les étapes pour reproduire
le problème et son impact. Ce projet est maintenu bénévolement : un accusé de réception est envoyé dès que
possible, et un correctif publié en priorité pour les failles qui touchent les mises à jour ou l'intégrité des
outils.

## Versions prises en charge

Seule la dernière version publiée de chaque app reçoit des correctifs :

| App | Versions |
|---|---|
| Gulp (Android) | dernière release `android-vX.Y.Z` |
| YT Downloader (macOS) | dernière version de la branche principale |

## Ce que les apps font pour se protéger

- **Mises à jour Android** : l'APK est téléchargé en HTTPS depuis les Releases GitHub de ce dépôt ; son empreinte
  SHA-256 est vérifiée, et il doit être signé avec le même certificat que l'app installée.
- **Mises à jour macOS** : le code est téléchargé en HTTPS pour un commit précis, sans retour possible à une version
  antérieure, puis compilé sur le Mac.
- **Outils** : yt-dlp et Deno sont vérifiés par l'empreinte SHA-256 publiée avec leur release ; FFmpeg (macOS) par
  une empreinte inscrite dans le code. Les empreintes sont recontrôlées à chaque lancement.
- **yt-dlp isolé** : `--ignore-config`, `--no-plugin-dirs`, seuls les liens `https://` sont acceptés.
- **Clé de signature Android** : elle n'est jamais dans le dépôt, seulement dans les secrets GitHub Actions.

## Hors du champ

- Le contenu des sites téléchargés et les changements de ces sites qui empêchent un téléchargement (ce sont des
  bugs, pas des failles : ouvrez une issue normale).
- Les failles de yt-dlp, FFmpeg, Python ou Deno eux-mêmes : signalez-les à leurs projets ; elles seront intégrées
  ici dès qu'un correctif amont existe.
