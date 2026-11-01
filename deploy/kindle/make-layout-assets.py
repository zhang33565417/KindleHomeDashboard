"""Build portrait/landscape atlases from existing transparent, clockwise tiles."""
from pathlib import Path
import argparse, shutil
from PIL import Image
p=argparse.ArgumentParser()
p.add_argument('--clock',type=Path,required=True)
p.add_argument('--sleep-clock',type=Path,required=True)
p.add_argument('--battery',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
sources=list(a.clock.glob('time-*.png'))+list(a.sleep_clock.glob('sleep-time-*.png'))+list(a.battery.glob('[0-9]*.png'))
assert len(sources)==3082, f'Expected 3082 tiles, got {len(sources)}'
for layout in ('portrait-v1','landscape-v1'):
 out=a.output/layout; out.mkdir(parents=True,exist_ok=True)
 for source in sources:
  with Image.open(source) as im:
   assert im.mode=='RGBA', f'Need transparent input: {source}'
   if layout=='portrait-v1': im.transpose(Image.Transpose.ROTATE_90).save(out/source.name)
   else: shutil.copyfile(source,out/source.name)
 print(layout,len(sources))
