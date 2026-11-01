"""Generate current client tiles on the computer, using Pillow and local fonts."""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--font', required=True, help='Chinese font for sleep label and battery')
p.add_argument('--bold-font', required=True, help='Font for clock digits')
p.add_argument('--output', type=Path, default=Path('deploy/kindle/layout-assets'))
a = p.parse_args()
for layout in ('portrait-v1', 'landscape-v1'):
    (a.output / layout).mkdir(parents=True, exist_ok=True)

def save(mask, name):
    tile = Image.new('RGBA', mask.size, (0, 0, 0, 0))
    tile.putalpha(mask)
    tile.save(a.output / 'portrait-v1' / name)
    tile.transpose(Image.Transpose.ROTATE_270).save(a.output / 'landscape-v1' / name)

clock = ImageFont.truetype(a.bold_font, 100)
label = ImageFont.truetype(a.font, 23)
digits = ImageFont.truetype(a.bold_font, 59)
battery = ImageFont.truetype(a.font, 21)
for hour in range(24):
    for minute in range(60):
        text = f'{hour:02}:{minute:02}'
        box = clock.getbbox(text)
        mask = Image.new('L', (box[2]-box[0], box[3]-box[1]), 0)
        ImageDraw.Draw(mask).text((-box[0], -box[1]), text, font=clock, fill=255)
        save(mask.resize((227, 80), Image.Resampling.LANCZOS), f'time-{hour:02}{minute:02}.png')
        mask = Image.new('L', (227, 80), 0)
        d = ImageDraw.Draw(mask)
        title = '休眠时刻'
        d.text(((227-d.textlength(title, font=label))/2, -2), title, font=label, fill=255)
        box = d.textbbox((0, 0), text, font=digits)
        d.text(((227-d.textlength(text, font=digits))/2, 28-box[1]), text, font=digits, fill=255)
        save(mask, f'sleep-time-{hour:02}{minute:02}.png')
for n in range(101):
    for charging in (0, 1):
        mask = Image.new('L', (130, 30), 0)
        d = ImageDraw.Draw(mask)
        d.rounded_rectangle((4, 8, 32, 23), radius=2, outline=255, width=2)
        d.rectangle((33, 12, 36, 18), fill=255)
        if n:
            d.rectangle((8, 12, 8+round(20*n/100), 19), fill=255)
        state = '+' if charging else '!' if n <= 20 else ''
        d.text((47, 1), f'{n}% {state}', font=battery, fill=255)
        save(mask, f'{n}-{charging}.png')
print('Generated 3082 transparent tiles per layout; no font files are copied.')
