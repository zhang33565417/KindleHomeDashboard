#!/bin/sh
# KUAL needs time to close its menu before the screen is taken over.
sleep 2
exec sh /mnt/us/extensions/kindle-dashboard/control.sh dashboard
