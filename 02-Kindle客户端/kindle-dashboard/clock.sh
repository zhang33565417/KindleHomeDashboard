#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/layout-common.sh"
is_dashboard || exit 0
load_frame || exit 0
clock=$(TZ=CST-8 date '+%H%M')
profile=$(cat "$RUN/profile" 2>/dev/null)
case "$profile" in night) tile=sleep-time; style=snapshot;; *) tile=time; style=live;; esac
key="$K_layout-$frame_name-$style-$clock"
if [ "${CALENDAR_FORCE:-}" != force ] && [ "$(cat "$RUN/clock-key" 2>/dev/null)" = "$key" ]; then exit 0; fi
FB="$BASE/bin/fbink"
"$FB" -q -b -Y -4 -g "file=$FRAME_DIR/clock-background.png,x=$K_clock_bg_x,y=$K_clock_bg_y" || exit 1
"$FB" -q -b -Y -4 -g "file=$BASE/layout-assets/$K_layout/$tile-$clock.png,x=$K_clock_x,y=$K_clock_y,w=$K_clock_w,h=$K_clock_h" || exit 1
"$FB" -q -w -s "top=$K_clock_bg_y,left=$K_clock_bg_x,width=$K_clock_bg_w,height=$K_clock_bg_h" || exit 1
echo "$key" > "$RUN/clock-key"
echo "$clock" > "$RUN/calendar-clock"
# No per-minute flash log writes; runtime state is sufficient to verify ticking.
