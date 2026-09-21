#!/usr/bin/env python3
"""Checks that every story on the widget's current page has its picture.

A drawn thumbnail is an image node whose description is the story's title, so a story with a
picture shows up twice in the dump - once as the title, once as the image - while a story left
with a grey placeholder shows up once. The script scrolls the widget's own list from top to
bottom so it sees all ten stories, not just the three on screen.

Prints "<drawn> of <total>" and the titles still without a picture; exits non-zero if any are.
"""
import re
import subprocess
import sys
import time

ADB = sys.argv[1]
PASSES = int(sys.argv[2]) if len(sys.argv) > 2 else 8
PKG = "com.ainews.android"
BUTTONS = {
    "Save story", "Share story", "Hide story", "Show the whole story", "Show less",
    "Pin story", "Unpin story", "Remove from library", "Copy link", "Open source",
    "Open summary", "Open research", "Open translation", "Previous story", "Next story",
    "Power on", "Power off", "Refresh", "Show stack mode", "Show column mode",
    "Show unread only", "Show every story", "Mark everything read", "Widget settings",
    "Back to the newest stories",
}


def dump():
    subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
                   check=True, capture_output=True)
    return subprocess.run([ADB, "shell", "cat", "/sdcard/ui.xml"],
                          check=True, capture_output=True, text=True).stdout


def nodes(xml):
    for tag in (m.group(0) for m in re.finditer(r"<node[^>]*>", xml)):
        if f'package="{PKG}"' not in tag:
            continue
        text = (re.search(r'text="([^"]*)"', tag) or [None, ""])[1]
        desc = (re.search(r'content-desc="([^"]*)"', tag) or [None, ""])[1]
        bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        scrollable = 'scrollable="true"' in tag
        yield text, desc, bounds, scrollable


def is_title(text, desc):
    return (
        text and not desc and len(text) > 24 and not text.isupper()
        and not re.match(r"^(Updated |Paused|Update failed|Fetching|No connection)", text)
        and not re.match(r"^\d{1,2} \w{3} \d{2}:\d{2}", text)
        and not re.match(r"^\d+-\d+ of \d+$", text)
    )


titles, images = [], set()
worst_placeholders = 0
seen = set()
for step in range(PASSES):
    xml = dump()
    box = None
    on_screen = 0
    for text, desc, bounds, scrollable in nodes(xml):
        if scrollable and bounds:
            box = tuple(int(v) for v in bounds.groups())
        if desc and not text and desc not in BUTTONS:
            images.add(desc)
        # A card still waiting for its picture shows the source's initial in a grey tile.
        if text and not desc and len(text) == 1 and text.isalpha():
            on_screen += 1
        if is_title(text, desc) and text not in seen:
            seen.add(text)
            titles.append(text)
    # The same tile is seen again at every scroll step, so count the worst single view.
    worst_placeholders = max(worst_placeholders, on_screen)
    if box is None:
        break
    x1, y1, x2, y2 = box
    subprocess.run([ADB, "shell", "input", "swipe",
                    str((x1 + x2) // 2), str(y2 - 40),
                    str((x1 + x2) // 2), str(y1 + 40), "200"], capture_output=True)
    time.sleep(0.8)

# Leave the list where we found it.
if box:
    x1, y1, x2, y2 = box
    for _ in range(PASSES + 2):
        subprocess.run([ADB, "shell", "input", "swipe",
                        str((x1 + x2) // 2), str(y1 + 40),
                        str((x1 + x2) // 2), str(y2 - 40), "200"], capture_output=True)

missing = [t for t in titles if t not in images]
print(f"{len(titles) - len(missing)} of {len(titles)} stories have their picture, "
      f"at worst {worst_placeholders} grey placeholders on screen at once")
for t in missing:
    print(f"  missing: {t[:70]}")
sys.exit(1 if worst_placeholders else 0)
