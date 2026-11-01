#!/bin/sh
# Pure functions: no device I/O.
choose_profile() {
    local_second=$1; level=$2; external=$3; latched=$4; manual=$5
    latched=0
    if [ "$external" = 1 ]; then profile=fast
    elif [ "$manual" = 1 ]; then profile=interactive
    elif [ "$local_second" -ge "$NIGHT_START" ] || [ "$local_second" -lt "$NIGHT_END" ]; then profile=night
    else profile=day
    fi
}
profile_interval() {
    case "$1" in
        fast) interval=$FAST_NETWORK_INTERVAL;;
        interactive) interval=120;;
        night) interval=$NIGHT_INTERVAL;;
        *) interval=$DAY_NETWORK_INTERVAL;;
    esac
}
until_boundary() {
    if [ "$1" -lt "$NIGHT_END" ]; then boundary=$((NIGHT_END-$1))
    elif [ "$1" -lt "$NIGHT_START" ]; then boundary=$((NIGHT_START-$1))
    else boundary=$((86400+NIGHT_END-$1))
    fi
}
