#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/power-common.sh"
duration=$1
case "$duration" in ''|*[!0-9]*) exit 2;; esac
[ "$duration" -ge 5 ] && [ "$duration" -le 3600 ] || exit 2
is_dashboard || exit 0
trap clear_owned_alarms EXIT
trap 'exit 1' INT TERM
cat /sys/class/rtc/rtc0/device/power/wakeup > "$RUN/rtc0-wakeup-before"
touch "$RUN/sleep-armed"
echo enabled > /sys/class/rtc/rtc0/device/power/wakeup
echo 0 > /sys/class/rtc/rtc0/wakealarm
echo +$((duration+90)) > /sys/class/rtc/rtc0/wakealarm || exit 1
resume_hash=''
if [ "$(cat "$RUN/profile")" = day ]; then
    resume_hash=$(dd if=/dev/fb0 bs=1088 count=1448 2>/dev/null | md5sum)
fi
before=$(date +%s)
echo $((before+duration)) > "$RUN/wake-deadline"
log "Deep sleep: requested=$duration seconds profile=$(cat "$RUN/profile")"
[ "$(cat "$RUN/profile")" = day ] || sync
rtcwake -u -d /dev/rtc1 -m mem -s "$duration" >> "$BASE/suspend.log" 2>&1
result=$?
after=$(date +%s)
log "Deep wake: elapsed=$((after-before)) seconds result=$result"
echo "$before $after $result" > "$RUN/last-sleep"
sleep 2
if is_dashboard; then
    # Pillow also draws directly on resume; preserve both paused native painters.
    killall -STOP awesome pillowd cvm 2>/dev/null
    rm -f "$RUN/resume-clean"
    if [ -n "$resume_hash" ] && [ "$resume_hash" = "$(dd if=/dev/fb0 bs=1088 count=1448 2>/dev/null | md5sum)" ]; then
        touch "$RUN/resume-clean"
    fi
    touch "$RUN/redraw-after-wake"
fi
exit "$result"
