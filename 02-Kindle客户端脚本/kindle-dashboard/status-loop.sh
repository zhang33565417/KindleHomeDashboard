#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
while is_dashboard; do
    (exec 9>"$RUN/display.lock"; flock -x 9 && sh "$BASE/status.sh")
    # Align to the next minute even after drawing takes some time.
    seconds=$(TZ=CST-8 date '+%S' | sed 's/^0//')
    [ -n "$seconds" ] || seconds=0
    sleep $((60 - seconds))
done
