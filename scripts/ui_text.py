#!/usr/bin/env python3
"""Prints everything the news widget currently shows: one "text | description"
line per node, so a test can compare the widget before and after an action."""
import re, subprocess, sys

ADB = sys.argv[1]
PKG = sys.argv[2] if len(sys.argv) > 2 else "com.ainews.android"

subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], check=True, capture_output=True)
xml = subprocess.run([ADB, "shell", "cat", "/sdcard/ui.xml"], check=True, capture_output=True, text=True).stdout

for tag in (m.group(0) for m in re.finditer(r"<node[^>]*>", xml)):
    if f'package="{PKG}"' not in tag:
        continue
    text = (re.search(r'text="([^"]*)"', tag) or [None, ""])[1]
    desc = (re.search(r'content-desc="([^"]*)"', tag) or [None, ""])[1]
    if text or desc:
        print(f"{text} | {desc}")
