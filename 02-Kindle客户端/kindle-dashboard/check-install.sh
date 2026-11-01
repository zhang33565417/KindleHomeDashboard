#!/bin/sh
# Read-only preflight. This does not enter dashboard mode or change system files.
BASE=/mnt/us/extensions/kindle-dashboard
fail=0
check() {
    if "$@"; then echo "OK: $*"; else echo "MISSING: $*"; fail=1; fi
}
for tool in lipc-get-prop lipc-set-prop flock wget unzip od awk rtcwake md5sum; do
    check command -v "$tool"
done
check test -x "$BASE/bin/fbink"
check test -x /mnt/us/usbnet/bin/evtest
check test -e /dev/fb0
check test -e /dev/rtc1
check test -w /sys/class/rtc/rtc0/wakealarm
check test -w /sys/class/rtc/rtc1/wakealarm
check test -r /sys/class/rtc/rtc0/device/power/wakeup
check test -r /sys/class/power_supply/bd71827_ac/online
check test -r /sys/class/power_supply/bd71827_bat/capacity
for layout in portrait-v1 landscape-v1; do
    for tile in time-0000.png sleep-time-0000.png 0-0.png 100-1.png; do
        check test -s "$BASE/layout-assets/$layout/$tile"
    done
done
check test -s "$BASE/server-url.txt"
echo 'This client targets PW4 (1072x1448). Passing checks does not prove hardware compatibility.'
exit "$fail"
