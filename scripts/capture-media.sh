#!/usr/bin/env bash
# Takes the README screenshots and GIFs of the widget. Run it after
# widget-clean-install.sh, with the widget on the home screen and the feed fetched.
set -uo pipefail

ANDROID_SERIAL="${ANDROID_SERIAL:-$("$HOME/Library/Android/sdk/platform-tools/adb" devices | awk '/emulator-/{print $1; exit}')}"
export ANDROID_SERIAL
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$HERE/../docs/images}"
RAW="${RAW:-/tmp/ai-news-media}"
mkdir -p "$OUT" "$RAW"

point() { "$HERE/ui_find.py" "$ADB" "$1" "${2:-any}" 2>/dev/null; }
tap() { local p; p="$(point "$1" "${2:-any}")" || { echo "missing: $1" >&2; return 1; }; "$ADB" shell input tap $p; sleep "${3:-4}"; }
find_widget() { # the pinned widget may sit on a later home page
  for _ in 0 1 2 3; do
    "$HERE/ui_text.py" "$ADB" | grep -q . && return 0
    "$ADB" shell input swipe 900 1200 200 1200 200; sleep 2
  done
  "$HERE/ui_text.py" "$ADB" | grep -q .
}
home() { "$ADB" shell input keyevent KEYCODE_HOME; sleep 3; find_widget; }

home || { echo "the widget is not on any home screen" >&2; exit 1; }

# Crop to the widget with a small margin, the same frame for every picture.
read -r X1 Y1 X2 Y2 <<<"$("$HERE/ui_box.py" "$ADB" widget)"
CX=$(( X1 - 12 )); CY=$(( Y1 - 12 )); CW=$(( X2 - X1 + 24 )); CH=$(( Y2 - Y1 + 24 ))
CROP="crop=$CW:$CH:$CX:$CY"

shot() { # shot <name> — a still of the widget
  "$ADB" shell screencap -p /sdcard/m.png; "$ADB" pull /sdcard/m.png "$RAW/$1.png" >/dev/null
  ffmpeg -loglevel error -y -i "$RAW/$1.png" -vf "$CROP" "$OUT/$1.png"
}
app_shot() { # app_shot <name> — a whole-screen still of the app
  "$ADB" shell screencap -p /sdcard/m.png; "$ADB" pull /sdcard/m.png "$RAW/$1.png" >/dev/null
  ffmpeg -loglevel error -y -i "$RAW/$1.png" -vf "scale=540:-1" "$OUT/$1.png"
}
rec_start() { "$ADB" shell rm -f /sdcard/m.mp4; "$ADB" shell screenrecord --bit-rate 8000000 /sdcard/m.mp4 & REC=$!; sleep 1; }
rec_stop() { # rec_stop <name> [crop|full]
  sleep 1; "$ADB" shell pkill -INT screenrecord; wait "$REC" 2>/dev/null; sleep 2
  "$ADB" pull /sdcard/m.mp4 "$RAW/$1.mp4" >/dev/null
  local vf="$CROP,fps=12,scale=420:-1:flags=lanczos"
  [ "${2:-crop}" = full ] && vf="fps=12,scale=320:-1:flags=lanczos"
  ffmpeg -loglevel error -y -i "$RAW/$1.mp4" \
    -vf "$vf,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4" \
    "$OUT/$1.gif"
}
scroll_list() { # scroll_list <down|up> <times>
  read -r a b c d <<<"$("$HERE/ui_box.py" "$ADB" list)"
  local cx=$(( (a + c) / 2 )) top=$(( b + 80 )) bot=$(( d - 80 ))
  for _ in $(seq 1 "$2"); do
    if [ "$1" = down ]; then "$ADB" shell input swipe $cx $bot $cx $top 600
    else "$ADB" shell input swipe $cx $top $cx $bot 600; fi
    sleep 1
  done
}

echo "== Stills"
shot widget-column
tap "Show the whole story" desc 5 && shot widget-expanded && tap "Show less" desc 4
tap "Show stack mode" desc 5 && shot widget-stack && tap "Show column mode" desc 5

echo "== Scrolling the list"
rec_start; scroll_list down 4; scroll_list up 4; rec_stop widget-scroll

echo "== Paging"
for _ in 1 2 3 4 5 6 7 8; do point "Load next" >/dev/null && break; scroll_list down 1; done
P="$(point "Load next")"
rec_start
"$ADB" shell input tap $P; sleep 6
tap "Back to the newest stories" desc 6
rec_stop widget-paging

echo "== Opening a story up"
rec_start; tap "Show the whole story" desc 4; tap "Show less" desc 3; rec_stop widget-expand

echo "== Stack mode"
rec_start
tap "Show stack mode" desc 4
tap "Next story" desc 3; tap "Next story" desc 3; tap "Previous story" desc 3
tap "Show column mode" desc 4
rec_stop widget-stack

echo "== Unread filter"
rec_start; tap "Show unread only" desc 4; tap "Show every story" desc 4; rec_stop widget-unread

echo "== The app"
"$ADB" shell am force-stop com.ainews.android
"$ADB" shell am start -n com.ainews.android/.MainActivity >/dev/null; sleep 8
app_shot app-feed
rec_start
"$ADB" shell input swipe 540 1800 540 700 600; sleep 1
"$ADB" shell input swipe 540 700 540 1800 600; sleep 1
tap "Read story" text 5
app_shot app-story
"$ADB" shell input swipe 540 1700 540 900 600; sleep 2
rec_stop app-tour full
home
echo "done -> $OUT"
