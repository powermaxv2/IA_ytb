# Politique de confidentialité

Dernière mise à jour : 3 octobre 2026

Gulp (Android) et YT Downloader (macOS) ne collectent aucune donnée personnelle pour le compte de leur auteur.
L'auteur n'exploite aucun serveur pour ces apps : il ne reçoit ni vos liens, ni vos fichiers, ni votre adresse IP.

## Ce qui ne sort jamais de votre appareil

- Vos réglages et la file de téléchargements.
- Les fichiers téléchargés (Galerie sur Android, dossier choisi sur macOS).
- Aucune mesure d'audience, aucune publicité, aucun rapport de plantage, aucun compte.
- Android : la sauvegarde automatique des données de l'app est désactivée.

## Connexions faites par l'app

| Destination | Pourquoi | Ce que reçoit le service |
|---|---|---|
| `api.github.com`, `github.com` | Vérifier et télécharger les mises à jour de l'app (Android : à l'ouverture et toutes les 6 h ; macOS : au lancement, désactivable dans les Réglages) | Votre adresse IP et l'identifiant technique de l'app (User-Agent) |
| `github.com` | Installer et mettre à jour yt-dlp (et, sur macOS, FFmpeg et Deno) | Votre adresse IP |
| Le site de la vidéo (YouTube, Instagram, X, TikTok…) | Analyser et télécharger le lien que vous avez fourni | Votre adresse IP et le lien demandé, comme si vous ouvriez la page |

Ces services traitent ces informations selon leurs propres politiques de confidentialité
(par exemple la [déclaration de confidentialité de GitHub](https://docs.github.com/fr/site-policy/privacy-policies/github-general-privacy-statement)).

## Cookies du navigateur (macOS uniquement)

Une option des Réglages, désactivée par défaut, permet à l'app macOS de lire les cookies d'un navigateur installé
sur votre Mac pour les transmettre au site de la vidéo. Ces cookies restent sur votre Mac et ne sont envoyés qu'au
site auquel ils appartiennent. Vous pouvez désactiver l'option à tout moment.

## Permissions Android

| Permission | Usage |
|---|---|
| Internet, état du réseau | Télécharger les vidéos et les mises à jour |
| Notifications | Progression des téléchargements, nouvelle version disponible |
| Service au premier plan | Continuer un téléchargement quand l'app est en arrière-plan |
| Installation de paquets | Installer les mises à jour de Gulp (Android vous demande l'autorisation) |
| Démarrage de l'appareil | Reprendre la vérification périodique des mises à jour |
| Stockage (Android 9 et antérieur uniquement) | Enregistrer les fichiers dans les dossiers publics |

## Vos droits

Comme l'auteur ne détient aucune donnée vous concernant, il n'a rien à consulter, corriger ou effacer. Pour
supprimer toutes les données locales, désinstallez l'app (Android) ou supprimez
`~/Library/Application Support/YTDownloader` (macOS).

## Changements

Si une future version ajoute un traitement de données (mesure d'audience, rapport de plantage, serveur…), cette
page sera mise à jour avant la publication de cette version, et votre consentement sera demandé lorsque la loi
l'exige.

## Contact

Ouvrez une issue sur https://github.com/powermaxv2/IA_ytb/issues.
