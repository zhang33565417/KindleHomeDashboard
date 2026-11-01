#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/layout-common.sh"
load_frame || { log 'Touch disabled: no validated layout'; exit 1; }
cleanup() {
    [ -z "$reader" ] || kill "$reader" 2>/dev/null
    [ -z "$parser" ] || kill "$parser" 2>/dev/null
    rm -f "$RUN/touch.fifo"
}
trap 'cleanup; exit' INT TERM EXIT
device=''
for entry in /sys/class/input/event*/device/name; do
    if [ "$(cat "$entry")" = goodix-ts ]; then
        node=${entry%/device/name}; device=/dev/input/${node##*/}; break
    fi
done
[ -n "$device" ] || { log 'Touch device missing'; exit 1; }
rm -f "$RUN/exit-request" "$RUN/touch.fifo"
mkfifo "$RUN/touch.fifo" || exit 1
# Exclusive grab prevents taps from activating the native UI behind the PNG.
/mnt/us/usbnet/bin/evtest --grab "$device" > "$RUN/touch.fifo" 2>> "$BASE/touch-loop.log" &
reader=$!
awk -v request="$RUN/exit-request" -v minx="$K_home_x" -v maxx="$((K_home_x+K_home_w-1))" \
    -v miny="$K_home_y" -v maxy="$((K_home_y+K_home_h-1))" -f "$BASE/long-press.awk" < "$RUN/touch.fifo" &
parser=$!
log "Touch listener started: $device"
# Block on input rather than polling: idle touch handling consumes no CPU.
wait "$parser"
parser=''
if is_dashboard && [ -s "$RUN/exit-request" ]; then
    log "Home button: $(cat "$RUN/exit-request")"
    cleanup
    # Become the controller after releasing our worker identity. This avoids
    # killing the transition itself when Home stops the other workers.
    rm -f "$RUN/touch-loop.pid"
    exec sh "$BASE/control.sh" home
fi
log 'Touch input stopped without a Home request'
