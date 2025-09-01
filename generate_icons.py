#!/usr/bin/env python3
"""
Generate Android app icons from source image
Requires: pip install Pillow
"""

import os
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Installing Pillow...")
    import subprocess
    subprocess.check_call(["pip3", "install", "Pillow"])
    from PIL import Image

# Icon sizes for Android
ICON_SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

def generate_icons():
    # Path to original logo
    source_path = "app/src/main/res/logo_original.png"
    
    if not os.path.exists(source_path):
        print(f"Error: Source image not found at {source_path}")
        return False
    
    # Open the original image
    print(f"Opening source image: {source_path}")
    img = Image.open(source_path)
    
    # Convert to RGBA if not already
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
    
    # Generate icons for each density
    for density, size in ICON_SIZES.items():
        # Create directory if it doesn't exist
        dir_path = f"app/src/main/res/mipmap-{density}"
        os.makedirs(dir_path, exist_ok=True)
        
        # Resize image
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save square version
        output_path = f"{dir_path}/ic_launcher.png"
        resized.save(output_path, "PNG", optimize=True)
        print(f"Generated {density}: {size}x{size} -> {output_path}")
        
        # Save round version (same image for now)
        output_path_round = f"{dir_path}/ic_launcher_round.png"
        resized.save(output_path_round, "PNG", optimize=True)
        print(f"Generated {density} round: {size}x{size} -> {output_path_round}")
    
    print("\n✅ All icons generated successfully!")
    return True

if __name__ == "__main__":
    if generate_icons():
        print("\nNext steps:")
        print("1. Icons have been placed in app/src/main/res/mipmap-* directories")
        print("2. Rebuild the app to see the new icons")
    else:
        print("\n❌ Failed to generate icons")