#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/common.sh
api_base() {
    base=$(cat "$RUN/server-override" 2>/dev/null)
    if printf '%s\n' "$base" | grep -Eq '^http://127\.0\.0\.1:[0-9]{1,5}$'; then printf '%s\n' "$base"
    else
        base=$(cat "$BASE/server-url.txt" 2>/dev/null | tr -d '\r\n')
        case "$base" in
            http://?*|https://?*) printf '%s\n' "${base%/}";;
            *) log 'Set server-url.txt to your dashboard server URL'; return 1;;
        esac
    fi
}
valid_rect() {
    [ "$1" -ge 0 ] && [ "$2" -ge 0 ] && [ "$3" -gt 0 ] && [ "$4" -gt 0 ] &&
    [ "$3" -le 1072 ] && [ "$4" -le 1448 ] && [ $(($1+$3)) -le 1072 ] && [ $(($2+$4)) -le 1448 ]
}
validate_manifest() {
    # Generate local assignments only from whitelisted keys/enums and plain integers.
    # Downloaded shell code is never sourced.
    awk -F= '
    BEGIN {
        n=split("schema version layout rotation width height source_width source_height clock_x clock_y clock_w clock_h clock_bg_x clock_bg_y clock_bg_w clock_bg_h battery_x battery_y battery_w battery_h home_x home_y home_w home_h",keys," ")
        for(i=1;i<=n;i++) allowed[keys[i]]=1
    }
    NF!=2 || !allowed[$1] || seen[$1]++ {bad=1; next}
    {
        if($1=="layout") {if($2!="portrait-v1" && $2!="landscape-v1") bad=1}
        else {if($2 !~ /^(0|[1-9][0-9]*)$/ || length($2)>($1=="version"?20:5)) bad=1}
        values[$1]=$2
    }
    END {
        for(i=1;i<=n;i++) if(!seen[keys[i]]) bad=1
        if(bad) exit 1
        for(i=1;i<=n;i++) print "K_" keys[i] "=" values[keys[i]]
    }' "$1" > "$2" || return 1
    . "$2"
    [ "$K_schema" = 1 ] && [ "$K_width" = 1072 ] && [ "$K_height" = 1448 ] || return 1
    case "$K_layout:$K_rotation" in portrait-v1:0|landscape-v1:90) :;; *) return 1;; esac
    [ "$K_version" != 0 ] || return 1
    [ "$K_source_width" -ge 400 ] && [ "$K_source_width" -le 3000 ] &&
    [ "$K_source_height" -ge 400 ] && [ "$K_source_height" -le 3000 ] || return 1
    [ "$K_clock_x" -ge "$K_clock_bg_x" ] && [ "$K_clock_y" -ge "$K_clock_bg_y" ] &&
    [ $((K_clock_x+K_clock_w)) -le $((K_clock_bg_x+K_clock_bg_w)) ] &&
    [ $((K_clock_y+K_clock_h)) -le $((K_clock_bg_y+K_clock_bg_h)) ] || return 1
    valid_rect "$K_clock_x" "$K_clock_y" "$K_clock_w" "$K_clock_h" &&
    valid_rect "$K_clock_bg_x" "$K_clock_bg_y" "$K_clock_bg_w" "$K_clock_bg_h" &&
    valid_rect "$K_battery_x" "$K_battery_y" "$K_battery_w" "$K_battery_h" &&
    valid_rect "$K_home_x" "$K_home_y" "$K_home_w" "$K_home_h"
}
png_dimensions() {
    png=$1; expected_w=$2; expected_h=$3
    set -- $(od -An -tu1 -N24 "$png")
    [ "$#" = 24 ] || return 1
    [ "$1 $2 $3 $4 $5 $6 $7 $8" = '137 80 78 71 13 10 26 10' ] || return 1
    actual_w=$((${17}*16777216+${18}*65536+${19}*256+${20}))
    actual_h=$((${21}*16777216+${22}*65536+${23}*256+${24}))
    [ "$actual_w" = "$expected_w" ] && [ "$actual_h" = "$expected_h" ]
}
valid_frame_name() {
    printf '%s\n' "$1" | grep -Eq '^frame-[0-9]{1,20}-(portrait-v1|landscape-v1)-[0-9]+$'
}
load_frame() {
    frame_name=$(cat "$BASE/frame-current" 2>/dev/null)
    valid_frame_name "$frame_name" || return 1
    FRAME_DIR="$BASE/frames/$frame_name"
    [ -s "$FRAME_DIR/frame.png" ] || return 1
    if [ "$(cat "$RUN/layout-frame" 2>/dev/null)" != "$frame_name" ] || [ ! -s "$RUN/layout-state" ]; then
        cache="$RUN/layout-state-$$.tmp"
        validate_manifest "$FRAME_DIR/manifest.txt" "$cache" || { rm -f "$cache"; return 1; }
        printf "FRAME_DIR='%s'\n" "$FRAME_DIR" >> "$cache"
        mv "$cache" "$RUN/layout-state"
        echo "$frame_name" > "$RUN/layout-frame"
    fi
    . "$RUN/layout-state"
}
resume_frame_touch() {
    is_dashboard || return 0
    case "$(cat "$RUN/profile" 2>/dev/null)" in day|night) :;; *) start_worker touch-loop;; esac
}
