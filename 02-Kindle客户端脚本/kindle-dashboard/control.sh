#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/power-common.sh"
. "$BASE/layout-common.sh"
exec 8>"$RUN/control.lock"
flock -x 8 || exit 1
restore_home() {
    local restored=0
    echo home > "$RUN/mode"
    stop_worker "$BASE/auto-refresh.pid"
    for name in power-loop auto-refresh status-loop touch-loop power-events; do
        stop_worker "$RUN/$name.pid"
    done
    clear_owned_alarms
    restore_light
    # Give an in-flight draw a short window. A stuck draw must not strand Home.
    exec 9>"$RUN/display.lock"
    attempt=0
    until flock -xn 9; do
        if [ "$attempt" -ge 3 ]; then
            log 'Display lock still held; restoring native Home anyway'
            break
        fi
        sleep 1; attempt=$((attempt+1))
    done
    killall -CONT awesome pillowd cvm 2>/dev/null
    lipc-set-prop com.lab126.powerd preventScreenSaver 0 || { log 'Native sleep restore failed'; restored=1; }
    lipc-set-prop com.lab126.appmgrd start app://com.lab126.booklet.home || { log 'Native Home launch failed'; restored=1; }
    # Pillow sometimes times out. It must not delay launching the native Home.
    timeout -t 2 lipc-set-prop com.lab126.pillow disableEnablePillow enable || log 'Native Pillow enable did not complete'
    # Home can repaint its body without replacing the native toolbar pixels.
    # Ask X to expose the native windows again after releasing the framebuffer.
    if command -v xrefresh >/dev/null 2>&1; then
        DISPLAY=:0 timeout -t 4 xrefresh || log 'Native Home redraw did not complete'
    fi
    rm -f "$RUN/exit-request" "$RUN/status-key" "$RUN/calendar-day" "$RUN/calendar-clock" "$RUN/clock-key" "$RUN/profile" "$RUN/manual-until" "$RUN/policy-test" "$RUN/power-button-time"
    if [ "$restored" = 0 ]; then log 'Returned to native home; normal sleep restored'; fi
    return "$restored"
}
fail_dashboard_entry() {
    log "$1"
    # Roll back under the same control lock; another entry cannot race cleanup.
    restore_home
    exit 1
}
case "${1:-}" in
dashboard)
    if ! is_dashboard; then rm -f "$RUN/exit-request"; fi
    echo dashboard > "$RUN/mode"
    stop_worker "$BASE/auto-refresh.pid"
    stop_worker "$RUN/auto-refresh.pid"
    stop_worker "$RUN/status-loop.pid"
    if ! load_frame && ! sh "$BASE/dashboard.sh"; then
        fail_dashboard_entry 'No compatible cache or server frame; returning to native Home'
    fi
    lipc-set-prop com.lab126.powerd preventScreenSaver 1 ||
        fail_dashboard_entry 'Could not keep Kindle awake; returning to native Home'
    # A pending native screen saver is not cancelled by preventScreenSaver.
    # Wake powerd before pausing the native UI, including KUAL/power-key races.
    killall -CONT awesome cvm pillowd 2>/dev/null
    if [ "$(lipc-get-prop com.lab126.powerd state 2>/dev/null)" != active ]; then
        lipc-set-prop com.lab126.powerd wakeUp 1 ||
            fail_dashboard_entry 'Native wake request failed; returning to Home'
        attempt=0
        while [ "$(lipc-get-prop com.lab126.powerd state 2>/dev/null)" != active ] && [ "$attempt" -lt 5 ]; do
            sleep 1; attempt=$((attempt+1))
        done
        if [ "$(lipc-get-prop com.lab126.powerd state 2>/dev/null)" != active ]; then
            fail_dashboard_entry 'Native wake failed; returning to Home instead of drawing over screen saver'
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
    sh "$BASE/display.sh" || fail_dashboard_entry 'Dashboard display failed; returning to native Home'
    start_worker power-events
    case "$profile" in day|night) :;; *) start_worker touch-loop;; esac
    start_worker power-loop
    log 'Entered dashboard mode'
    ;;
home)
    # Both touch and the power loop can handle a tap; a completed request is a no-op.
    if [ "${2:-}" = tap ] && [ ! -s "$RUN/exit-request" ]; then exit 0; fi
    restore_home
    ;;
*) echo 'Usage: control.sh dashboard|home' >&2; exit 2;;
esac
