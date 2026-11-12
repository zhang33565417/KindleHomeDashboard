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
if [ -n "$(sed '/^0$/d' /sys/class/rtc/rtc0/wakealarm)" ]; then
    rm -f "$RUN/rtc0-wakeup-before"
    log 'RTC0 already armed by native UI; leaving its alarm intact'
    exit 1
fi
touch "$RUN/sleep-armed"
echo enabled > /sys/class/rtc/rtc0/device/power/wakeup
# RTC1 alone did not wake this PW4; arm the verified PMIC alarm at the actual deadline.
echo +$duration > /sys/class/rtc/rtc0/wakealarm || exit 1
cat /sys/class/rtc/rtc0/wakealarm > "$RUN/rtc0-dashboard-alarm"
resume_hash=''
if [ "$(cat "$RUN/profile")" = day ]; then
    resume_hash=$(dd if=/dev/fb0 bs=1088 count=1448 2>/dev/null | md5sum)
fi
before=$(date +%s)
echo $((before+duration)) > "$RUN/wake-deadline"
log "Deep sleep: requested=$duration seconds profile=$(cat "$RUN/profile")"
[ "$(cat "$RUN/profile")" = day ] || sync
echo mem > /sys/power/state 2>> "$BASE/suspend.log"
result=$?
after=$(date +%s)
log "Deep wake: elapsed=$((after-before)) seconds result=$result"
echo "$before $after $result" > "$RUN/last-sleep"
sleep 2
if is_dashboard; then
    rm -f "$RUN/resume-clean"
    if [ -n "$resume_hash" ] && [ "$resume_hash" = "$(dd if=/dev/fb0 bs=1088 count=1448 2>/dev/null | md5sum)" ]; then
        touch "$RUN/resume-clean"
    fi
    touch "$RUN/redraw-after-wake"
fi
exit "$result"
