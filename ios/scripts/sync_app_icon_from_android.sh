#!/usr/bin/env bash
# Regenerate ios/turnTable/Assets.xcassets/AppIcon from Android drawable/ic_launcher.png
# Run from repo root: ./ios/scripts/sync_app_icon_from_android.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export ROOT
python3 << 'PY'
from PIL import Image
import json
import os

ROOT = os.environ["ROOT"]
SRC = f"{ROOT}/android/app/src/main/res/drawable/ic_launcher.png"
APPICON = f"{ROOT}/ios/turnTable/Assets.xcassets/AppIcon.appiconset"
os.makedirs(APPICON, exist_ok=True)

src = Image.open(SRC).convert("RGBA")

# Opaque fill matches Android app cards (values/colors.xml home_card_bg #2B2B2B)
_BG = (0x2B, 0x2B, 0x2B)

def save_flat(px, name):
    im = src.resize((px, px), Image.Resampling.LANCZOS)
    bg = Image.new("RGB", im.size, _BG)
    bg.paste(im, mask=im.split()[3])
    bg.save(f"{APPICON}/{name}", "PNG")

sizes = {
    "icon-20.png": 20, "icon-29.png": 29, "icon-40.png": 40, "icon-58.png": 58,
    "icon-60.png": 60, "icon-76.png": 76, "icon-80.png": 80, "icon-87.png": 87,
    "icon-120.png": 120, "icon-152.png": 152, "icon-167.png": 167,
    "icon-180.png": 180, "icon-1024.png": 1024,
}
for fn, px in sizes.items():
    save_flat(px, fn)

images = [
    {"size": "20x20", "idiom": "iphone", "filename": "icon-40.png", "scale": "2x"},
    {"size": "20x20", "idiom": "iphone", "filename": "icon-60.png", "scale": "3x"},
    {"size": "29x29", "idiom": "iphone", "filename": "icon-58.png", "scale": "2x"},
    {"size": "29x29", "idiom": "iphone", "filename": "icon-87.png", "scale": "3x"},
    {"size": "40x40", "idiom": "iphone", "filename": "icon-80.png", "scale": "2x"},
    {"size": "40x40", "idiom": "iphone", "filename": "icon-120.png", "scale": "3x"},
    {"size": "60x60", "idiom": "iphone", "filename": "icon-120.png", "scale": "2x"},
    {"size": "60x60", "idiom": "iphone", "filename": "icon-180.png", "scale": "3x"},
    {"size": "20x20", "idiom": "ipad", "filename": "icon-20.png", "scale": "1x"},
    {"size": "20x20", "idiom": "ipad", "filename": "icon-40.png", "scale": "2x"},
    {"size": "29x29", "idiom": "ipad", "filename": "icon-29.png", "scale": "1x"},
    {"size": "29x29", "idiom": "ipad", "filename": "icon-58.png", "scale": "2x"},
    {"size": "40x40", "idiom": "ipad", "filename": "icon-40.png", "scale": "1x"},
    {"size": "40x40", "idiom": "ipad", "filename": "icon-80.png", "scale": "2x"},
    {"size": "76x76", "idiom": "ipad", "filename": "icon-76.png", "scale": "1x"},
    {"size": "76x76", "idiom": "ipad", "filename": "icon-152.png", "scale": "2x"},
    {"size": "83.5x83.5", "idiom": "ipad", "filename": "icon-167.png", "scale": "2x"},
    {"size": "1024x1024", "idiom": "ios-marketing", "filename": "icon-1024.png", "scale": "1x"},
]
with open(f"{APPICON}/Contents.json", "w") as f:
    json.dump({"images": images, "info": {"author": "xcode", "version": 1}}, f, indent=2)
print("OK:", APPICON)
PY
