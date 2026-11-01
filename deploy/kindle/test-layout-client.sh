#!/bin/sh
library=$1; fixtures=$2
. "$library/layout-common.sh"
work="$RUN/layout-tests-$$"; mkdir -p "$work"
fail=0
check() { if "$@"; then echo "PASS $*"; else echo "FAIL $*"; fail=1; fi; }
for layout in portrait landscape; do
    unzip -p "$fixtures/$layout.zip" manifest.txt > "$work/manifest.txt" || exit 1
    check validate_manifest "$work/manifest.txt" "$work/validated"
    for item in frame clock-background battery-background; do unzip -p "$fixtures/$layout.zip" "$item.png" > "$work/$item.png" || exit 1; done
    check png_dimensions "$work/frame.png" 1072 1448
    check png_dimensions "$work/clock-background.png" "$K_clock_bg_w" "$K_clock_bg_h"
    check png_dimensions "$work/battery-background.png" "$K_battery_w" "$K_battery_h"
    x=$((K_home_x+K_home_w/2)); y=$((K_home_y+K_home_h/2))
    for where in house battery; do
        if [ "$where" = battery ]; then x=$((K_battery_x+K_battery_w/2)); y=$((K_battery_y+K_battery_h/2)); fi
        rm -f "$work/request"
        printf 'Event: time 100.0, type 3 (ABS), code 57 (ID), value 120\nEvent: time 100.0, type 3 (ABS), code 53 (X), value %s\nEvent: time 100.0, type 3 (ABS), code 54 (Y), value %s\nEvent: time 100.2, type 3 (ABS), code 57 (ID), value -1\n' "$x" "$y" |
          awk -v request="$work/request" -v minx="$K_home_x" -v maxx="$((K_home_x+K_home_w-1))" -v miny="$K_home_y" -v maxy="$((K_home_y+K_home_h-1))" -f "$library/long-press.awk"
        if [ "$where" = house ]; then check test -s "$work/request"; else check test ! -e "$work/request"; fi
    done
    for bad in unknown injection bounds duplicate; do
        case "$bad" in
            unknown) sed 's/^layout=.*/layout=unknown-v9/' "$work/manifest.txt" > "$work/bad";;
            injection) sed 's/^version=.*/version=$(touch injected)/' "$work/manifest.txt" > "$work/bad";;
            bounds) sed 's/^clock_x=.*/clock_x=99999/' "$work/manifest.txt" > "$work/bad";;
            duplicate) cat "$work/manifest.txt" > "$work/bad"; echo schema=1 >> "$work/bad";;
        esac
        if validate_manifest "$work/bad" "$work/bad-state"; then echo "FAIL accepted $bad"; fail=1
        else echo "PASS rejected $layout $bad"; fi
    done
done
rm -f "$work/manifest.txt" "$work/validated" "$work/frame.png" "$work/clock-background.png" "$work/battery-background.png" "$work/request" "$work/bad" "$work/bad-state"
rmdir "$work"
exit "$fail"
