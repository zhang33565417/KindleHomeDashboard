# Current power policy (2026-11-01)

Only external power and Beijing day/night affect scheduling; battery percentage is display-only.

| Profile | Condition | Clock/network | Suspend |
| --- | --- | --- | --- |
| fast | External power, including full battery | Minute clock / 30-second network | No |
| day | Unplugged 06:30–23:30 | Ten-minute clock / 600-second network | RTC RAM suspend between tasks |
| night | Unplugged 23:30–06:30 | Hourly snapshot/network | Existing hourly RTC policy |
| interactive | Manual entry or power key | Minute clock, 120-second grace | After grace returns to automatic policy |

During suspend press the power key before tapping Home. Frontlight is off in automatic battery modes and restored for interaction/Home. Network failure backs off up to one hour during day; local clock still updates every ten minutes. Cached frames remain usable offline.

Day resume compares framebuffer before/after suspend: unchanged pixels permit overlay-only refresh; changed pixels retain full recovery to avoid native UI pollution. Night retains full recovery. A suspend failure returns to native Home and releases the worker identity before stopping workers.

The optional `kindleVersion` in `/api/version` selects `?contentOnly=true` frames. The stable numeric fingerprint covers clean-clock frame pixels, paper and geometry; existing endpoints/legacy versions remain supported. Deploy the server JAR to enable content deduplication; older servers still work with time-based versions.

## Rollback

Exit via control.sh home before restoring the extension backup. This releases RTC alarms, paused UI and frontlight. Restore scripts/config and re-enter through KUAL. Server and client may be rolled back independently because legacy endpoints remain supported.
