"""
Generates BusNow app icon (1024x1024) and splash logo (512x512).
Run once: python3 generate_assets.py
"""
from PIL import Image, ImageDraw
import os

os.makedirs("assets/icon", exist_ok=True)

# ── Colors ────────────────────────────────────────────────────────────────────
PRIMARY      = (64,  96,  147)   # #406093
PRIMARY_DARK = (26,  58,  92)    # #1A3A5C
PRIMARY_LIGHT= (107, 138, 181)   # #6B8AB5
WHITE        = (255, 255, 255)
TRANSPARENT  = (0,   0,   0,   0)

# ── Helper: filled rounded rectangle ─────────────────────────────────────────
def rounded_rect(draw, x0, y0, x1, y1, r, fill):
    draw.rectangle([x0 + r, y0, x1 - r, y1], fill=fill)
    draw.rectangle([x0, y0 + r, x1, y1 - r], fill=fill)
    draw.ellipse([x0,       y0,       x0+r*2, y0+r*2], fill=fill)
    draw.ellipse([x1-r*2,   y0,       x1,     y0+r*2], fill=fill)
    draw.ellipse([x0,       y1-r*2,   x0+r*2, y1    ], fill=fill)
    draw.ellipse([x1-r*2,   y1-r*2,   x1,     y1    ], fill=fill)

# ── Draw bus silhouette ───────────────────────────────────────────────────────
def draw_bus(draw, cx, cy, scale, bus_color, wheel_color, glass_color):
    """Draw bus centred at (cx,cy) scaled by `scale` (1.0 = 1024px canvas)."""
    s = scale

    # Body
    bx0, by0 = cx - int(330*s), cy - int(130*s)
    bx1, by1 = cx + int(330*s), cy + int(130*s)
    rounded_rect(draw, bx0, by0, bx1, by1, int(40*s), bus_color)

    # Roof bump (raised cabin top)
    rounded_rect(draw,
                 bx0 + int(20*s), by0 - int(55*s),
                 bx1 - int(20*s), by0 + int(20*s),
                 int(25*s), bus_color)

    # Windows — 4 across
    wy0 = by0 - int(30*s)
    wy1 = wy0 + int(95*s)
    win_starts = [-290, -140, 10, 160]
    for wx in win_starts:
        wx0 = cx + int(wx * s)
        rounded_rect(draw, wx0, wy0, wx0 + int(110*s), wy1, int(14*s), glass_color)

    # Windshield (front, right side)
    rounded_rect(draw,
                 cx + int(265*s), wy0,
                 cx + int(315*s), wy1,
                 int(14*s), glass_color)

    # Door
    rounded_rect(draw,
                 cx + int(70*s),  cy - int(20*s),
                 cx + int(160*s), by1,
                 int(12*s), glass_color)

    # Front bumper detail
    rounded_rect(draw,
                 cx + int(305*s), cy + int(20*s),
                 cx + int(335*s), cy + int(90*s),
                 int(8*s), PRIMARY_LIGHT)

    # Wheels
    for wx in [-200, 200]:
        wcx = cx + int(wx * s)
        wcy = by1 + int(55*s)
        wr  = int(70*s)
        draw.ellipse([wcx-wr, wcy-wr, wcx+wr, wcy+wr], fill=wheel_color)
        draw.ellipse([wcx - int(32*s), wcy - int(32*s),
                      wcx + int(32*s), wcy + int(32*s)], fill=bus_color)


# ══════════════════════════════════════════════════════════════════════════════
# 1. APP ICON  1024 × 1024
# ══════════════════════════════════════════════════════════════════════════════
SIZE = 1024
icon = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(icon)

# Background — rounded square
rounded_rect(d, 0, 0, SIZE, SIZE, 180, PRIMARY)

# Subtle gradient stripe
for i in range(SIZE):
    alpha = int(30 * (i / SIZE))
    d.line([(0, i), (SIZE, i)], fill=(255, 255, 255, alpha))

# Bus centred slightly above middle
draw_bus(d, cx=512, cy=500, scale=0.88,
         bus_color=WHITE, wheel_color=PRIMARY_DARK, glass_color=(214, 228, 247))

icon.save("assets/icon/app_icon.png")
print("✓ assets/icon/app_icon.png")


# ══════════════════════════════════════════════════════════════════════════════
# 2. SPLASH LOGO  512 × 512  (transparent — placed on coloured native bg)
# ══════════════════════════════════════════════════════════════════════════════
SL = 512
logo = Image.new("RGBA", (SL, SL), (0, 0, 0, 0))
dl = ImageDraw.Draw(logo)

draw_bus(dl, cx=256, cy=240, scale=0.44,
         bus_color=WHITE, wheel_color=(214, 228, 247), glass_color=(107, 138, 181))

logo.save("assets/icon/splash_logo.png")
print("✓ assets/icon/splash_logo.png")
print("\nDone! Run flutter commands next.")
