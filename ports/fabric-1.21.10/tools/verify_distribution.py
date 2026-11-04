"""Verify the installable artifact and its nested authentication library."""
import hashlib
import io
import json
import struct
import re
import sys
import zipfile
from pathlib import Path


def inspect(path):
    data = path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        assert jar.testzip() is None
        names = set(jar.namelist())
        meta = json.loads(jar.read('fabric.mod.json'))
        mixins = json.loads(jar.read('autism.mixins.json'))
        assert meta['id'] == 'autism'
        assert meta['depends']['minecraft'] == '1.21.10'
        assert meta['depends']['java'] == '>=21'
        assert meta['authors'] == ['Melonik']
        assert mixins['compatibilityLevel'] == 'JAVA_21'
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
        assert all(struct.unpack_from('>H', jar.read(name), 6)[0] <= 65 for name in classes)
        for entry in meta.get('jars', []):
            with zipfile.ZipFile(io.BytesIO(jar.read(entry['file']))) as nested:
                assert nested.testzip() is None
                assert json.loads(nested.read('fabric.mod.json'))['id'] == 'de_florianreuth_waybackauthlib'
                assert all(struct.unpack_from('>H', nested.read(name), 6)[0] <= 65
                           for name in nested.namelist() if name.endswith('.class'))
    return {'jar': str(path.resolve()), 'sha256': hashlib.sha256(data).hexdigest(),
            'bytes': len(data), 'classes': len(classes), 'minecraft': '1.21.10', 'passed': True}


if __name__ == '__main__':
    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    else:
        version = re.search(r'^version=(.+)$', Path('gradle.properties').read_text(), re.M)[1]
        path = Path('build/libs') / f'curryfarts-{version}.jar'
    print(json.dumps(inspect(path), indent=2))
