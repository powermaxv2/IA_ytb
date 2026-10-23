# Ce que Maxime doit faire lui-même pour publier Gulp sur Google Play

Les cases cochées sont faites. Le reste prend environ une soirée, plus quelques jours d'attente de Google.

## Déjà fait

- [x] Branche `play-store` : Gulp sans le Téléchargeur vidéo, sans la mise à jour par GitHub, sans les permissions sensibles
- [x] Bundle `.aab` signé : `/mnt/project-files/play-store/Gulp-play-1.17.0.aab` (empreinte dans `SHA256SUMS.txt`)
- [x] Politique de confidentialité en français et en anglais
- [x] Textes de la fiche, réponses aux formulaires, justification des permissions : `listing.md`
- [x] Icône 512, image de présentation, captures d'écran : `/mnt/project-files/play-store/visuels/`

## À décider avant tout (2 points qui bloquent)

1. **La licence GPL et le dépôt privé.** Gulp est sous GPL-3.0 et embarque FFmpeg (GPL). La GPL oblige à donner le code source
   à toute personne qui reçoit l'app. Le dépôt est privé : ce n'est pas respecté. Trois solutions :
   - rendre public un dépôt qui ne contient **que** la branche `play-store` (sans yt-dlp ni le Téléchargeur) : recommandé, la version personnelle reste privée ;
   - rendre tout le dépôt public (c'est contraire à ta décision d'octobre : le Téléchargeur YouTube serait visible) ;
   - remplacer FFmpeg GPL par une version LGPL (gros travail, à voir plus tard).
2. **Une adresse web publique pour la politique de confidentialité.** Google exige un lien qui s'ouvre sans compte. Un dépôt privé ne marche pas.
   Le plus simple : un site gratuit (GitHub Pages sur le dépôt public de l'étape 1, ou une page Notion publiée, ou Google Sites) avec le texte de `PRIVACY-fr.md`.

## Étapes dans la Play Console

- [ ] 1. Créer un compte développeur : https://play.google.com/console/signup (25 $ une seule fois). Choisir « Personnel » ou « Organisation ».
      Un compte personnel créé après novembre 2023 doit faire un **test fermé avec au moins 12 testeurs pendant 14 jours** avant la mise en production.
      Prévois 12 amis qui s'inscrivent avec leur adresse Gmail.
- [ ] 2. Vérifier ton identité (pièce d'identité, adresse, téléphone) : compte sous 1 à 3 jours.
- [ ] 3. Créer l'application : nom `Gulp : outils photo, PDF, son`, langue par défaut français, type « Application », gratuite.
- [ ] 4. Activer **Play App Signing** (proposé par défaut). Envoyer le bundle `Gulp-play-1.17.0.aab` dans Tests › Test fermé › Créer une version.
- [ ] 5. Fiche principale : coller les textes de `listing.md` ; envoyer icône, image de présentation, captures.
- [ ] 6. Contenu de l'application :
      - Politique de confidentialité : coller l'adresse publique
      - Annonces : non
      - Accès à l'application : tout est accessible
      - Classification du contenu : questionnaire (réponses dans `listing.md`)
      - Public cible : 18 ans et plus
      - Sécurité des données : « aucune donnée collectée ni partagée » (détails dans `listing.md`)
      - Déclarations des autorisations sensibles (appareil photo, micro) si demandées : textes de justification dans `listing.md`
- [ ] 7. Pays de distribution : choisir (France, Belgique, Suisse, Canada…).
- [ ] 8. Envoyer en test fermé, inviter les 12 testeurs, attendre 14 jours, puis demander l'accès à la production.
- [ ] 9. Production : créer la version avec le même bundle (ou un plus récent), envoyer à examen (quelques heures à 7 jours).

## Pour chaque nouvelle version

1. Un thread fusionne les nouveaux outils de la branche normale dans `play-store` (voir `android/PLAY_STORE.md`).
2. Un thread construit le `.aab` signé (le numéro de version monte tout seul avec `android/VERSION`).
3. Tu l'envoies dans la Play Console (Production › Créer une version › envoyer le `.aab`).
