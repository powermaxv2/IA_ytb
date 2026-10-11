"""GIF animés du README, faits à partir des captures Roborazzi de l'app (branche screenshots/<commit> du CI).

    python3 docs/readme/fabrication/gifs.py <dossier des captures>
      ->  docs/readme/gulp-trois-interfaces.gif   les 3 interfaces en même temps, outil après outil
      ->  docs/readme/outils/<outil>.gif          chaque outil, écran après écran (interface Gulp)
"""
import os
import shutil
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..")
FONTS = os.path.join(HERE, "../../../android/app/src/main/res/font")

BG = (20, 13, 17)
BEZEL = (11, 7, 9)
EDGE = (58, 39, 44)
INK = (246, 242, 240)
MUTED = (188, 168, 163)
ORANGE = (255, 122, 69)
RED = (255, 59, 78)

FPS = 20
LOSSY = 55          # gifsicle --lossy, si gifsicle est installé
SCROLL_FPS = 12      # les longues captures qui défilent : moins d'images, GIF plus léger
HOLD = 1700          # ms sur chaque écran
MOVE = 0.44          # s de transition


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name + ".ttf"), size)


def ease(t):
    """Accélère puis freine (cubic in-out)."""
    return 4 * t ** 3 if t < .5 else 1 - (-2 * t + 2) ** 3 / 2


def ease_out(t):
    return 1 - (1 - t) ** 3


class Screen:
    """Une capture mise à la largeur du téléphone ; les captures longues défilent."""

    def __init__(self, shots, name, w, h, scroll=0.0):
        im = Image.open(os.path.join(shots, name + ".png")).convert("RGB")
        im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
        self.im, self.w, self.h = im, w, h
        self.travel = max(0, min(im.height - h, round(scroll * w))) if scroll else 0

    def view(self, t=0.0):
        """Fenêtre visible ; t de 0 à 1 fait défiler la capture longue."""
        y = round(self.travel * ease(t))
        return self.im.crop((0, y, self.w, y + self.h))


def rounded_mask(w, h, r):
    m = Image.new("L", (w * 3, h * 3), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * 3 - 1, h * 3 - 1), r * 3, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def phone(canvas, x, y, w, h, content, r=26):
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle((x - 8, y - 8, x + w + 7, y + h + 7), r + 7, fill=BEZEL, outline=EDGE, width=2)
    canvas.paste(content, (x, y), rounded_mask(w, h, r))


def slide(old, new, t, w, h, vertical=False):
    """Navigation façon Android : le nouvel écran arrive et pousse l'ancien, qui s'assombrit."""
    e = ease(t)
    out = Image.new("RGB", (w, h), BG)
    if vertical:
        oy, ny = round(-h * .35 * e), round(h * (1 - e))
        out.paste(Image.blend(old, Image.new("RGB", (w, h), BG), .6 * e), (0, oy))
        out.paste(new, (0, ny))
    else:
        ox, nx = round(-w * .3 * e), round(w * (1 - e))
        out.paste(Image.blend(old, Image.new("RGB", (w, h), BG), .6 * e), (ox, 0))
        out.paste(new, (nx, 0))
    return out


def save_gif(path, frames):
    """frames = [(image RGB, durée ms)] ; une palette commune garde les couleurs stables d'une image à l'autre."""
    sample = Image.new("RGB", (frames[0][0].width, frames[0][0].height * 4))
    picks = [frames[i * (len(frames) - 1) // 3][0] for i in range(4)]
    for i, im in enumerate(picks):
        sample.paste(im, (0, i * im.height))
    palette = sample.quantize(255, method=Image.Quantize.MEDIANCUT)
    ims = [im.quantize(palette=palette, dither=Image.Dither.NONE) for im, _ in frames]
    ims[0].save(path, save_all=True, append_images=ims[1:], duration=[d for _, d in frames],
                loop=0, optimize=True, disposal=1)
    if shutil.which("gifsicle"):
        subprocess.run(["gifsicle", "-O3", f"--lossy={LOSSY}", "-b", path], check=True)
    print(os.path.relpath(path, OUT), f"{os.path.getsize(path) // 1024} Ko, {len(frames)} images")


# ---------------------------------------------------------------------------------------------------------------
# 1. Les trois interfaces, synchronisées

SKINS = [("classic", "Gulp"), ("paper", "Papier"), ("terminal", "Terminal")]
TOUR = [
    ("home_last_used", "Accueil", "10 outils prêts", 1.6),
    ("youtube_video", "Téléchargeur vidéo", "YouTube, Instagram, TikTok, X…", 0),
    ("convert_options", "Convertisseur", "Photo, son, vidéo, document", 0),
    ("preview_zip", "Aperçu", "Montre un fichier sans le modifier", 0),
    ("chat_results", "Vu, répondu", "Qui répond le plus vite sur WhatsApp ?", 3.4),
    ("compress_done", "Compresseur", "Plus léger pour l'envoyer", 0),
    ("qr_create", "QR code", "Scanner ou créer", 0),
    ("scan_edit", "Scanner", "Des photos de pages, un PDF", 0),
    ("trim_edit", "Découpeur", "Le bon moment d'une vidéo", 0),
    ("gomme_scan", "Gomme", "Efface ce que racontent les photos", 0),
    ("pdfjoin_edit", "Assembleur PDF", "Plusieurs PDF en un seul", 0),
]


def interfaces(shots):
    W, H = 820, 660
    pw, ph = 226, 490
    gap = (W - 3 * pw) // 4
    xs = [gap + i * (pw + gap) for i in range(3)]
    top = 116
    stagger = 0.09

    title_f, sub_f = font("bricolage_extrabold", 38), font("bricolage_regular", 19)
    num_f, skin_f = font("jetbrains_mono_bold", 14), font("bricolage_semibold", 18)

    screens = [[Screen(shots, f"{s}_{name}", pw, ph, scroll) for s, _ in SKINS] for name, _, _, scroll in TOUR]

    def base(caption_idx, cap_t):
        """Fond, légende de l'outil (qui monte en fondu) et nom des interfaces."""
        c = Image.new("RGB", (W, H), BG)
        d = ImageDraw.Draw(c)
        for k, (alpha, idx) in enumerate(((1 - cap_t, caption_idx - 1), (cap_t, caption_idx))):
            if idx < 0 or alpha <= 0:
                continue
            _, title, sub, _ = TOUR[idx]
            dy = round((1 - alpha) * (18 if k == 1 else -18))
            mix = lambda col: tuple(round(BG[j] + (col[j] - BG[j]) * alpha) for j in range(3))
            label = "GULP" if idx == 0 else f"OUTIL {idx:02d}/10"
            d.text((W // 2, 20 + dy), label, font=num_f, fill=mix(ORANGE), anchor="mt")
            d.text((W // 2, 41 + dy), title, font=title_f, fill=mix(INK), anchor="mt")
            d.text((W // 2, 86 + dy), sub, font=sub_f, fill=mix(MUTED), anchor="mt")
        for x, (_, label) in zip(xs, SKINS):
            d.text((x + pw // 2, top + ph + 18), label, font=skin_f, fill=MUTED, anchor="mt")
        # barre de progression du tour, en bas
        return c

    def progress(c, frac):
        d = ImageDraw.Draw(c)
        d.rounded_rectangle((xs[0], H - 14, xs[2] + pw, H - 10), 2, fill=(42, 30, 34))
        d.rounded_rectangle((xs[0], H - 14, xs[0] + max(4, round((xs[2] + pw - xs[0]) * frac)), H - 10), 2, fill=RED)

    frames = []
    n = len(TOUR)
    for i in range(n):
        cur = screens[i]
        frac = (i + 1) / n
        # transition depuis l'écran précédent, téléphone par téléphone (décalés)
        if i > 0:
            prev = screens[i - 1]
            steps = round((MOVE + 2 * stagger) * FPS)
            for f in range(steps + 1):
                tt = f / steps * (MOVE + 2 * stagger)
                c = base(i, ease_out(min(1, tt / MOVE)))
                for k in range(3):
                    lt = min(1, max(0, (tt - k * stagger) / MOVE))
                    old = prev[k].view(1.0)
                    phone(c, xs[k], top, pw, ph, slide(old, cur[k].view(0), lt, pw, ph, vertical=True))
                progress(c, (i + lt) / n)
                frames.append((c, 1000 // FPS))
        # pause, ou défilement des captures longues
        scroll = TOUR[i][3]
        if scroll:
            frames.append((draw_static(base(i, 1), cur, xs, top, pw, ph, 0, progress, frac), 900))
            steps = round(2.6 * SCROLL_FPS)
            for f in range(1, steps + 1):
                c = base(i, 1)
                for k in range(3):
                    phone(c, xs[k], top, pw, ph, cur[k].view(f / steps))
                progress(c, frac)
                frames.append((c, 1000 // SCROLL_FPS))
            frames[-1] = (frames[-1][0], 1300)
        else:
            frames.append((draw_static(base(i, 1), cur, xs, top, pw, ph, 0, progress, frac), HOLD))
    save_gif(os.path.join(OUT, "gulp-trois-interfaces.gif"), frames)


def draw_static(c, cur, xs, top, pw, ph, t, progress, frac):
    for k in range(3):
        phone(c, xs[k], top, pw, ph, cur[k].view(t))
    progress(c, frac)
    return c


# ---------------------------------------------------------------------------------------------------------------
# 2. Un GIF par outil

TOOLS = {
    "telechargeur-video": [("youtube_idle", 0), ("youtube_video", 0)],
    "convertisseur": [("convert_idle", 0), ("convert_options", 0), ("convert_progress", 0), ("convert_done", 0)],
    "apercu": [("preview_idle", 0), ("preview_info", 0), ("preview_zip", 0), ("preview_code", 0)],
    "vu-repondu": [("chat_idle", 0), ("chat_results", 6.0)],
    "compresseur": [("compress_idle", 0), ("compress_options", 0), ("compress_progress", 0), ("compress_done", 0)],
    "qr-code": [("qr_idle", 0), ("qr_scan", 0), ("qr_create", 0), ("qr_result", 0)],
    "scanner": [("scan_idle", 0), ("scan_pages", 0), ("scan_edit", 0), ("scan_done", 0)],
    "decoupeur": [("trim_idle", 0), ("trim_edit", 0), ("trim_progress", 0), ("trim_done", 0)],
    "gomme": [("gomme_idle", 0), ("gomme_scan", 0), ("gomme_progress", 0), ("gomme_done", 0)],
    "assembleur-pdf": [("pdfjoin_idle", 0), ("pdfjoin_edit", 0), ("pdfjoin_progress", 0), ("pdfjoin_done", 0)],
}


def tool(shots, slug, steps_list, skin="classic"):
    pw, ph, m = 250, 542, 18
    W, H = pw + 2 * m, ph + 2 * m + 18
    screens = [Screen(shots, f"{skin}_{name}", pw, ph, scroll) for name, scroll in steps_list]

    def dots(c, pos):
        d = ImageDraw.Draw(c)
        n = len(screens)
        x0 = W // 2 - (n - 1) * 8
        for k in range(n):
            on = max(0.0, 1 - abs(pos - k))
            r = 3 + 1.5 * on
            col = tuple(round(EDGE[j] + (RED[j] - EDGE[j]) * on) for j in range(3))
            d.ellipse((x0 + k * 16 - r, H - 14 - r, x0 + k * 16 + r, H - 14 + r), fill=col)

    def frame(content, pos):
        c = Image.new("RGB", (W, H), BG)
        phone(c, m, m, pw, ph, content, r=24)
        dots(c, pos)
        return c

    frames = []
    for i, s in enumerate(screens):
        if s.travel:
            frames.append((frame(s.view(0), i), 1000))
            n = round(3.4 * FPS)
            frames += [(frame(s.view(f / n), i), 1000 // FPS) for f in range(1, n + 1)]
            frames[-1] = (frames[-1][0], 1400)
        else:
            frames.append((frame(s.view(0), i), HOLD))
        nxt = screens[(i + 1) % len(screens)]
        back = i == len(screens) - 1
        n = round(MOVE * FPS)
        for f in range(1, n):
            t = f / n
            content = slide(s.view(1.0), nxt.view(0), t, pw, ph) if not back else \
                Image.blend(s.view(1.0), nxt.view(0), ease(t))
            frames.append((frame(content, (i + ease(t)) % len(screens) if not back else i * (1 - ease(t))), 1000 // FPS))
    os.makedirs(os.path.join(OUT, "outils"), exist_ok=True)
    save_gif(os.path.join(OUT, "outils", slug + ".gif"), frames)


if __name__ == "__main__":
    shots = sys.argv[1]
    only = sys.argv[2:]
    if not only or "interfaces" in only:
        interfaces(shots)
    for slug, st in TOOLS.items():
        if not only or slug in only:
            tool(shots, slug, st)
