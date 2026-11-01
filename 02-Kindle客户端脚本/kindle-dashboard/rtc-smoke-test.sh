#!/bin/sh
# Controlled short test; the second RTC is an independent 90-second rescue alarm.
. /mnt/us/extensions/kindle-dashboard/common.sh
exec >> "$BASE/rtc-smoke-test.log" 2>&1
echo "=== RTC test $(date) ==="
old_wakeup=$(cat /sys/class/rtc/rtc0/device/power/wakeup)
cleanup() {
    echo 0 > /sys/class/rtc/rtc0/wakealarm
    echo "$old_wakeup" > /sys/class/rtc/rtc0/device/power/wakeup
    start_worker status-loop
    start_worker auto-refresh
    start_worker touch-loop
}
trap cleanup EXIT
for name in status-loop auto-refresh touch-loop; do stop_worker "$RUN/$name.pid"; done
echo enabled > /sys/class/rtc/rtc0/device/power/wakeup
echo 0 > /sys/class/rtc/rtc0/wakealarm
echo +90 > /sys/class/rtc/rtc0/wakealarm || exit 1
before=$(date +%s)
echo "before=$before rtc1=$(cat /sys/class/rtc/rtc1/since_epoch)"
rtcwake -u -d /dev/rtc1 -m mem -s 20
result=$?
after=$(date +%s)
echo "after=$after elapsed=$((after-before)) result=$result"
lipc-get-prop com.lab126.powerd state
dmesg | tail -n 45
