# Parse standard evtest output. Trigger on a single tap/release inside the
# original top-left house icon. Ignore swipes, long holds and multitouch.
BEGIN { if(maxx<=minx || maxy<=miny) exit 2; slot=0; active=0; fired=0 }
/SYN_DROPPED/ { active=0; next }
/type 3 .*code 47 / { slot=$NF+0; next }
/type 3 .*code 57 / {
    value=$NF+0
    if (slot != 0) { if (value >= 0) active=0; next }
    if (value >= 0) {
        started=$3+0; active=1; valid=1; haveX=0; haveY=0
    } else {
        elapsed=($3+0)-started
        if (active && valid && haveX && haveY && elapsed >= 0 && elapsed <= 1.5 && !fired) {
            print "tap_seconds=" elapsed " raw_x=" x " raw_y=" y > request
            close(request)
            fired=1
            exit
        }
        active=0
    }
    next
}
/type 3 .*code 53 / {
    if (slot != 0 || !active) next
    x=$NF+0
    if (!haveX) { originX=x; haveX=1 }
    if (x < minx || x > maxx || x-originX > 25 || originX-x > 25) valid=0
    next
}
/type 3 .*code 54 / {
    if (slot != 0 || !active) next
    y=$NF+0
    if (!haveY) { originY=y; haveY=1 }
    if (y < miny || y > maxy || y-originY > 25 || originY-y > 25) valid=0
    next
}
