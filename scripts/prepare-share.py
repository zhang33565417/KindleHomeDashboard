"""Prepare a ready-to-upload GitHub directory; no archive or checksum file."""
import argparse
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--assets', type=Path, default=ROOT / 'deploy/kindle/layout-assets')
p.add_argument('--jar', type=Path, default=ROOT / 'target/kindle-home-dashboard-0.1.0.jar')
a = p.parse_args()
out = ROOT / 'share' / 'GitHub上传目录'
assets = a.assets.resolve()
jar = a.jar.resolve()
# Validate all inputs before replacing generated folders.
with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    if 'BOOT-INF/classes/com/example/kindleboard/KindleBoardApplication.class' not in names:
        raise SystemExit('Not a dashboard Spring Boot JAR')
    if any(n.startswith(('data/', 'BOOT-INF/classes/data/')) for n in names):
        raise SystemExit('Refusing a JAR containing household data')
tiles = []
for layout in ('portrait-v1', 'landscape-v1'):
    expected = [f'time-{h:02}{m:02}.png' for h in range(24) for m in range(60)]
    expected += [f'sleep-time-{h:02}{m:02}.png' for h in range(24) for m in range(60)]
    expected += [f'{n}-{c}.png' for n in range(101) for c in (0, 1)]
    for name in expected:
        f = assets / layout / name
        if not f.is_file():
            raise SystemExit(f'Missing tile: {f}')
        tiles.append((f, Path('layout-assets') / layout / name))

def source_files():
    for name in ('README.md', '.gitignore', '.gitattributes', 'pom.xml'):
        yield ROOT / name
    for directory in ('src', 'scripts', 'docs', 'deploy'):
        for f in sorted((ROOT / directory).rglob('*')):
            if f.is_file() and f.suffix in ('.java', '.properties', '.html', '.css', '.js',
                                           '.md', '.png', '.py', '.sh', '.awk', '.conf', '.json', '.xml'):
                if 'layout-assets' not in f.parts and 'history' not in f.parts:
                    yield f
    yield ROOT / 'deploy/kindle/server-url.example.txt'

files = sorted(set(source_files()))
# Replace only this generated upload directory after validating every input.
if out.resolve().parent != (ROOT / 'share').resolve() or out.is_symlink():
    raise SystemExit('Unsafe output path')
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)
folders = ['01-服务端', '02-Kindle客户端']
for name in folders:
    (out / name).mkdir()

def copy(f, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    data = f.read_bytes()
    if f.suffix not in ('.png', '.jar'):
        data = data.replace(b'\r\n', b'\n')
    target.write_bytes(data)

copy(jar, out / folders[0] / 'kindle-home-dashboard.jar')
for f in files:
    copy(f, out / f.relative_to(ROOT))
client = out / folders[1] / 'kindle-dashboard'
for f in files:
    if f.parent == ROOT / 'deploy/kindle' and f.suffix in ('.sh', '.awk', '.conf', '.json', '.xml', '.md', '.txt'):
        copy(f, client / f.name)
for f, relative in tiles:
    copy(f, client / relative)
# These templates are also included in the source folder so regeneration is reproducible.
for name in folders:
    template = ROOT / 'scripts/share-notes' / (name + '.md')
    copy(template, out / name / '文件说明.md')
print(out)
print('Created ready-to-upload directory. No household data, Git history, personal URL, SSH files or third-party executables included.')
