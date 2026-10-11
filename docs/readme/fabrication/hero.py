"""Bannière animée du README : le logo « La Gorgée » avale des fichiers, les outils défilent en dessous.

    python3 docs/readme/fabrication/hero.py <dossier des captures>   ->  docs/readme/gulp-banniere.svg
"""
import base64
import io
import os
import re
import sys

from PIL import Image

from textpath import path, width

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "gulp-banniere.svg")

W, H = 1280, 500
ICON_X, ICON_Y, ICON = 92, 96, 250          # tuile du logo
CX = ICON_X + ICON / 2                        # axe de chute
TOP = ICON_Y + 136 * ICON / 512               # haut du triangle « play » : là où les fichiers disparaissent
CYCLE = 2.8                                   # même rythme que l'icône animée de l'app

TOOLS = ["Téléchargeur vidéo", "Convertisseur", "Aperçu", "Vu, répondu", "Compresseur",
         "QR code", "Scanner", "Découpeur", "Gomme", "Assembleur PDF"]

# Mot « gulp » de l'identité (Bricolage Grotesque 800 vectorisé), repris du logotype validé.
WORDMARK = re.search(r'<path transform="translate\(4,12\)" d="([^"]+)"',
                     open(os.path.join(HERE, "gulp-mot-seul.svg")).read()).group(1)

# Ce que Gulp avale : vidéo, son, PDF, photo, discussion. Dessinés autour de (0, 0), 48 px.
ITEMS = [
    '<rect x="-24" y="-24" width="48" height="48" rx="13" fill="#FF3B4E"/>'
    '<path d="M-7 -11 L12 0 L-7 11 Z" fill="#fff" stroke="#fff" stroke-width="4" stroke-linejoin="round"/>',
    '<rect x="-24" y="-24" width="48" height="48" rx="13" fill="#7C5CFF"/>'
    '<path d="M-4 9 V-12 L11 -15 V5" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>'
    '<circle cx="-8" cy="10" r="5" fill="#fff"/><circle cx="7" cy="6" r="5" fill="#fff"/>',
    '<path d="M-18 -24 H8 L18 -14 V24 H-18 Z" fill="#F6F2F0"/><path d="M8 -24 V-14 H18" fill="#D9CCC8"/>'
    '<rect x="-12" y="-3" width="24" height="12" rx="3" fill="#FF3B4E"/>'
    '<path d="M-12 -12 H4 M-12 16 H10" stroke="#BFAFAA" stroke-width="3" stroke-linecap="round"/>',
    '<rect x="-24" y="-24" width="48" height="48" rx="13" fill="#1FA971"/>'
    '<circle cx="8" cy="-8" r="5" fill="#FFE27A"/><path d="M-17 16 L-5 0 L4 10 L9 5 L17 16 Z" fill="#fff"/>',
    '<rect x="-24" y="-24" width="48" height="48" rx="13" fill="#25D366"/>'
    '<path d="M-13 1 L-7 7 L4 -5 M-1 7 L1 9 L13 -5" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>',
]


def falling_items():
    n = len(ITEMS)
    total = CYCLE * n
    css, svg = [], []
    for i, item in enumerate(ITEMS):
        start = i * CYCLE
        # pourcentages du cycle complet : apparition, arrivée dans la bouche (au moment où elle s'ouvre), disparition
        p = lambda t: f"{(start + t) / total * 100:.2f}%"
        drift = (-38, 30, -22, 42, -30)[i]
        rot = (-18, 14, -10, 22, -14)[i]
        css.append(
            f"@keyframes fall{i}{{0%,{p(0.0)}{{opacity:0;transform:translate({CX + drift}px,-60px) rotate({rot}deg) scale(1)}}"
            f"{p(0.12)}{{opacity:1;transform:translate({CX + drift}px,-30px) rotate({rot}deg) scale(1.25);animation-timing-function:cubic-bezier(.55,0,.9,.5)}}"
            f"{p(1.10)}{{opacity:1;transform:translate({CX}px,{TOP + 6}px) rotate(0deg) scale(.8)}}"
            f"{p(1.22)},100%{{opacity:0;transform:translate({CX}px,{TOP + 22}px) rotate(0deg) scale(.5)}}}}"
            f".fall{i}{{animation:fall{i} {total}s linear infinite}}"
        )
        svg.append(f'<g class="fall{i}" opacity="0">{item}</g>')
    return "".join(css), "".join(svg)


def marquee():
    font, size, pad, gap = "bricolage_semibold", 19, 20, 14
    chips, x = [], 0.0
    for name in TOOLS:
        w = width(name, font, size) + 2 * pad + 22
        chips.append(
            f'<g transform="translate({x:.1f},0)">'
            f'<rect width="{w:.1f}" height="44" rx="22" fill="#21161A" stroke="#3A272C"/>'
            f'<circle cx="{pad + 4}" cy="22" r="5" fill="url(#brand)"/>'
            f'<path d="{path(name, font, size, pad + 18, 28.5)}" fill="#EDE3E0"/></g>'
        )
        x += w + gap
    row = "".join(chips)
    return x, f'<g class="marquee">{row}<g transform="translate({x:.1f},0)">{row}</g></g>'


def phones(shots):
    """Les trois interfaces, en téléphones qui flottent à droite."""
    out, css = [], []
    spots = [("paper", 1000, 70, -9, 0.0), ("terminal", 1150, 92, 8, 1.4), ("classic", 1062, 40, -1, 0.7)]
    for skin, x, y, rot, delay in spots:
        im = Image.open(os.path.join(shots, f"{skin}_home.png")).convert("RGB")
        im = im.crop((0, 0, im.width, int(im.width * 2.05))).resize((260, 533), Image.LANCZOS)
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=80, optimize=True, progressive=True)
        data = base64.b64encode(buf.getvalue()).decode()
        w, h = 130, 266
        out.append(
            f'<g class="bob" style="animation-delay:-{delay}s"><g transform="translate({x},{y}) rotate({rot} {w / 2} {h / 2})">'
            f'<rect x="-7" y="-7" width="{w + 14}" height="{h + 14}" rx="24" fill="#0B0709" stroke="#3A272C" stroke-width="1.5"/>'
            f'<clipPath id="ph-{skin}"><rect width="{w}" height="{h}" rx="17"/></clipPath>'
            f'<image href="data:image/jpeg;base64,{data}" width="{w}" height="{h}" clip-path="url(#ph-{skin})" preserveAspectRatio="xMidYMin slice"/>'
            f'</g></g>'
        )
    return "".join(out)


def main():
    fall_css, fall_svg = falling_items()
    loop, chips = marquee()
    tx = 396
    shots = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "captures")
    devices = phones(shots)
    tagline = path("La boîte à outils qui avale tout.", "bricolage_extrabold", 38, tx, 352)
    sub = path("Vidéos, photos, sons, PDF, discussions WhatsApp.", "bricolage_regular", 22, tx, 392)

    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="Gulp, la boîte à outils qui avale tout">
<title>Gulp</title>
<defs>
<linearGradient id="brand" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FF3B4E"/><stop offset="1" stop-color="#FF7A45"/></linearGradient>
<radialGradient id="hi" cx=".3" cy=".1" r=".8"><stop offset="0" stop-color="#fff" stop-opacity=".22"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
<radialGradient id="glow"><stop offset="0" stop-color="#FF5A48" stop-opacity=".55"/><stop offset=".55" stop-color="#FF5A48" stop-opacity=".12"/><stop offset="1" stop-color="#FF5A48" stop-opacity="0"/></radialGradient>
<radialGradient id="glow2"><stop offset="0" stop-color="#7C5CFF" stop-opacity=".22"/><stop offset="1" stop-color="#7C5CFF" stop-opacity="0"/></radialGradient>
<pattern id="dots" width="26" height="26" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.3" fill="#fff" fill-opacity=".05"/></pattern>
<linearGradient id="fade" x1="0" x2="1"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".08" stop-color="#fff"/><stop offset=".92" stop-color="#fff"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
<mask id="edges"><rect y="420" width="{W}" height="60" fill="url(#fade)"/></mask>
<clipPath id="card"><rect width="{W}" height="{H}" rx="28"/></clipPath>
</defs>
<style>
.play{{transform-box:fill-box;transform-origin:50% 100%;animation:drop {CYCLE}s cubic-bezier(.5,0,.3,1) infinite}}
.mouth{{transform-box:fill-box;transform-origin:50% 0;animation:gulp {CYCLE}s cubic-bezier(.5,0,.3,1) infinite}}
@keyframes drop{{0%,20%{{transform:translateY(-12px)}}44%{{transform:translateY(36px) scale(.8,.88)}}56%{{transform:translateY(22px) scale(.9)}}78%,100%{{transform:translateY(-12px)}}}}
@keyframes gulp{{0%,30%{{transform:scaleY(1)}}47%{{transform:scaleY(1.6)}}60%{{transform:scaleY(.78)}}76%,100%{{transform:scaleY(1)}}}}
.tile{{transform-box:fill-box;transform-origin:50% 100%;animation:squash {CYCLE}s cubic-bezier(.5,0,.3,1) infinite}}
@keyframes squash{{0%,40%{{transform:scale(1)}}50%{{transform:scale(1.035,.965)}}62%{{transform:scale(.99,1.01)}}74%,100%{{transform:scale(1)}}}}
.glow{{transform-box:fill-box;transform-origin:50% 50%;animation:pulse {CYCLE}s ease-in-out infinite}}
@keyframes pulse{{0%,30%{{opacity:.55;transform:scale(.92)}}50%{{opacity:1;transform:scale(1.06)}}100%{{opacity:.55;transform:scale(.92)}}}}
.drift{{animation:drift 9s ease-in-out infinite alternate}}
@keyframes drift{{to{{transform:translate(-60px,30px)}}}}
.word{{animation:rise .9s cubic-bezier(.2,.8,.2,1) both}}
.tag{{animation:rise .9s .25s cubic-bezier(.2,.8,.2,1) both}}
.sub{{animation:rise .9s .45s cubic-bezier(.2,.8,.2,1) both}}
@keyframes rise{{from{{opacity:0;transform:translateY(26px)}}to{{opacity:1;transform:none}}}}
.bob{{animation:bob 4.2s ease-in-out infinite alternate}}
@keyframes bob{{from{{transform:translateY(-7px)}}to{{transform:translateY(9px)}}}}
.marquee{{animation:roll 38s linear infinite}}
@keyframes roll{{to{{transform:translateX(-{loop:.1f}px)}}}}
{fall_css}
@media (prefers-reduced-motion:reduce){{*{{animation:none!important}}[class^=fall]{{display:none}}}}
</style>
<g clip-path="url(#card)">
<rect width="{W}" height="{H}" fill="#140D11"/>
<rect width="{W}" height="{H}" fill="url(#dots)"/>
<g class="drift"><circle cx="1120" cy="70" r="300" fill="url(#glow2)"/></g>
<circle class="glow" cx="{CX}" cy="{ICON_Y + ICON / 2}" r="250" fill="url(#glow)"/>
{devices}
{fall_svg}
<g class="tile"><g transform="translate({ICON_X},{ICON_Y}) scale({ICON / 512})">
<rect width="512" height="512" rx="116" fill="url(#brand)"/><rect width="512" height="512" rx="116" fill="url(#hi)"/>
<g class="play"><polygon points="182,140 330,140 256,270" fill="#fff" stroke="#fff" stroke-width="42" stroke-linejoin="round"/></g>
<g class="mouth"><path d="M154 334 Q256 410 358 334" fill="none" stroke="#1C0A0E" stroke-width="42" stroke-linecap="round"/></g>
</g></g>
<g class="word"><path transform="translate({tx - 6},58) scale(1.42) translate(4,12)" d="{WORDMARK}" fill="#F6F2F0"/></g>
<path class="tag" d="{tagline}" fill="#F6F2F0"/>
<path class="sub" d="{sub}" fill="#BCA8A3"/>
<g mask="url(#edges)"><g transform="translate(0,424)">{chips}</g></g>
</g>
</svg>
'''
    open(OUT, "w").write(svg)
    print(OUT, len(svg) // 1024, "Ko")


if __name__ == "__main__":
    main()
