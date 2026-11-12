#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
interpreter=/mnt/us/koreader/luajit
[ -x "$BASE/bin/luajit" ] && interpreter="$BASE/bin/luajit"
[ -x "$interpreter" ] || { log 'LuaJIT unavailable for X11 window'; exit 1; }
cd "${interpreter%/*}" || exit 1
"$interpreter" "$BASE/window.lua" "$RUN" "$BASE/bin/fbink" &
child=$!
trap 'kill "$child" 2>/dev/null; wait "$child"; exit' INT TERM
wait "$child"
# On a renderer crash, yield back to the native UI.
is_dashboard || exit 0
rm -f "$RUN/window-worker.pid"
exec sh "$BASE/control.sh" home
