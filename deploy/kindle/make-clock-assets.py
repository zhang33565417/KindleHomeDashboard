"""Small glyph atlas + 1440 time tiles; local shell selects tiles using its clock."""
from pathlib import Path
import argparse
from PIL import Image, ImageDraw, ImageFont, ImageOps

parser = argparse.ArgumentParser()
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--labels', required=True, type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
chinese = ImageFont.truetype('C:/Windows/Fonts/simsun.ttc', 100)
numbers = ImageFont.truetype('C:/Windows/Fonts/timesbd.ttf', 100)
small = ImageFont.truetype('C:/Windows/Fonts/simsun.ttc', 29)
weekday = ImageFont.truetype('C:/Windows/Fonts/simsun.ttc', 34)

def tile(name, text, font, size, fit=False):
    image = Image.new('L', size, 255)
    draw = ImageDraw.Draw(image)
    bbox = draw.textbbox((0,0), text, font=font)
    if fit:
        glyph = Image.new('L', (bbox[2]-bbox[0],bbox[3]-bbox[1]), 255)
        ImageDraw.Draw(glyph).text((-bbox[0],-bbox[1]),text,font=font,fill=0)
        image = glyph.resize(size, Image.Resampling.LANCZOS)
    else:
        draw.text((0, (size[1]-(bbox[3]-bbox[1]))//2-bbox[1]), text, font=font, fill=0)
    # Black text with antialiased alpha; no white rectangle is ever painted.
    text = Image.new('RGBA', image.size, (0,0,0,0))
    text.putalpha(ImageOps.invert(image))
    text.transpose(Image.Transpose.ROTATE_270).save(args.output / f'{name}.png')

for digit in '0123456789':
    tile(f'digit-{digit}',digit,numbers,(61,85),True)
for key,text in [('year','年'),('month','月'),('day','日')]:
    tile(key,text,chinese,(80,85),True)
for hour in range(24):
    for minute in range(60):
        tile(f'time-{hour:02}{minute:02}',f'{hour:02}:{minute:02}',numbers,(227,80),True)
for day,text in enumerate('日一二三四五六'):
    tile(f'weekday-{day}','星期'+text,weekday,(193,42))
for line in args.labels.read_text(encoding='utf-8').splitlines():
    key,label = line.split('\t')
    tile(key,label,small,(214,38))
