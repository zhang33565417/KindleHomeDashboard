#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/layout-common.sh"
# Only one listener may grab the input device at a time, even during restart.
exec 7>"$RUN/touch.lock"
flock -xn 7 || exit 0
is_dashboard && [ ! -s "$RUN/exit-request" ] || exit 0
fifo="$RUN/touch-$$.fifo"
load_frame || { log 'Touch disabled: no validated layout'; exit 1; }
cleanup() {
    [ -z "$reader" ] || kill "$reader" 2>/dev/null
    [ -z "$parser" ] || kill "$parser" 2>/dev/null
    rm -f "$fifo"
}
trap 'cleanup; exit' INT TERM EXIT
device=''
for entry in /sys/class/input/event*/device/name; do
    if [ "$(cat "$entry")" = goodix-ts ]; then
        node=${entry%/device/name}; device=/dev/input/${node##*/}; break
    fi
done
[ -n "$device" ] || { log 'Touch device missing'; exit 1; }
mkfifo "$fifo" || exit 1
# Exclusive grab prevents taps from activating the native UI behind the PNG.
/mnt/us/usbnet/bin/evtest --grab "$device" 7>&- > "$fifo" 2>> "$BASE/touch-loop.log" &
reader=$!
awk -v request="$RUN/exit-request" -v minx="$K_home_x" -v maxx="$((K_home_x+K_home_w-1))" \
    -v miny="$K_home_y" -v maxy="$((K_home_y+K_home_h-1))" -f "$BASE/long-press.awk" 7>&- < "$fifo" &
parser=$!
log "Touch listener started: $device"
# Block on input rather than polling: idle touch handling consumes no CPU.
wait "$parser"
parser=''
if is_dashboard && [ -s "$RUN/exit-request" ]; then
    log "Home button: $(cat "$RUN/exit-request")"
    cleanup
    exec 7>&-
    exec sh "$BASE/control.sh" home tap
fi
log 'Touch input stopped without a Home request'
