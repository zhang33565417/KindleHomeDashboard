"""Crop exact framebuffer background patches after the existing FBInk offset."""
import argparse
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('--background', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
landscape = Image.open(args.background).convert('L')
assert landscape.size == (1448,1072)
raw = Image.new('L',(1072,1448),255)
# Existing full-image rendering uses the FBInk viewport's 4-pixel vertical origin.
raw.paste(landscape.transpose(Image.Transpose.ROTATE_270),(0,4))
patches = {
    'date': (872,46,91,734),
    'clock': (872,1180,86,238),
    'weekday': (915,840,42,193),
    'lunar': (865,840,38,214),
    'battery': (1008,826,30,130),
}
for name,(x,y,w,h) in patches.items():
    raw.crop((x,y,x+w,y+h)).save(args.output / f'{name}.png')
