"""Generate compact, rotated local status badges; no Python runs on Kindle."""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

parser = argparse.ArgumentParser()
parser.add_argument('--font', default='C:/Windows/Fonts/msyh.ttc')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
font = ImageFont.truetype(args.font, 21)
for level in range(101):
    for charging in (0, 1):
        image = Image.new('L', (130, 30), 255)
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((4, 8, 32, 23), radius=2, outline=0, width=2)
        draw.rectangle((33, 12, 36, 18), fill=0)
        if level:
            draw.rectangle((8, 12, 8 + round(20 * level / 100), 19), fill=0)
        state = '+' if charging else '!' if level <= 20 else ''
        draw.text((47, 1), f'{level}% {state}', font=font, fill=0)
        text = Image.new('RGBA',image.size,(0,0,0,0))
        text.putalpha(ImageOps.invert(image))
        text.transpose(Image.Transpose.ROTATE_270).save(args.output / f'{level}-{charging}.png')
