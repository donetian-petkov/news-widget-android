#!/usr/bin/env python3
"""Prints "x1 y1 x2 y2" for part of the screen.

  widget  - the whole news widget on the home screen
  list    - the scrolling story list inside it
  <text>  - the first node whose text or description contains <text>
"""
import re, subprocess, sys

ADB = sys.argv[1]
WHAT = sys.argv[2] if len(sys.argv) > 2 else "widget"
PKG = "com.ainews.android"

subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], check=True, capture_output=True)
xml = subprocess.run([ADB, "shell", "cat", "/sdcard/ui.xml"], check=True, capture_output=True, text=True).stdout

best = None
for tag in (m.group(0) for m in re.finditer(r"<node[^>]*>", xml)):
    if WHAT in ("widget", "list"):
        if f'package="{PKG}"' not in tag:
            continue
        if WHAT == "list" and 'scrollable="true"' not in tag:
            continue
    else:
        text = (re.search(r'text="([^"]*)"', tag) or [None, ""])[1]
        desc = (re.search(r'content-desc="([^"]*)"', tag) or [None, ""])[1]
        if WHAT.lower() not in f"{text} {desc}".lower():
            continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not b:
        continue
    x1, y1, x2, y2 = (int(v) for v in b.groups())
    area = (x2 - x1) * (y2 - y1)
    if best is None or area > best[0]:
        best = (area, (x1, y1, x2, y2))

if not best:
    sys.exit(1)
print(*best[1])
