#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/power-common.sh"
. "$BASE/layout-common.sh"
exec 8>"$RUN/control.lock"
flock -x 8 || exit 1
native_prop() {
    timeout -t 2 lipc-get-prop "com.lab126.$1" "$2" 2>/dev/null
}
native_ready() {
    ready_stage=framework
    [ "$(native_prop kaf frameworkStarted)" = 1 ] || return 1
    ready_stage=window-manager
    [ "$(native_prop winmgr winmgrReady)" = 1 ] || return 1
    ready_stage=power-state
    [ "$(native_prop powerd state)" = active ]
}
home_foreground() {
    local value
    ready_stage=foreground-app
    [ "$(native_prop appmgrd activeApp)" = com.lab126.booklet.home ] || return 1
    ready_stage=foreground-window
    value=$(native_prop winmgr getActiveAppTitle) || return 1
    case "$value" in *application_ID:com.lab126.booklet.home_*) ;; *) return 1;; esac
    return 0
}
restore_home() {
    local restored=0 ready_stage deadline last_stage= stable=0 requested=0 checks=0
    log 'Restoring native Home'
    echo home > "$RUN/mode"
    stop_worker "$BASE/auto-refresh.pid"
    for name in power-loop auto-refresh status-loop touch-loop power-events window-worker; do
        stop_worker "$RUN/$name.pid"
    done
    clear_owned_alarms
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
    # Compatibility with devices upgrading from the framebuffer-only version.
    timeout -t 3 lipc-set-prop com.lab126.powerd preventScreenSaver 0 || { log 'Native sleep restore failed or timed out'; restored=1; }
    timeout -t 2 lipc-set-prop com.lab126.pillow disableEnablePillow enable || log 'Native Pillow enable did not complete'
    restore_light || { log 'Native frontlight restore failed or timed out'; restored=1; }
    deadline=$(($(date +%s)+90))
    while :; do
        if native_ready; then
            stable=$((stable+1))
            if [ "$stable" -ge 2 ]; then
                home_foreground && break
                if [ "$requested" = 0 ]; then
                    requested=1
                    timeout -t 4 lipc-set-prop com.lab126.appmgrd start app://com.lab126.booklet.home ||
                        log 'Native Home launch did not acknowledge; leaving recovery to native UI'
                fi
            fi
        else
            stable=0
        fi
        if [ "$last_stage" != "$ready_stage" ]; then
            log "Waiting for native Home: stage=$ready_stage"
            last_stage=$ready_stage
        fi
        checks=$((checks+1))
        if [ "$(date +%s)" -ge "$deadline" ] || [ "$checks" -ge 90 ]; then
            log "Native Home readiness timed out: stage=$ready_stage"
            restored=1
            break
        fi
        sleep 1
    done
    # Home can repaint its body without replacing the native toolbar pixels.
    # Ask X to expose the native windows again after releasing the framebuffer.
    if command -v xrefresh >/dev/null 2>&1; then
        DISPLAY=:0 timeout -t 4 xrefresh || { log 'Native Home redraw did not complete'; restored=1; }
    fi
    "$BASE/bin/fbink" -q -w -s top=0,left=0,width=1072,height=1448 ||
        { log 'Native panel refresh did not complete'; restored=1; }
    rm -f "$RUN/exit-request" "$RUN/status-key" "$RUN/calendar-day" "$RUN/calendar-clock" "$RUN/clock-key" "$RUN/profile" "$RUN/manual-until" "$RUN/policy-test" "$RUN/power-button-time" "$RUN/window-request" "$RUN/window-ack"
    if [ "$restored" = 0 ]; then
        log 'Native Home ready in foreground; normal sleep restored'
    else
        log 'Native Home recovery incomplete; see failed stage above'
    fi
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
    # Wake powerd before mapping the dashboard surface.
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
    start_worker window-worker
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
