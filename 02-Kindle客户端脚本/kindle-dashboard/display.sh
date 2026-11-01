#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
exec 9>"$RUN/display.lock"
flock -x 9 || exit 1
{
    date
    sh "$BASE/render-frame.sh"
    result=$?
    echo "EXIT_CODE: $result"
    exit "$result"
} > "$BASE/display.log" 2>&1
