# Widget acceptance run

`scripts/widget-acceptance.sh` is the check that runs after every widget change. It wipes
the app, installs the current debug build, adds the widget to the home screen the way a
person would, and then exercises the things that have broken before. Every check prints
PASS or FAIL and screenshots land in `/tmp/ai-news-acceptance`.

What it checks:

1. The widget is on the home screen and shows the feed name.
2. The status line shows when it last fetched.
3. The footer shows a page counter, for example `1-2 of 283`.
4. Story thumbnails render - measured from the screenshot pixels, not the view tree.
5. Load next moves to the next ten and the counter changes.
6. The page really shows different stories afterwards.
7. A Load previous control appears once you have moved.
8. Thumbnails still render on the new page.
9. Paging lands at the top of the new page.
10. Four more taps keep advancing rather than sticking.
11. Rows still render once you are deep in the list.
12. Load previous goes back ten.
13. The back arrow returns to the newest.
11. A card expands on demand and shows more text.
12. Refresh actually fetches, confirmed from the app log rather than the clock.
13. The repeating background refresh alarm is registered.
14. Nothing logged a widget error or a crash.

Run it with `./scripts/widget-acceptance.sh`; it exits non-zero if anything failed.
