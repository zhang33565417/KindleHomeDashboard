#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/layout-common.sh
VERSION_URL="$(api_base)/api/version"
log 'Auto refresh started'
while is_dashboard; do
    if wget -q -T 10 -O "$RUN/version-response.tmp" "$VERSION_URL"; then
        version=$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$RUN/version-response.tmp")
        old=$(cat "$BASE/current-version.txt" 2>/dev/null)
        if [ -n "$version" ] && [ "$version" != "$old" ]; then
            if sh "$BASE/dashboard.sh" && is_dashboard && sh "$BASE/display.sh" && is_dashboard; then
                echo "$version" > "$BASE/current-version.txt"
                log "Dashboard updated: $version"
            else
                log 'Dashboard update interrupted or failed; retry next cycle'
            fi
        fi
    else
        log 'Version check failed; retaining cached image'
    fi
    sleep 30
done
