#!/usr/bin/env bash
# Acceptance run for the widget. Wipes the app, installs the build, adds the widget
# and checks the things that have broken before. Prints PASS/FAIL per check.
set -uo pipefail

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
first_headline() { dump | grep -o 'text="[^"]\{25,\}"' | grep -v "Updated" | head -1; }
scroll_to_end() { for _ in $(seq 1 12); do "$ADB" shell input swipe 540 1150 540 750 160; done; }
shot() { "$ADB" shell screencap -p /sdcard/s.png >/dev/null; "$ADB" pull /sdcard/s.png "$SHOTS/$1.png" >/dev/null; }

echo "== Clean install and add the widget"
SHOT="$SHOTS/00-installed.png" "$HERE/widget-clean-install.sh" >/dev/null 2>&1

XML="$(dump)"
echo "$XML" | grep -q "All Feeds" && check "widget is on the home screen" 0 || check "widget is on the home screen" 1
echo "$XML" | grep -q "Updated" && check "status line shows a fetch time" 0 || check "status line shows a fetch time" 1
[ -n "$(counter)" ] && check "footer shows a page counter" 0 "$(counter)" || check "footer shows a page counter" 1

shot 00-page1
"$HERE/has_thumbnails.py" "$SHOTS/00-page1.png" && check "story thumbnails render" 0 || check "story thumbnails render" 1

echo "== Paging"
BEFORE="$(counter)"; BEFORE_HEAD="$(first_headline)"
P="$(find_node 'load more stories')"
if [ -z "$P" ]; then scroll_to_end; P="$(find_node 'load more stories')"; fi
if [ -n "$P" ]; then
  "$ADB" shell input tap $P; sleep 5
  AFTER="$(counter)"; AFTER_HEAD="$(first_headline)"
  shot 01-page2
  [ "$BEFORE" != "$AFTER" ] && check "load more advances the page" 0 "$BEFORE -> $AFTER" || check "load more advances the page" 1 "stuck on $BEFORE"
  [ "$BEFORE_HEAD" != "$AFTER_HEAD" ] && check "the page shows different stories" 0 || check "the page shows different stories" 1
  "$HERE/has_thumbnails.py" "$SHOTS/01-page2.png" && check "thumbnails survive paging" 0 || check "thumbnails survive paging" 1
  ROW_VISIBLE="$(find_node 'load more stories')"
  [ -n "$ROW_VISIBLE" ] && check "load more stays reachable without scrolling" 0 || check "load more stays reachable without scrolling" 1
else
  check "load more row exists" 1
fi

echo "== Repeated paging"
OK=0
for round in 1 2 3 4; do
  P="$(find_node 'load more stories')"
  [ -z "$P" ] && { scroll_to_end; P="$(find_node 'load more stories')"; }
  [ -z "$P" ] && { OK=1; break; }
  PREV="$(counter)"
  "$ADB" shell input tap $P; sleep 4
  NOW="$(counter)"
  [ "$PREV" = "$NOW" ] && { OK=1; echo "      round $round stuck on $PREV"; break; }
done
check "paging keeps working (4 more taps)" "$OK" "$(counter)"

echo "== Back to the newest"
R="$(find_node 'Back to the newest stories' desc)"
if [ -n "$R" ]; then
  "$ADB" shell input tap $R; sleep 4
  case "$(counter)" in 1-*) check "reset returns to the first page" 0 "$(counter)";; *) check "reset returns to the first page" 1 "$(counter)";; esac
else
  check "reset control is offered after paging" 1
fi

echo "== Expand a card"
for _ in 1 2 3; do "$ADB" shell input swipe 540 750 540 1150 160; done
E="$(find_node 'Show the whole story' desc)"
if [ -n "$E" ]; then
  LONG_BEFORE="$(dump | grep -o 'text="[^"]\{80,\}"' | wc -l | tr -d ' ')"
  "$ADB" shell input tap $E; sleep 4
  LONG_AFTER="$(dump | grep -o 'text="[^"]\{80,\}"' | wc -l | tr -d ' ')"
  [ "$LONG_AFTER" -gt "$LONG_BEFORE" ] && check "card expands on demand" 0 "$LONG_BEFORE -> $LONG_AFTER long lines" || check "card expands on demand" 1 "$LONG_BEFORE -> $LONG_AFTER long lines"
  L="$(find_node 'Show less' desc)"; [ -n "$L" ] && "$ADB" shell input tap $L && sleep 2
else
  check "expand control exists" 1
fi

echo "== Refresh"
BEFORE_T="$(dump | grep -o 'text="Updated [^"]*"' | head -1)"
RF="$(find_node 'Refresh' desc)"
if [ -n "$RF" ]; then
  "$ADB" logcat -c
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
