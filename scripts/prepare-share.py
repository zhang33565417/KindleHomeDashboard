"""Prepare a GitHub upload directory from the single maintained client folder."""
import argparse
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / '02-Kindle客户端脚本/kindle-dashboard'
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--assets', type=Path, default=CLIENT / 'layout-assets')
p.add_argument('--jar', type=Path, default=ROOT / 'target/kindle-home-dashboard-0.1.0.jar')
a = p.parse_args()
out = ROOT / 'share/GitHub上传目录'
assets = a.assets.resolve()
jar = a.jar.resolve()
# Validate inputs before replacing this generated directory.
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
    for name in ('README.md', '.gitignore', '.gitattributes', 'pom.xml',
                 '01-服务端jar包/文件说明.md', '02-Kindle客户端脚本/文件说明.md'):
        yield ROOT / name
    for directory in ('src', 'scripts', 'docs'):
        for f in sorted((ROOT / directory).rglob('*')):
            if f.is_file() and f.suffix in ('.java', '.properties', '.html', '.css', '.js',
                                           '.md', '.png', '.py', '.sh', '.awk', '.conf', '.json', '.xml'):
                if 'history' not in f.relative_to(ROOT).parts:
                    yield f
    for f in sorted(CLIENT.iterdir()):
        if f.is_file() and (f.suffix in ('.sh', '.awk', '.conf', '.json', '.xml', '.md')
                            or f.name == 'server-url.example.txt'):
            yield f

files = sorted(set(source_files()))
for f in files:
    if not f.is_file():
        raise SystemExit(f'Missing source: {f}')
if out.resolve().parent != (ROOT / 'share').resolve() or out.is_symlink():
    raise SystemExit('Unsafe output path')
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)

def copy(f, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    data = f.read_bytes()
    if f.suffix not in ('.png', '.jar'):
        data = data.replace(b'\r\n', b'\n')
    target.write_bytes(data)

copy(jar, out / '01-服务端jar包/kindle-home-dashboard.jar')
for f in files:
    copy(f, out / f.relative_to(ROOT))
for f, relative in tiles:
    copy(f, out / CLIENT.relative_to(ROOT) / relative)
print(out)
print('Created upload directory from canonical client files; private configuration, caches and third-party executables excluded.')
