#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
is_dashboard || exit 1
sh "$BASE/dashboard.sh"
