#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/power-common.sh"
. "$BASE/layout-common.sh"
exec 8>"$RUN/control.lock"
flock -x 8 || exit 1
case "${1:-}" in
dashboard)
    echo dashboard > "$RUN/mode"
    stop_worker "$BASE/auto-refresh.pid"
    stop_worker "$RUN/auto-refresh.pid"
    stop_worker "$RUN/status-loop.pid"
    if ! load_frame && ! sh "$BASE/dashboard.sh"; then
        log 'No compatible cache or server frame; returning to native Home'
        exec 8>&-
        exec sh "$BASE/control.sh" home
    fi
    lipc-set-prop com.lab126.powerd preventScreenSaver 1 || exit 1
    # A pending native screen saver is not cancelled by preventScreenSaver.
    # Wake powerd before pausing the native UI, including KUAL/power-key races.
    killall -CONT awesome cvm pillowd 2>/dev/null
    if [ "$(lipc-get-prop com.lab126.powerd state 2>/dev/null)" != active ]; then
        lipc-set-prop com.lab126.powerd wakeUp 1 || exit 1
        attempt=0
        while [ "$(lipc-get-prop com.lab126.powerd state 2>/dev/null)" != active ] && [ "$attempt" -lt 5 ]; do
            sleep 1; attempt=$((attempt+1))
        done
        if [ "$(lipc-get-prop com.lab126.powerd state 2>/dev/null)" != active ]; then
            log 'Native wake failed; returning to Home instead of drawing over screen saver'
            exec 8>&-
            exec sh "$BASE/control.sh" home
        fi
    fi
    lipc-set-prop com.lab126.pillow disableEnablePillow disable
    killall -STOP awesome pillowd cvm 2>/dev/null
    read_power
    now=$(date +%s); second=$(((now+28800)%86400))
    latched=$(cat "$RUN/low-latched" 2>/dev/null); [ "$latched" = 1 ] || latched=0
    choose_profile "$second" "$level" "$external" "$latched" 0
    if [ "${2:-}" != boot ]; then
        case "$profile" in day|night)
            echo $((now+MANUAL_WAKE_GRACE)) > "$RUN/manual-until"; profile=interactive;;
        esac
    fi
    echo "$profile" > "$RUN/profile"
    # Show cached content immediately; dates remain in the original server PNG.
    sh "$BASE/display.sh" || :
    start_worker power-events
    case "$profile" in day|night) :;; *) start_worker touch-loop;; esac
    start_worker power-loop
    log 'Entered dashboard mode'
    ;;
home)
    echo home > "$RUN/mode"
    stop_worker "$BASE/auto-refresh.pid"
    for name in auto-refresh status-loop touch-loop power-loop power-events; do
        stop_worker "$RUN/$name.pid"
    done
    clear_owned_alarms
    restore_light
    # Wait for an in-flight display before handing the screen back to Kindle.
    exec 9>"$RUN/display.lock"
    flock -x 9 || exit 1
    killall -CONT awesome pillowd cvm 2>/dev/null
    lipc-set-prop com.lab126.pillow disableEnablePillow enable
    lipc-set-prop com.lab126.powerd preventScreenSaver 0
    lipc-set-prop com.lab126.appmgrd start app://com.lab126.booklet.home
    rm -f "$RUN/exit-request" "$RUN/status-key" "$RUN/calendar-day" "$RUN/calendar-clock" "$RUN/clock-key" "$RUN/profile" "$RUN/manual-until" "$RUN/policy-test" "$RUN/power-button-time"
    log 'Returned to native home; normal sleep restored'
    ;;
*) echo 'Usage: control.sh dashboard|home' >&2; exit 2;;
esac
