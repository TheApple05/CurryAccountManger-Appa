"""Collect all four installable ports and verify the delivered binary bytes.

Run only after each port's build and isolated runtime validation have finished.
This intentionally rejects partial collections or development/QA artifacts.
"""
import hashlib
import json
import shutil
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGETS = (
    ('1.8.9', 'forge-1.8.9', 'forge', 52),
    ('1.12.2', 'forge-1.12.2', 'forge', 52),
    ('1.16.5', 'fabric-1.16.5', 'fabric', 52),
    ('1.21.10', 'fabric-1.21.10', 'fabric', 65),
)


def verify(path, version, loader, major):
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None, f'Corrupt JAR: {path}'
        names = archive.namelist()
        classes = [name for name in names if name.endswith('.class')]
        assert classes, f'No compiled classes: {path}'
        assert not any(name.startswith('net/minecraft/') for name in classes), 'Vanilla classes in mod JAR'
        assert not any('/qa/' in name or '/gametest/' in name for name in classes), 'QA classes in installable JAR'
        for name in classes:
            assert struct.unpack_from('>H', archive.read(name), 6)[0] <= major, f'Wrong Java version: {name}'
        if loader == 'fabric':
            metadata = json.loads(archive.read('fabric.mod.json'))
            assert version in str(metadata['depends']['minecraft']), 'Wrong Minecraft dependency'
            for entrypoint in metadata.get('entrypoints', {}).get('client', []):
                assert entrypoint.replace('.', '/') + '.class' in names, f'Missing entrypoint: {entrypoint}'
        else:
            assert 'mcmod.info' in names, 'Missing Forge metadata'
            assert version in archive.read('mcmod.info').decode('utf-8'), 'Wrong Forge Minecraft metadata'
        assert any(name.startswith('LICENSE') or name.startswith('META-INF/LICENSE') for name in names), 'Missing license'
    return {'file': path.name, 'bytes': path.stat().st_size, 'classes': len(classes),
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def main():
    output = ROOT / 'dist/minecraft-ports'
    output.mkdir(parents=True, exist_ok=True)
    manifest = []
    # Verify every input before copying any artifacts.
    for version, directory, loader, major in TARGETS:
        project = ROOT / 'ports' / directory
        candidates = [path for path in (project / 'build/libs').glob(f'*+{version}-{loader}.jar')
                      if not any(word in path.name for word in ('sources', '-dev', '-qa'))]
        assert len(candidates) == 1, f'Expected one installable {version} {loader} JAR, found {candidates}'
        artifact = verify(candidates[0], version, loader, major)
        evidence = project / 'validation/runtime.json'
        assert evidence.is_file(), f'Missing runtime evidence: {evidence}'
        runtime = json.loads(evidence.read_text(encoding='utf-8'))
        verdict = runtime['verdict']
        assert verdict.get('passed') is True and verdict.get('exitCode') == 0, f'Runtime did not pass: {version}'
        assert verdict.get('sourceSha256') == artifact['sha256'], f'Artifact changed after runtime test: {version}'
        result = verdict.get('result', {})
        assert result.get('sha256', result.get('jarSha256')) == artifact['sha256'], f'Loaded runtime JAR differs: {version}'
        assert result.get('status') == 'PASSED' and result.get('checks'), f'No runtime checks: {version}'
        artifact.update(minecraft=version, loader=loader, java=major - 44,
                        sourceProject=project.relative_to(ROOT).as_posix(), runtimeChecks=len(result['checks']))
        manifest.append((candidates[0], artifact))
    for source, artifact in manifest:
        destination = output / artifact['file']
        shutil.copy2(source, destination)
        assert hashlib.sha256(destination.read_bytes()).hexdigest() == artifact['sha256']
    data = [artifact for _, artifact in manifest]
    (output / 'artifacts.json').write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    (output / 'SHA256SUMS.txt').write_text('\n'.join(item['sha256'] + '  ' + item['file'] for item in data) + '\n', encoding='utf-8')
    print(json.dumps({'directory': str(output), 'artifacts': data}, indent=2))


if __name__ == '__main__':
    main()
