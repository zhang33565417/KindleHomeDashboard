#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/layout-common.sh"
# The caller holds display.lock; all image and status drawing is serialized.
is_dashboard || exit 0
load_frame || exit 0
if [ "${STATUS_CLOCK_ONLY:-}" != 1 ]; then
level=$(lipc-get-prop com.lab126.powerd battLevel 2>/dev/null)
charging=$(lipc-get-prop com.lab126.powerd isCharging 2>/dev/null)
case "$level" in ''|*[!0-9]*) log 'Battery read failed'; exit 1;; esac
[ "$level" -le 100 ] || level=100
[ "$charging" = 1 ] || charging=0
asset="$level-$charging"
key="$K_layout-$frame_name-$asset"
if [ "${1:-}" = force ] || [ "$(cat "$RUN/status-key" 2>/dev/null)" != "$key" ]; then
    "$BASE/bin/fbink" -q -b -Y -4 -g "file=$FRAME_DIR/battery-background.png,x=$K_battery_x,y=$K_battery_y" || exit 1
    "$BASE/bin/fbink" -q -b -Y -4 -g "file=$BASE/layout-assets/$K_layout/$asset.png,x=$K_battery_x,y=$K_battery_y,w=$K_battery_w,h=$K_battery_h" || exit 1
    "$BASE/bin/fbink" -q -w -s "top=$K_battery_y,left=$K_battery_x,width=$K_battery_w,height=$K_battery_h" || exit 1
    echo "$key" > "$RUN/status-key"
    log "Status drawn: battery=$level charging=$charging"
fi
fi
CALENDAR_FORCE="${1:-}" sh "$BASE/clock.sh"
