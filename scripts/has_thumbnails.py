#!/usr/bin/env python3
"""True when the widget screenshot shows story thumbnails: the left edge of the
cards carries colourful pixels rather than flat card background."""
import sys
from PIL import Image

img = Image.open(sys.argv[1]).convert("RGB")
w, h = img.size
# the widget sits in the upper half; story thumbnails hug the left edge of each card
region = img.crop((int(w * 0.11), int(h * 0.25), int(w * 0.23), int(h * 0.48)))
colours = region.getcolors(maxcolors=1_000_000) or []
distinct = len(colours)
saturated = sum(count for count, (r, g, b) in colours if max(r, g, b) - min(r, g, b) > 40)
print(f"distinct={distinct} saturated={saturated}", file=sys.stderr)
sys.exit(0 if distinct > 200 and saturated > 500 else 1)
