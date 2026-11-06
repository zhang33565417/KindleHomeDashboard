#!/bin/sh
BASE=/mnt/us/extensions/kindle-dashboard
RUN=/tmp/kindle-dashboard
mkdir -p "$RUN"
is_dashboard() { [ "$(cat "$RUN/mode" 2>/dev/null)" = dashboard ]; }
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$BASE/controls.log"; }
kill_tree() {
    # A touch worker can become the Home controller while still being a child
    # of the power loop it is stopping. Keep the controller and its children.
    [ "$1" != "$$" ] || return 0
    local child
    for child in $(ps -eo pid,ppid | awk -v p="$1" '$2 == p {print $1}'); do
        kill_tree "$child"
    done
    kill "$1" 2>/dev/null || :
}
stop_worker() {
    local file="$1" pid worker
    worker=${file##*/}
    worker=${worker%.pid}
    pid=$(cat "$file" 2>/dev/null)
    case "$pid" in ''|*[!0-9]*) rm -f "$file"; return;; esac
    if [ -r "/proc/$pid/cmdline" ] && tr '\000' '\n' < "/proc/$pid/cmdline" | grep -Fxq "$BASE/$worker.sh"; then
        kill_tree "$pid"
    fi
    rm -f "$file"
}
start_worker() {
    local name="$1" pid
    is_dashboard || return 0
    if [ "$name" = touch-loop ] && [ -s "$RUN/exit-request" ]; then return 0; fi
    pid=$(cat "$RUN/$name.pid" 2>/dev/null)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null &&
        tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q "$BASE/$name.sh"; then
        return
    fi
    # Workers must not inherit the controller's flock file descriptions.
    (exec 6>&- 8>&- 9>&-; exec nohup sh "$BASE/$name.sh" </dev/null >> "$BASE/$name.log" 2>&1) &
    echo $! > "$RUN/$name.pid"
}
