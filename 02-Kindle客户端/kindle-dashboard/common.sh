#!/bin/sh
BASE=/mnt/us/extensions/kindle-dashboard
RUN=/tmp/kindle-dashboard
mkdir -p "$RUN"
is_dashboard() { [ "$(cat "$RUN/mode" 2>/dev/null)" = dashboard ]; }
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$BASE/controls.log"; }
kill_tree() {
    for child in $(ps -eo pid,ppid | awk -v p="$1" '$2 == p {print $1}'); do
        kill_tree "$child"
    done
    kill "$1" 2>/dev/null || :
}
stop_worker() {
    file="$1"
    pid=$(cat "$file" 2>/dev/null)
    case "$pid" in ''|*[!0-9]*) rm -f "$file"; return;; esac
    if [ -r "/proc/$pid/cmdline" ] && tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q "$BASE/"; then
        kill_tree "$pid"
    fi
    rm -f "$file"
}
start_worker() {
    name="$1"
    pid=$(cat "$RUN/$name.pid" 2>/dev/null)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null &&
        tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q "$BASE/$name.sh"; then
        return
    fi
    # Workers must not inherit the controller's flock file descriptions.
    (exec 6>&- 8>&- 9>&-; exec nohup sh "$BASE/$name.sh" </dev/null >> "$BASE/$name.log" 2>&1) &
    echo $! > "$RUN/$name.pid"
}
