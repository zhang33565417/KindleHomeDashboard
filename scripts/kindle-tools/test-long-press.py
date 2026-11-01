"""Build evtest replays covering button tap, wrong region, drag and multitouch."""
from pathlib import Path
import argparse
parser = argparse.ArgumentParser()
parser.add_argument('output', type=Path)
out = parser.parse_args().output
out.mkdir(parents=True, exist_ok=True)
def event(t, code, value):
    return f'Event: time {t:.6f}, type 3 (EV_ABS), code {code} (TEST), value {value}\n'
def hold(x=1025, y=70, seconds=.2, extra=''):
    return event(100, 57, 1)+event(100, 53, x)+event(100, 54, y)+extra+event(100+seconds, 57, -1)
cases = {
    'valid-tap': (hold(), True),
    'long-hold': (hold(seconds=3), False),
    'battery-tap': (hold(y=870), False),
    'old-bottom-button': (hold(x=30,y=1350), False),
    'outside-region': (hold(x=500), False),
    'other-corner': (hold(y=1400), False),
    'drag': (hold(extra=event(100.05,53,950)+event(100.1,53,1025)), False),
    'missing-coordinates': (event(100,57,1)+event(103,57,-1), False),
    'dropped-events': (hold(extra='Event: time 100.1, >>> SYN_DROPPED <<<\n'), False),
    'multitouch': (hold(extra=event(100.05,47,1)+event(100.05,57,2)+event(100.1,47,0)), False),
    'overlong': (hold(seconds=20), False),
}
for name, (events, expected) in cases.items():
    (out/f'{name}.events').write_text(events, encoding='ascii')
    (out/f'{name}.expected').write_text('yes' if expected else 'no', encoding='ascii')
