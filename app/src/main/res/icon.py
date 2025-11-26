from PIL import Image
import os

# Base folder (inside res/)
base = "."

# Where your highest-res icons exist
anydpi = "mipmap-anydpi-v26"

# Icons to process
icons = ["ic_launcher.png", "ic_launcher_round.png"]

# Target sizes for each mipmap folder
sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

for icon_name in icons:
    src = os.path.join(anydpi, icon_name)

    if not os.path.exists(src):
        print(f"Missing: {src}")
        continue

    img = Image.open(src)

    # Generate for each density
    for folder, size in sizes.items():
        out_path = os.path.join(folder, icon_name)

        if not os.path.exists(folder):
            os.makedirs(folder)

        resized = img.resize((size, size), Image.LANCZOS)
        resized.save(out_path)

        print(f"Saved → {out_path} ({size}px)")
