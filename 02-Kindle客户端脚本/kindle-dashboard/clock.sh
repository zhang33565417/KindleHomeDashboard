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
sh "$BASE/window-draw.sh" "$K_clock_bg_x" "$K_clock_bg_y" "$K_clock_bg_w" "$K_clock_bg_h" \
    "$K_clock_bg_x" "$K_clock_bg_y" "$K_clock_bg_w" "$K_clock_bg_h" "$FRAME_DIR/clock-background.png" \
    "$K_clock_x" "$K_clock_y" "$K_clock_w" "$K_clock_h" "$BASE/layout-assets/$K_layout/$tile-$clock.png" || exit 1
echo "$key" > "$RUN/clock-key"
echo "$clock" > "$RUN/calendar-clock"
# No per-minute flash log writes; runtime state is sufficient to verify ticking.
