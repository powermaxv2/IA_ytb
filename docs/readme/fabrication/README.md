# Fabrication des visuels du README

Tout part des vraies captures de l'app, refaites par le workflow **Build Android app** (tests Robolectric +
Roborazzi) et publiées sur la branche `screenshots/<commit>` à chaque push.

```bash
git fetch origin screenshots/<commit> && mkdir captures && git archive FETCH_HEAD | tar x -C captures
pip install fonttools pillow            # gifsicle (facultatif) rend les GIF plus légers
python3 docs/readme/fabrication/hero.py captures   # -> docs/readme/gulp-banniere.svg
python3 docs/readme/fabrication/gifs.py captures   # -> docs/readme/gulp-trois-interfaces.gif, docs/readme/outils/*.gif
```

`gifs.py captures gomme` ne refait que le GIF d'un outil. Les textes sont dessinés avec les polices de l'app
(`android/app/src/main/res/font/`), vectorisées dans la bannière pour ne dépendre d'aucune police externe.
