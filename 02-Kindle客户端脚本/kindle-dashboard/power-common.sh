#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/power.conf
. /mnt/us/extensions/kindle-dashboard/power-policy.sh
read_power() {
    external=0; level=50
    if [ -r /sys/class/power_supply/bd71827_ac/online ]; then
        read -r external < /sys/class/power_supply/bd71827_ac/online
    fi
    if [ -r /sys/class/power_supply/bd71827_bat/capacity ]; then
        read -r level < /sys/class/power_supply/bd71827_bat/capacity
    else level=$(lipc-get-prop com.lab126.powerd battLevel 2>/dev/null)
    fi
    case "$level" in ''|*[!0-9]*) level=50;; esac
    [ "$external" = 1 ] || external=0
}
restore_light() {
    if [ -s "$RUN/saved-light" ]; then
        saved_light=$(cat "$RUN/saved-light")
        timeout -t 3 lipc-set-prop com.lab126.powerd flIntensity "$saved_light" 2>/dev/null || return 1
        rm -f "$RUN/saved-light"
    fi
}
dim_light() {
    if [ ! -s "$RUN/saved-light" ]; then
        lipc-get-prop com.lab126.powerd flIntensity > "$RUN/saved-light"
    fi
    lipc-set-prop com.lab126.powerd flIntensity 0 2>/dev/null
}
clear_owned_alarms() {
    if [ -f "$RUN/sleep-armed" ]; then
        owned_alarm=$(cat "$RUN/rtc0-dashboard-alarm" 2>/dev/null)
        if [ -n "$owned_alarm" ] && [ "$(cat /sys/class/rtc/rtc0/wakealarm)" = "$owned_alarm" ]; then
            echo 0 > /sys/class/rtc/rtc0/wakealarm
        fi
        old_wakeup=$(cat "$RUN/rtc0-wakeup-before" 2>/dev/null)
        case "$old_wakeup" in enabled|disabled) echo "$old_wakeup" > /sys/class/rtc/rtc0/device/power/wakeup;; esac
        rm -f "$RUN/sleep-armed" "$RUN/rtc0-wakeup-before" "$RUN/rtc0-dashboard-alarm"
    fi
}
