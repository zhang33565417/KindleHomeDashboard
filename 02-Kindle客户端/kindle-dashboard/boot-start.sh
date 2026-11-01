#!/bin/sh
BASE=/mnt/us/extensions/kindle-dashboard
{
    echo "=== Boot: $(date) ==="
    sleep 35
    count=0
    while [ "$count" -lt 10 ]; do
        lipc-get-prop com.lab126.powerd battLevel >/dev/null 2>&1 && break
        count=$((count + 1)); sleep 5
    done
    sh "$BASE/control.sh" dashboard boot
    echo "Boot startup finished: $?"
} >> "$BASE/boot-start.log" 2>&1
