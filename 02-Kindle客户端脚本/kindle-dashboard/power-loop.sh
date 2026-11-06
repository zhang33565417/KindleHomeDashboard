#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
. "$BASE/power-common.sh"
. "$BASE/layout-common.sh"
latched=$(cat "$RUN/low-latched" 2>/dev/null); [ "$latched" = 1 ] || latched=0
network_due=0; battery_due=0; failures=0; previous=''
network_sync() {
    [ ! -f "$RUN/network-paused" ] || return 0
    date +%s > "$RUN/last-network-check"
    tries=0; version=''
    while [ "$tries" -lt 2 ] && is_dashboard; do
        if wget -q -T 8 -O "$RUN/version-response.tmp" "$(api_base)/api/version"; then
            version=$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$RUN/version-response.tmp")
            content_version=$(sed -n 's/.*"kindleVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$RUN/version-response.tmp")
            if [ -n "$content_version" ]; then version=$content_version; echo 1 > "$RUN/content-only"
            else rm -f "$RUN/content-only"; fi
            [ -z "$version" ] || break
        fi
        tries=$((tries+1)); [ "$tries" -ge 2 ] || sleep 3
    done
    if [ -z "$version" ]; then log 'Network unavailable; cached board retained'; return 1; fi
    old=$(cat "$BASE/current-version.txt" 2>/dev/null)
    if [ "$version" != "$old" ] || [ ! -s "$BASE/frame-current" ]; then
        if sh "$BASE/dashboard.sh" && is_dashboard; then
            log "Dashboard updated: $(cat "$BASE/current-version.txt")"
        else return 1
        fi
    fi
}
draw_status() {
    (exec 9>"$RUN/display.lock"; flock -x 9 || exit 1
     if [ "$1" = clock ]; then STATUS_CLOCK_ONLY=1 sh "$BASE/status.sh"
     else sh "$BASE/status.sh" "${1:-}"
     fi)
}
handle_home_request() {
    [ ! -s "$RUN/exit-request" ] || exec sh "$BASE/control.sh" home tap
}
while is_dashboard; do
    handle_home_request
    now=$(date +%s); second=$(((now+28800)%86400))
    read_power
    manual_until=$(cat "$RUN/manual-until" 2>/dev/null); [ -n "$manual_until" ] || manual_until=0
    key_time=$(cat "$RUN/power-button-time" 2>/dev/null); [ -n "$key_time" ] || key_time=0
    if [ $((key_time+MANUAL_WAKE_GRACE)) -gt "$manual_until" ]; then
        manual_until=$((key_time+MANUAL_WAKE_GRACE)); echo "$manual_until" > "$RUN/manual-until"
    fi
    manual=0; [ "$now" -ge "$manual_until" ] || manual=1
    choose_profile "$second" "$level" "$external" "$latched" "$manual"
    echo "$latched" > "$RUN/low-latched"
    # Temporary real-device replay; bounded by expiry and never survives a reboot.
    test_until=0
    if [ -s "$RUN/policy-test" ]; then
        read -r test_profile test_interval test_until < "$RUN/policy-test"
        if [ "$now" -lt "$test_until" ] && [ "$external" != 1 ] && [ "$manual" != 1 ]; then
            case "$test_profile" in day|night) profile=$test_profile;; esac
        else rm -f "$RUN/policy-test"; test_until=0
        fi
    fi
    profile_interval "$profile"
    if [ "$test_until" -gt "$now" ] && [ "$external" != 1 ] && [ "$manual" != 1 ]; then interval=$test_interval; fi
    echo "$interval" > "$RUN/network-interval"
    start_worker power-events
    if [ -f "$RUN/force-network" ]; then network_due=0; rm -f "$RUN/force-network"; fi
    if [ "$profile" != "$previous" ]; then
        echo "$profile" > "$RUN/profile"
        log "Power profile: $previous -> $profile battery=$level external=$external"
        case "$profile" in
            day|night) stop_worker "$RUN/touch-loop.pid"; dim_light;;
            *) restore_light; start_worker touch-loop;;
        esac
        network_due=0; battery_due=0; draw_status force; previous=$profile
    fi
    if [ "$now" -ge "$network_due" ]; then
        if network_sync; then failures=0
        else failures=$((failures+1)); fi
        now=$(date +%s)
        delay=$interval
        if [ "$profile" = day ] && [ "$failures" -gt 0 ]; then
            n=$failures
            while [ "$n" -gt 0 ] && [ "$delay" -lt 3600 ]; do delay=$((delay*2)); n=$((n-1)); done
            [ "$delay" -le 3600 ] || delay=3600
        fi
        network_due=$((now+delay))
        if [ "$profile" = day ] && [ "$failures" = 0 ]; then
            network_due=$(((now/600+1)*600))
        fi
        echo "$network_due" > "$RUN/next-network-check"
    fi
    is_dashboard || break
    handle_home_request
    if [ -f "$RUN/redraw-after-wake" ]; then
        # Only skip the full recovery when resume left the framebuffer intact.
        if [ "$profile" = day ] && [ -f "$RUN/resume-clean" ]; then
            draw_status force
            rm -f "$RUN/redraw-after-wake" "$RUN/resume-clean"
        else sh "$BASE/display.sh"; fi
    fi
    case "$profile" in
        day|night)
            # A slow Wi-Fi reconnect may cross 06:30 or coincide with USB/key wake.
            # Re-evaluate immediately before suspend, not just before networking.
            expected_profile=$profile
            now=$(date +%s); second=$(((now+28800)%86400)); read_power
            recent_key=$(cat "$RUN/power-button-time" 2>/dev/null); [ -n "$recent_key" ] || recent_key=0
            manual_now=0
            [ "$now" -ge "$manual_until" ] || manual_now=1
            [ "$now" -ge $((recent_key+MANUAL_WAKE_GRACE)) ] || manual_now=1
            choose_profile "$second" "$level" "$external" "$latched" "$manual_now"
            if [ "$test_until" -gt "$now" ] && [ "$external" != 1 ] && [ "$manual_now" != 1 ]; then profile=$test_profile; fi
            [ "$profile" = "$expected_profile" ] || continue
            stop_worker "$RUN/touch-loop.pid"
            dim_light
            draw_status force
            now=$(date +%s); second=$(((now+28800)%86400)); until_boundary "$second"
            duration=$((network_due-now))
            # Clock keeps its ten-minute cadence even during network backoff/pause.
            if [ "$profile" = day ]; then
                clock_wait=$((600-now%600))
                [ "$duration" -le "$clock_wait" ] || duration=$clock_wait
            fi
             [ "$duration" -le "$boundary" ] || duration=$boundary
            if [ "$test_until" -gt 0 ] && [ "$duration" -gt $((test_until-now)) ]; then duration=$((test_until-now)); fi
            if [ "$duration" -ge 5 ]; then
                handle_home_request
                if ! sh "$BASE/sleep-cycle.sh" "$duration"; then
                    log 'Suspend failed; returning to native Home'; rm -f "$RUN/power-loop.pid"; exec sh "$BASE/control.sh" home
                fi
            else sleep 2
            fi
            ;;
        *)
            start_worker touch-loop
            if [ "$now" -ge "$battery_due" ]; then draw_status; battery_due=$((now+BATTERY_INTERVAL))
            else draw_status clock
            fi
            now=$(date +%s); second=$(((now+28800)%86400)); until_boundary "$second"
            wait=$((60-now%60)); [ "$wait" -le 15 ] || wait=15
            remaining=$((network_due-now)); [ "$remaining" -ge "$wait" ] || wait=$remaining
            [ "$boundary" -ge "$wait" ] || wait=$boundary
            [ "$wait" -gt 0 ] || wait=1
            sleep "$wait"
            ;;
    esac
done
