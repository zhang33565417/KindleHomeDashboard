#!/bin/sh
BASE=/mnt/us/extensions/kindle-dashboard
boot_prop() {
    timeout -t 2 lipc-get-prop "com.lab126.$1" "$2" 2>/dev/null
}
native_ready() {
    stage=framework
    [ "$(boot_prop kaf frameworkStarted)" = 1 ] || return 1
    stage=window-manager
    [ "$(boot_prop winmgr winmgrReady)" = 1 ] || return 1
    stage=power-service
    boot_prop powerd battLevel >/dev/null
}
{
    echo "=== Boot: $(date) ==="
    count=0 stable=0 last_stage=
    # Cap both the number of checks and each IPC call, even if the clock changes.
    while [ "$count" -lt 20 ]; do
        if native_ready; then
            stable=$((stable+1))
            if [ "$stable" -ge 2 ]; then
                echo "Native framework and window manager ready; starting dashboard: $(date)"
                sh "$BASE/control.sh" dashboard boot
                result=$?
                echo "Boot startup finished: $result"
                exit "$result"
            fi
            last_stage=
        else
            stable=0
            if [ "$last_stage" != "$stage" ]; then
                echo "Waiting for native startup: stage=$stage"
                last_stage=$stage
            fi
        fi
        count=$((count+1))
        [ "$count" -ge 20 ] || sleep 3
    done
    echo "Boot startup skipped: native readiness timed out (stage=${last_stage:-stability}); leaving native UI"
} >> "$BASE/boot-start.log" 2>&1
