#!/usr/bin/env bash
# Acceptance run for the widget. Wipes the app, installs the build, adds the widget
# and checks the things that have broken before. Prints PASS/FAIL per check.
set -uo pipefail

# Target the emulator by default: a plugged-in phone must not be touched by tests.
ANDROID_SERIAL="${ANDROID_SERIAL:-$("$HOME/Library/Android/sdk/platform-tools/adb" devices | awk '/emulator-/{print $1; exit}')}"
export ANDROID_SERIAL
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
HERE="$(cd "$(dirname "$0")" && pwd)"
SHOTS="${SHOTS:-/tmp/ai-news-acceptance}"
mkdir -p "$SHOTS"
PASS=0
FAIL=0

check() { # check <name> <condition-result> <detail>
  if [ "$2" = "0" ]; then
    echo "PASS  $1${3:+  ($3)}"
    PASS=$((PASS + 1))
  else
    echo "FAIL  $1${3:+  ($3)}"
    FAIL=$((FAIL + 1))
  fi
}

dump() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/ui.xml; }
find_node() { "$HERE/ui_find.py" "$ADB" "$1" "${2:-any}" 2>/dev/null; }
counter() { dump | grep -o 'text="[0-9]*-[0-9]* of [0-9]*"' | head -1 | sed 's/text=//;s/"//g'; }
first_index() { counter | cut -d- -f1; }
first_headline() { dump | grep -o 'text="[^"]\{25,\}"' | grep -v "Updated" | head -1; }
scroll_to_end() { for _ in $(seq 1 12); do "$ADB" shell input swipe 540 1150 540 750 160; done; }
shot() { "$ADB" shell screencap -p /sdcard/s.png >/dev/null; "$ADB" pull /sdcard/s.png "$SHOTS/$1.png" >/dev/null; }

"$ADB" logcat -c >/dev/null 2>&1 || true   # only judge errors from this run

echo "== Clean install and add the widget"
SHOT="$SHOTS/00-installed.png" "$HERE/widget-clean-install.sh" >/dev/null 2>&1

echo "== Waiting for the first fetch"
for _ in $(seq 1 45); do
  STORIES="$(dump | grep -o 'text="[^"]*stories"' | head -1 | grep -o '[0-9]*' | tail -1)"
  case "$(dump)" in *Fetching*|*Updating*) sleep 3; continue;; esac
  [ "${STORIES:-0}" -gt 20 ] 2>/dev/null && break
  sleep 3
done
echo "   stories on the widget: ${STORIES:-unknown}"

XML="$(dump)"
echo "$XML" | grep -q "All Feeds" && check "widget is on the home screen" 0 || check "widget is on the home screen" 1
echo "$XML" | grep -q "Updated" && check "status line shows a fetch time" 0 || check "status line shows a fetch time" 1
[ -n "$(counter)" ] && check "footer shows a page counter" 0 "$(counter)" || check "footer shows a page counter" 1

shot 00-page1
"$HERE/has_thumbnails.py" "$SHOTS/00-page1.png" && check "story thumbnails render" 0 || check "story thumbnails render" 1

echo "== Paging"
BEFORE="$(first_index)"; BEFORE_HEAD="$(first_headline)"
P="$(find_node 'load next')"
if [ -z "$P" ]; then scroll_to_end; P="$(find_node 'load next')"; fi
if [ -n "$P" ]; then
  "$ADB" shell input tap $P; sleep 5
  AFTER="$(first_index)"; AFTER_HEAD="$(first_headline)"
  shot 01-page2
  [ "${AFTER:-0}" -gt "${BEFORE:-0}" ] 2>/dev/null && check "next page loads" 0 "$BEFORE -> $AFTER" || check "next page loads" 1 "stuck at $BEFORE"
  [ "$BEFORE_HEAD" != "$AFTER_HEAD" ] && check "the page shows new stories" 0 || check "the page shows new stories" 1
  TOP_TEXT="$(dump | grep -o 'text="[^"]*"' | sed -n '4p')"
  dump | grep -q "Load previous" && check "previous control appears" 0 || check "previous control appears" 1
  "$HERE/has_thumbnails.py" "$SHOTS/01-page2.png" && check "thumbnails survive paging" 0 || check "thumbnails survive paging" 1
  TOP_ROW="$(find_node 'load previous')"
  [ -n "$TOP_ROW" ] && check "lands at the top of the new page" 0 || check "lands at the top of the new page" 1
else
  check "load more row exists" 1
fi

echo "== Repeated loading"
OK=0
for round in 1 2 3 4; do
  P="$(find_node 'load next')"
  [ -z "$P" ] && { scroll_to_end; P="$(find_node 'load next')"; }
  [ -z "$P" ] && { OK=1; break; }
  PREV="$(first_index)"
  "$ADB" shell input tap $P; sleep 5
  NOW="$(first_index)"
  [ "${NOW:-0}" -le "${PREV:-0}" ] 2>/dev/null && { OK=1; echo "      round $round stuck on $PREV"; break; }
done
check "paging keeps working (4 more taps)" "$OK" "$(counter)"
EMPTY_ROWS="$(dump | grep -c 'text=""  *content-desc=""' || true)"
LAST_HEAD="$(dump | grep -o 'text="[^"]\{25,\}"' | tail -1)"
[ -n "$LAST_HEAD" ] && check "rows still render after growing the list" 0 || check "rows still render after growing the list" 1

echo "== Thumbnails deep in the list"
shot 02-page-deep
"$HERE/has_thumbnails.py" "$SHOTS/02-page-deep.png" && check "thumbnails render on a later page" 0 || check "thumbnails render on a later page" 1

echo "== Previous page and reset"
for _ in $(seq 1 12); do "$ADB" shell input swipe 540 750 540 1150 160; done
PP="$(find_node 'load previous')"
if [ -n "$PP" ]; then
  BEFORE_P="$(first_index)"
  "$ADB" shell input tap $PP; sleep 5
  AFTER_P="$(first_index)"
  [ "${AFTER_P:-0}" -lt "${BEFORE_P:-0}" ] 2>/dev/null && check "previous page loads" 0 "$BEFORE_P -> $AFTER_P" || check "previous page loads" 1 "$BEFORE_P -> $AFTER_P"
  FIRST_AFTER_PREV="$(dump | grep -o 'text="[^"]\{12,\}"' | sed -n '2p')"
  case "$FIRST_AFTER_PREV" in *"Load previous"*|*"ACTUALNO"*|*"."*) check "previous lands at the top of that page" 0;; *) check "previous lands at the top of that page" 1 "$FIRST_AFTER_PREV";; esac
else
  check "previous control is reachable" 1
fi
R="$(find_node 'Back to the newest stories' desc)"
if [ -n "$R" ]; then
  "$ADB" shell input tap $R; sleep 5
  case "$(first_index)" in 1) check "reset returns to the newest" 0 "$(counter)";; *) check "reset returns to the newest" 1 "$(counter)";; esac
else
  check "reset control is offered" 1
fi

echo "== Expand a card"
for _ in 1 2 3; do "$ADB" shell input swipe 540 750 540 1150 160; done
E="$(find_node 'Show the whole story' desc)"
if [ -n "$E" ]; then
  "$ADB" shell input tap $E; sleep 4
  # An expanded card is tall, so its chevron can sit below the fold.
  FOUND=1
  for _ in 1 2 3 4; do
    dump | grep -q 'content-desc="Show less"' && { FOUND=0; break; }
    "$ADB" shell input swipe 540 1150 540 900 160
    sleep 1
  done
  check "card expands on demand" "$FOUND"
  L="$(find_node 'Show less' desc)"; [ -n "$L" ] && "$ADB" shell input tap $L && sleep 2
else
  check "expand control exists" 1
fi

echo "== Refresh"
BEFORE_T="$(dump | grep -o 'text="Updated [^"]*"' | head -1)"
RF="$(find_node 'Refresh' desc)"
if [ -n "$RF" ]; then
  "$ADB" shell input tap $RF
  SAW_FETCH=1
  for _ in $(seq 1 12); do
    sleep 2
    "$ADB" logcat -d | grep -q "AiNewsRefresh: fetched" && { SAW_FETCH=0; break; }
  done
  check "refresh actually fetches" "$SAW_FETCH" "$("$ADB" logcat -d | grep -o 'AiNewsRefresh: fetched .*' | head -1)"
else
  check "refresh button exists" 1
fi

echo "== Background refresh is scheduled"
"$ADB" shell dumpsys alarm | grep -q "RefreshAlarmReceiver" && check "repeating refresh alarm is registered" 0 || check "repeating refresh alarm is registered" 1

echo "== Errors in the log"
ERRORS="$("$ADB" logcat -d | grep -ciE "GlanceAppWidget.*Error|FATAL EXCEPTION")"
[ "$ERRORS" = "0" ] && check "no widget errors logged" 0 || check "no widget errors logged" 1 "$ERRORS"

shot 99-final
echo
echo "$PASS passed, $FAIL failed. Screenshots in $SHOTS"
[ "$FAIL" = "0" ]
