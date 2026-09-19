#!/usr/bin/env python3
"""Finds a node in the current screen dump and prints its centre as "x y"."""
import re
import subprocess
import sys

ADB = sys.argv[1]
NEEDLE = sys.argv[2].lower()
ATTR = sys.argv[3] if len(sys.argv) > 3 else "any"

subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
               check=True, capture_output=True)
xml = subprocess.run([ADB, "shell", "cat", "/sdcard/ui.xml"],
                     check=True, capture_output=True, text=True).stdout

for node in re.finditer(r"<node[^>]*>", xml):
    tag = node.group(0)
    text = (re.search(r'text="([^"]*)"', tag) or [None, ""])[1]
    desc = (re.search(r'content-desc="([^"]*)"', tag) or [None, ""])[1]
    haystack = {"text": text, "desc": desc, "any": f"{text} {desc}"}[ATTR].lower()
    if NEEDLE not in haystack:
        continue
    bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not bounds:
        continue
    x1, y1, x2, y2 = (int(v) for v in bounds.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    sys.exit(0)

sys.exit(1)
