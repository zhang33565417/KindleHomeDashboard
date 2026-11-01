#!/bin/sh
. /mnt/us/extensions/kindle-dashboard/layout-common.sh
exec 9>"$RUN/display.lock"; flock -x 9 || exit 1
load_frame || exit 1
actual="$RUN/verify-actual-$$.raw"; baseline="$RUN/verify-paper-$$.raw"
restore() {
 if [ -s "$actual" ]; then dd if="$actual" of=/dev/fb0 bs=1088 count=1448 2>/dev/null; fi
 rm -f "$actual" "$baseline"
}
trap restore EXIT
trap 'exit 1' INT TERM
dd if=/dev/fb0 of="$actual" bs=1088 count=1448 2>/dev/null || exit 1
FB="$BASE/bin/fbink"
"$FB" -q -b -i "$FRAME_DIR/frame.png" || exit 1
"$FB" -q -b -Y -4 -g "file=$FRAME_DIR/clock-background.png,x=$K_clock_bg_x,y=$K_clock_bg_y" || exit 1
"$FB" -q -b -Y -4 -g "file=$FRAME_DIR/battery-background.png,x=$K_battery_x,y=$K_battery_y" || exit 1
dd if=/dev/fb0 of="$baseline" bs=1088 count=1448 2>/dev/null || exit 1
echo "VERIFY $K_layout version=$K_version clock=$(cat "$RUN/calendar-clock") status=$(cat "$RUN/status-key")"
cmp -l "$baseline" "$actual" | awk -v cx="$K_clock_bg_x" -v cy="$K_clock_bg_y" -v cw="$K_clock_bg_w" -v ch="$K_clock_bg_h" -v bx="$K_battery_x" -v by="$K_battery_y" -v bw="$K_battery_w" -v bh="$K_battery_h" '
function oct(s, n,i){n=0;for(i=1;i<=length(s);i++)n=n*8+substr(s,i,1);return n}
{
 x=($1-1)%1088;y=int(($1-1)/1088)
 if(x>=1072)next
 inside=(x>=cx&&x<cx+cw&&y>=cy&&y<cy+ch)||(x>=bx&&x<bx+bw&&y>=by&&y<by+bh)
 if(!inside)outside++
 else {ink++;if(oct($3)>oct($2))lightened++}
}
END {print "outside_overlay_differences=" outside+0,"ink_pixels=" ink+0,"lightened_paper_pixels=" lightened+0; if(outside||lightened||!ink)exit 1}'
rc=$?
exit "$rc"
