"""Transparent clock snapshots explicitly identify frozen sleeping time."""
import argparse
from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--output',type=Path,required=True)
out=parser.parse_args().output; out.mkdir(parents=True,exist_ok=True)
label=ImageFont.truetype('C:/Windows/Fonts/simsun.ttc',23)
digits=ImageFont.truetype('C:/Windows/Fonts/timesbd.ttf',59)
for hour in range(24):
    for minute in range(60):
        mask=Image.new('L',(227,80),0); draw=ImageDraw.Draw(mask)
        text='休眠时刻'; draw.text(((227-draw.textlength(text,font=label))/2,-2),text,font=label,fill=255)
        text=f'{hour:02}:{minute:02}'; box=draw.textbbox((0,0),text,font=digits)
        draw.text(((227-draw.textlength(text,font=digits))/2,28-box[1]),text,font=digits,fill=255)
        image=Image.new('RGBA',mask.size,(0,0,0,0)); image.putalpha(mask)
        image.transpose(Image.Transpose.ROTATE_270).save(out/f'sleep-time-{hour:02}{minute:02}.png')
