#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
exec 8>"$RUN/control.lock"
flock -x 8 || exit 1
stop_worker "$BASE/auto-refresh.pid"
stop_worker "$RUN/auto-refresh.pid"
touch "$RUN/network-paused"
log 'Auto refresh stopped manually'
