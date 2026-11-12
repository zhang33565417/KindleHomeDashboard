#!/bin/sh
# Caller holds display.lock. Never acquire it recursively.
. /mnt/us/extensions/kindle-dashboard/layout-common.sh
is_dashboard || exit 0
load_frame || { log 'No supported cached frame'; exit 1; }
sleep 2
is_dashboard || exit 0
sh "$BASE/window-draw.sh" 0 0 "$K_width" "$K_height" \
    0 0 "$K_width" "$K_height" "$FRAME_DIR/frame.png" || exit 1
sh "$BASE/status.sh" force || exit 1
rm -f "$RUN/redraw-after-wake"
