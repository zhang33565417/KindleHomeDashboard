#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/layout-common.sh
exec 6>"$RUN/download.lock"
flock -x 6 || exit 1
mkdir -p "$BASE/frames"
stage="$BASE/frames/.stage-$$"; archive="$RUN/frame-$$.zip"
mkdir "$stage" || exit 1
cleanup() {
    rm -f "$archive" "$stage/frame.png" "$stage/manifest.txt" "$stage/clock-background.png" "$stage/battery-background.png" "$stage/validated"
    rmdir "$stage" 2>/dev/null || :
}
trap cleanup EXIT
trap 'exit 1' INT TERM
exec >> "$BASE/dashboard.log" 2>&1
echo "=== Frame download $(date) ==="
if [ -n "${1:-}" ]; then cp "$1" "$archive" || exit 1
else
    suffix=''; [ ! -f "$RUN/content-only" ] || suffix='?contentOnly=true'
    if ! wget -q -T 25 -O "$archive" "$(api_base)/api/kindle/frame$suffix"; then
        log 'Frame endpoint unavailable; keeping last supported frame'; exit 1
    fi
fi
# Exact entry extraction prevents an archive from writing arbitrary paths.
for entry in manifest.txt frame.png clock-background.png battery-background.png; do
    unzip -p "$archive" "$entry" > "$stage/$entry" || exit 1
    [ -s "$stage/$entry" ] || exit 1
done
validate_manifest "$stage/manifest.txt" "$stage/validated" || { log 'Rejected invalid frame manifest'; exit 1; }
png_dimensions "$stage/frame.png" 1072 1448 &&
png_dimensions "$stage/clock-background.png" "$K_clock_bg_w" "$K_clock_bg_h" &&
png_dimensions "$stage/battery-background.png" "$K_battery_w" "$K_battery_h" || { log 'Rejected frame PNG dimensions'; exit 1; }
[ -s "$BASE/layout-assets/$K_layout/time-0000.png" ] &&
[ -s "$BASE/layout-assets/$K_layout/sleep-time-0000.png" ] &&
[ -s "$BASE/layout-assets/$K_layout/0-0.png" ] || { log 'Unsupported frame asset revision'; exit 1; }
new_name="frame-$K_version-$K_layout-$$"; bundle_version=$K_version
rm -f "$stage/validated"
exec 9>"$RUN/display.lock"
flock -x 9 || exit 1
is_dashboard || exit 1
old_name=$(cat "$BASE/frame-current" 2>/dev/null)
stop_worker "$RUN/touch-loop.pid"
mv "$stage" "$BASE/frames/$new_name" || exit 1
printf '%s\n' "$new_name" > "$BASE/frame-current.tmp"
mv "$BASE/frame-current.tmp" "$BASE/frame-current" || exit 1
rm -f "$RUN/layout-frame" "$RUN/layout-state" "$RUN/status-key" "$RUN/clock-key"
if ! sh "$BASE/render-frame.sh"; then
    log 'Frame render failed; reverting previous generation'
    if valid_frame_name "$old_name"; then
        printf '%s\n' "$old_name" > "$BASE/frame-current.tmp"; mv "$BASE/frame-current.tmp" "$BASE/frame-current"
        rm -f "$RUN/layout-frame" "$RUN/layout-state" "$RUN/status-key" "$RUN/clock-key"
        sh "$BASE/render-frame.sh" || :
    else
        rm -f "$BASE/frame-current" "$RUN/layout-frame" "$RUN/layout-state"
    fi
    resume_frame_touch; exit 1
fi
echo "$bundle_version" > "$BASE/current-version.txt"
if valid_frame_name "$old_name"; then echo "$old_name" > "$BASE/frame-previous"; fi
resume_frame_touch
for directory in "$BASE"/frames/frame-*; do
    candidate=${directory##*/}
    valid_frame_name "$candidate" || continue
    [ "$candidate" != "$new_name" ] && [ "$candidate" != "$old_name" ] || continue
    rm -f "$directory/frame.png" "$directory/manifest.txt" "$directory/clock-background.png" "$directory/battery-background.png"
    rmdir "$directory" 2>/dev/null || :
done
log "Frame committed: $bundle_version $K_layout"
