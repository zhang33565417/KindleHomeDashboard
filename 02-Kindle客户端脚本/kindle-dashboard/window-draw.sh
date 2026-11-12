#!/bin/sh
# Caller holds display.lock. Arguments: refresh rectangle, then any image layers.
. /mnt/us/extensions/kindle-dashboard/common.sh
is_dashboard || exit 1
temp=$(mktemp "$RUN/window-draw.XXXXXX") || exit 1
trap 'rm -f "$temp"' EXIT
token=$$
{
    printf '%s\n%s %s %s %s\n' "$token" "$1" "$2" "$3" "$4"
    shift 4
    while [ "$#" -ge 5 ]; do
        printf '%s %s %s %s %s\n' "$1" "$2" "$3" "$4" "$5"
        shift 5
    done
} > "$temp" || exit 1
[ "$#" = 0 ] || exit 1
rm -f "$RUN/window-ack"
mv -f "$temp" "$RUN/window-request" || exit 1
n=0
while [ "$n" -lt 50 ] && is_dashboard; do
    read -r received status 2>/dev/null < "$RUN/window-ack" || received=
    if [ "$received" = "$token" ]; then [ "$status" = ok ]; exit $?; fi
    sleep 0.2
    n=$((n+1))
done
log 'Window did not acknowledge drawing'
exit 1
