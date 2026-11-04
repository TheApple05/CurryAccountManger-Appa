"""Package a locally verified Minecraft 26.x release without QA code or logs."""
import argparse
import hashlib
import json
import shutil
import tempfile
import zipfile
from pathlib import Path

from verify_distribution import inspect, properties


SCREENSHOTS = (
    '03-renamed-and-restored.png',
    '10-session-token-importer.png',
    '11-import-validation-results.png',
    '12-importer-compact.png',
)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def package(repo, minecraft):
    props = properties(repo / 'gradle.properties')
    props.update(properties(repo / 'versions' / f'{minecraft}.properties'))
    require(props['minecraft_version'] == minecraft, 'Build profile target mismatch')
    name = f"autism-account-manager-{props['version']}"
    build = repo / 'build' / minecraft
    jar = build / 'libs' / f'{name}.jar'
    sources = build / 'libs' / f'{name}-sources.jar'
    inspection = inspect(jar, minecraft)
    expected = inspection['sha256']
    require(inspection['passed'] is True, 'Artifact inspection did not pass')
    require(sources.is_file(), f'Missing sources JAR: {sources}')
    with zipfile.ZipFile(sources) as archive:
        require(archive.testzip() is None, 'Sources JAR checksum failure')
        require(any(path.endswith('.java') for path in archive.namelist()), 'Sources JAR has no Java source')
        require(not any('/qa/' in path for path in archive.namelist()), 'Sources JAR includes QA code')

    pointer = repo / '.tools' / f'latest-qa-run-{minecraft}.txt'
    run = Path(pointer.read_text(encoding='utf-8').strip()).resolve()
    require(run.is_relative_to((build / 'qa').resolve()) and run.is_dir(),
            'Latest QA pointer must identify an isolated run for the selected target')
    verdict_path, result_path = run / 'verdict.json', run / 'result.json'
    verdict, result = read_json(verdict_path), read_json(result_path)
    require(verdict.get('passed') is True and verdict.get('exitCode') == 0,
            'Latest packaged-client run did not pass with a zero exit code')
    require(verdict.get('minecraft') == minecraft, 'QA verdict targets another Minecraft version')
    require(verdict.get('sourceSha256') == expected and result.get('sha256') == expected,
            'Current artifact differs from the exact JAR tested by the latest QA run')
    require(result.get('status') == 'PASSED' and verdict.get('result') == result,
            'Runtime result and successful verdict do not agree')
    checks = result.get('checks')
    require(isinstance(checks, list) and checks and all(isinstance(check, str) for check in checks),
            'Successful runtime result has no check evidence')
    require('production loader environment' in checks and 'exact packaged JAR loaded' in checks,
            'Runtime evidence does not establish an exact production artifact test')
    tested = Path(result.get('modOrigin', '')).resolve()
    require(tested.is_relative_to(run / 'mods') and tested.is_file() and sha256(tested) == expected,
            'The loaded QA artifact is missing or differs from the current distribution')
    images = [run / 'screenshots' / name for name in SCREENSHOTS]
    require(all(path.is_file() for path in images), 'Selected runtime screenshots are missing')

    releases = repo / 'releases'
    destination = releases / minecraft
    require(not destination.exists(), f'Release directory already exists: {destination}')
    releases.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=f'.{minecraft}-package-', dir=releases) as temporary:
        staging = Path(temporary) / minecraft
        (staging / 'screenshots').mkdir(parents=True)
        for source in (jar, sources, repo / 'LICENSE'):
            shutil.copyfile(source, staging / source.name)
        shutil.copyfile(verdict_path, staging / 'production-verdict.json')
        shutil.copyfile(result_path, staging / 'runtime-result.json')
        for source in images:
            shutil.copyfile(source, staging / 'screenshots' / source.name)
        # Record the packaged path, rather than a build directory that may later be cleaned.
        inspection['jar'] = jar.name
        (staging / 'validation.json').write_text(json.dumps(inspection, indent=2) + '\n', encoding='utf-8')
        native_picker = any('native file picker loads the selected UTF-8 TXT fixture' in check for check in checks)
        picker_result = ('The native Open TXT file chooser loaded the selected UTF-8 fixture successfully.'
                         if native_picker else 'The native Open TXT file chooser was not exercised in this run.')
        readme = f'''# AUTISM Account Manager for Minecraft {minecraft}

Unofficial port of Melonik's AUTISM Account Manager 5.0, retaining the importer,
theme, encrypted account storage, and original GPL-3.0-only attribution.

## Install

Use Minecraft **{minecraft}**, **Java 25**, **Fabric Loader {props['loader_version']} or newer**,
and **Fabric API {props['fabric_api_version']}**. Put `{jar.name}` and the matching
Fabric API JAR in the instance's `mods` folder. Keep one account-manager version
installed. The bundled waybackauthlib is included. Open **Multiplayer → Accounts**.
The sources JAR is for development; the QA mod is excluded from this release.

## Validation

The exact installable JAR passed **{len(checks)} packaged-client checks** with development
mode disabled, using synthetic accounts in an isolated Minecraft {minecraft} instance.
The process exited with code zero. Checks cover account UI and platform input,
offline switching/restoration, encrypted persistence, importer navigation, TXT
loading, clipboard paste, cancellation, duplicate credential rotation, and
preserving accounts during temporary authentication failures.
{picker_result}

`validation.json` records artifact metadata and SHA-256 verification.
`runtime-result.json` and `production-verdict.json` contain the checks and verdict.
Selected in-game screenshots are in `screenshots`; `SHA256SUMS.txt` covers this bundle.

Microsoft OAuth, real session tokens, TheAltening, real saved-account migration,
multiplayer authentication, other mods, and Linux/macOS were not tested. Synthetic
tests do not establish external-service results. Startup authentication warnings
concern the synthetic QA user. See the repository README for importer inputs,
reproducible build commands, retained behavior, and source provenance.
'''
        (staging / 'README.md').write_text(readme, encoding='utf-8')
        artifacts = sorted(path for path in staging.rglob('*') if path.is_file())
        # Match .gitattributes before hashing: a checkout must retain valid sums
        # when Git normalizes Windows text files to LF during staging.
        for path in artifacts:
            if path.suffix in ('.json', '.md') or path.name == 'LICENSE':
                path.write_bytes(path.read_bytes().replace(b'\r\n', b'\n'))
        sums = ''.join(f'{sha256(path)}  {path.relative_to(staging).as_posix()}\n' for path in artifacts)
        (staging / 'SHA256SUMS.txt').write_bytes(sums.encode('utf-8'))
        # Recheck copied/current bytes before making the complete bundle visible.
        require(sha256(staging / jar.name) == expected and sha256(jar) == expected,
                'Artifact changed during packaging')
        staging.rename(destination)
    return {'minecraft': minecraft, 'release': str(destination), 'sha256': expected,
            'runtimeChecks': len(checks), 'nativeFilePickerChecked': native_picker}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft', choices=('26.2', '26.3'), required=True)
    args = parser.parse_args()
    print(json.dumps(package(Path(__file__).resolve().parents[1], args.minecraft), indent=2))


if __name__ == '__main__':
    main()
