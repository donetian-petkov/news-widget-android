# Widget acceptance run

`scripts/widget-acceptance.sh` is the check that runs after every widget change. It wipes the
app, installs the current debug build, has the launcher pin the widget to the home screen, and
then drives every control the widget offers, checking after each one that the widget really
changed. Every check prints PASS or FAIL and screenshots land in `/tmp/ai-news-actions`.

The widget can end up on a later home page, so the run looks for it before every check instead
of assuming the first screen.

What it drives:

- The feed arrives, the counter reads `1-10 of N`, thumbnails are drawn, and nothing is stuck
  on "Fetching" or "Update failed".
- Power off and back on, and refresh - confirmed from the app log, not the clock.
- Load next three times in a row, each landing at the top of the new page with its pictures,
  load previous, and the arrow back to the newest stories.
- The unread filter on and off, mark everything read, and the widget settings screen.
- On a story: save and unsave, expand and collapse, hide, share.
- Tapping the feed title opens the app; tapping a story opens the browser.
- Stack mode: next and previous story, the thumbnail, pin and unpin, copy link, open the
  summary, open the source in the browser, and back to column mode.
- No widget error or crash in the log, and the repeating background refresh alarm is
  registered.

Run it with `./scripts/widget-acceptance.sh`; it exits non-zero if anything failed.
`scripts/widget-actions.sh` is the same run without the alarm check, and `SKIP_INSTALL=1` reuses
the widget already on the screen.
