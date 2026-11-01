#!/bin/sh
base=${1:-/mnt/us/extensions/kindle-dashboard}
. "$base/power.conf"
. "$base/power-policy.sh"
fail=0; count=0
while read -r label seconds battery plugged latch interactive expected expected_latch expected_interval expected_boundary; do
    choose_profile "$seconds" "$battery" "$plugged" "$latch" "$interactive"
    profile_interval "$profile"; until_boundary "$seconds"
    count=$((count+1))
    if [ "$profile:$latched:$interval:$boundary" != "$expected:$expected_latch:$expected_interval:$expected_boundary" ]; then
        echo "FAIL $label actual=$profile:$latched:$interval:$boundary"; fail=1
    else echo "PASS $label"; fi
done <<'CASES'
before-night 84599 30 0 0 0 day 0 600 1
night-start 84600 30 0 0 0 night 0 3600 25200
before-day 23399 30 0 0 0 night 0 3600 1
day-start 23400 30 0 0 0 day 0 600 61200
midnight 0 30 0 0 0 night 0 3600 23400
low-ignored 43200 5 0 1 0 day 0 600 41400
full-battery 43200 100 0 0 0 day 0 600 41400
plugged-night 84600 30 1 0 0 fast 0 30 25200
plugged-full 43200 100 1 0 0 fast 0 30 41400
manual-night 84600 30 0 0 1 interactive 0 120 25200
manual-day 43200 1 0 1 1 interactive 0 120 41400
plugged-manual 84600 30 1 0 1 fast 0 30 25200
CASES
echo "Policy cases=$count result=$fail"
exit "$fail"
