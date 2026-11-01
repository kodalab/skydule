"""Builds store/feature-graphic-1024x500.png: "Skydule" and a small sun on the daytime sky (the app's morning blue).
Run from the repo root: python3 tools/generate_feature_graphic.py"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1024, 500
TOP, BOTTOM = (0x2E, 0x78, 0xC7), (0x5F, 0xA3, 0xDE)   # sky stop at 11:00 in ui/SkyBackground.kt
font = ImageFont.truetype("tools/fonts/IBMPlexSerif-Bold.ttf", 160)

img = Image.new("RGB", (W, H))
px = img.load()
for y in range(H):
    t = y / (H - 1)
    c = tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM))
    for x in range(W):
        px[x, y] = c

# Soft glow behind the sun
glow = Image.new("L", (W, H), 0)
ImageDraw.Draw(glow).ellipse([512 - 150, 178 - 150, 512 + 150, 178 + 150], fill=110)
glow = glow.filter(ImageFilter.GaussianBlur(60))
img = Image.composite(Image.new("RGB", (W, H), (255, 244, 214)), img, glow)

# Sun: pale gold to warm orange, top to bottom
r, cx, cy = 37, 512, 178
sun = Image.new("RGB", (2 * r, 2 * r))
sp = sun.load()
for y in range(2 * r):
    t = y / (2 * r - 1)
    c = tuple(round(a + (b - a) * t) for a, b in zip((0xFF, 0xEC, 0xAA), (0xFF, 0xA5, 0x5A)))
    for x in range(2 * r):
        sp[x, y] = c
m = Image.new("L", (8 * r, 8 * r), 0)
ImageDraw.Draw(m).ellipse([0, 0, 8 * r - 1, 8 * r - 1], fill=255)
m = m.resize((2 * r, 2 * r), Image.LANCZOS)
img.paste(sun, (cx - r, cy - r), m)

# Word, centred, with a soft shadow for contrast on the lighter blue
text = "Skydule"
bbox = font.getbbox(text)
tx = (W - (bbox[2] - bbox[0])) / 2 - bbox[0]
ty = 250 - bbox[1]
shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(shadow).text((tx + 3, ty + 5), text, font=font, fill=(10, 30, 70, 120))
shadow = shadow.filter(ImageFilter.GaussianBlur(4))
img = Image.alpha_composite(img.convert("RGBA"), shadow)
ImageDraw.Draw(img).text((tx, ty), text, font=font, fill=(255, 255, 255, 255))
img.convert("RGB").save("store/feature-graphic-1024x500.png")
