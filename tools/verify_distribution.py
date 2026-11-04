"""Verify the installable artifact and its nested authentication library."""
import argparse
import hashlib
import io
import json
import struct
import re
import zipfile
from pathlib import Path


SUPPORTED = {'1.21.11': 21, '26.2': 25, '26.3': 25}


def properties(path):
    return dict(re.findall(r'^([\w_]+)=(.*)$', path.read_text(), re.M))


def inspect(path, minecraft=None):
    data = path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        assert jar.testzip() is None
        names = set(jar.namelist())
        meta = json.loads(jar.read('fabric.mod.json'))
        mixins = json.loads(jar.read('autism.mixins.json'))
        assert meta['id'] == 'autism'
        version = meta['depends']['minecraft']
        assert version in SUPPORTED, f'Unsupported Minecraft target: {version}'
        assert minecraft is None or version == minecraft, f'Expected Minecraft {minecraft}, found {version}'
        java = SUPPORTED[version]
        assert meta['depends']['java'] == f'>={java}'
        assert meta['authors'] == ['Melonik']
        assert mixins['compatibilityLevel'] == f'JAVA_{java}'
        assert len(mixins['client']) == 3
        assert 'LICENSE' in names
        assert meta['icon'] in names
        for entrypoint in meta['entrypoints']['client']:
            assert entrypoint.replace('.', '/') + '.class' in names
        for mixin in mixins['client']:
            assert mixins['package'].replace('.', '/') + '/' + mixin.replace('.', '/') + '.class' in names
        classes = [name for name in names if name.endswith('.class')]
        assert len(classes) > 50
        assert not any('/qa/' in name for name in names)
        assert not any(name.startswith('net/minecraft/') for name in classes)
        class_data = [jar.read(name) for name in classes]
        assert all(struct.unpack_from('>H', content, 6)[0] <= java + 44 for content in class_data)
        intermediary = any(b'net/minecraft/class_' in content for content in class_data)
        assert intermediary == (version == '1.21.11'), 'Wrong Minecraft namespace for target'
        for entry in meta.get('jars', []):
            with zipfile.ZipFile(io.BytesIO(jar.read(entry['file']))) as nested:
                assert nested.testzip() is None
                assert json.loads(nested.read('fabric.mod.json'))['id'] == 'de_florianreuth_waybackauthlib'
                assert all(struct.unpack_from('>H', nested.read(name), 6)[0] <= 65
                           for name in nested.namelist() if name.endswith('.class'))
    return {'jar': str(path.resolve()), 'sha256': hashlib.sha256(data).hexdigest(),
            'bytes': len(data), 'classes': len(classes), 'minecraft': version,
            'java': java, 'namespace': 'intermediary' if intermediary else 'official', 'passed': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path, nargs='?')
    parser.add_argument('--minecraft', choices=SUPPORTED)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    props = properties(repo / 'gradle.properties')
    target = args.minecraft or props['minecraft_version']
    profile = repo / 'versions' / f'{target}.properties'
    if profile.is_file():
        props.update(properties(profile))
    build = repo / 'build' / target if target.startswith('26.') else repo / 'build'
    path = args.jar or build / 'libs' / f"autism-account-manager-{props['version']}.jar"
    print(json.dumps(inspect(path, args.minecraft), indent=2))
