"""Texte -> chemins SVG avec les polices de l'app (pas de police externe dans un README)."""
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
import os

FONTS = os.path.join(os.path.dirname(__file__), "../../../android/app/src/main/res/font")
_cache = {}


def font(name):
    if name not in _cache:
        _cache[name] = TTFont(os.path.join(FONTS, name + ".ttf"))
    return _cache[name]


def width(text, name, size, tracking=0.0):
    f = font(name)
    upm = f["head"].unitsPerEm
    cmap, hmtx = f.getBestCmap(), f["hmtx"]
    return sum(hmtx[cmap.get(ord(c), cmap[ord("?")])][0] for c in text) * size / upm + tracking * size * max(len(text) - 1, 0)


def path(text, name, size, x=0.0, y=0.0, tracking=0.0):
    """Chemin SVG (attribut d) du texte, ligne de base en y."""
    f = font(name)
    upm = f["head"].unitsPerEm
    cmap, hmtx, gs = f.getBestCmap(), f["hmtx"], f.getGlyphSet()
    s = size / upm
    pen = SVGPathPen(gs)
    cx = x
    for c in text:
        g = cmap.get(ord(c), cmap[ord("?")])
        gs[g].draw(TransformPen(pen, (s, 0, 0, -s, cx, y)))
        cx += hmtx[g][0] * s + tracking * size
    return pen.getCommands()
