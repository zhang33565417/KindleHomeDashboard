#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
# Non-exclusive observers; a power-key wake enables two minutes of touch UI.
for device in /dev/input/event0 /dev/input/event1; do
    ( /mnt/us/usbnet/bin/evtest "$device" 2>/dev/null |
      awk -v file="$RUN/power-button-time" '
        /type 1 .*code 116 .*value 1$/ {
            printf "%.0f\n", $3+0 > file; close(file)
        }' ) &
done
wait
