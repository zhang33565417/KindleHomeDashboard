#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
is_dashboard || exit 1
exec 8>"$RUN/control.lock"
flock -x 8 || exit 1
rm -f "$RUN/network-paused"
touch "$RUN/force-network"
start_worker power-events
start_worker power-loop
