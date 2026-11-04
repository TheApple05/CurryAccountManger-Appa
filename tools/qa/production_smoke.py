"""Run the exact distribution in pristine Minecraft, using synthetic accounts only."""
import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import time
import urllib.request
import uuid
import zipfile
from pathlib import Path


USER_AGENT = "Autism-Account-Manager-Packaged-QA/1.0"
SUPPORTED = {'1.21.11': 21, '26.2': 25, '26.3': 25}


def digest(path, algorithm="sha256"):
    result = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def request(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": USER_AGENT}), timeout=60) as response:
        return response.read()


def metadata(url, path, expected_sha1=None):
    if not path.exists() or (expected_sha1 and digest(path, "sha1") != expected_sha1):
        content = request(url)
        if expected_sha1 and hashlib.sha1(content).hexdigest() != expected_sha1:
            raise ValueError(f"Metadata checksum mismatch: {url}")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
    return json.loads(path.read_bytes())


def maven_path(coordinate):
    group, artifact, version, *classifier = coordinate.split(":")
    suffix = "-" + classifier[0] if classifier else ""
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{suffix}.jar"


def artifact(descriptor, cache, gradle_home, tools, coordinate=None):
    relative = descriptor.get("path") or maven_path(coordinate or descriptor["name"])
    target = cache / "libraries" / relative
    candidates = [target, tools / Path(relative).name]
    if coordinate:
        group, name, version, *_ = coordinate.split(":")
        module_dir = gradle_home / "caches/modules-2/files-2.1" / group / name / version
        candidates.extend(module_dir.glob("*/" + Path(relative).name))
    expected_hash = descriptor.get("sha256") or descriptor.get("sha1")
    algorithm = "sha256" if descriptor.get("sha256") else "sha1"
    url = descriptor.get("url", "")
    if not url.endswith(".jar"):
        url = url.rstrip("/") + "/" + relative
    if not expected_hash:
        expected_hash = request(url + ".sha256").decode().strip().split()[0]
        algorithm = "sha256"
    for candidate in candidates:
        if candidate.is_file() and digest(candidate, algorithm) == expected_hash:
            return candidate.resolve()
    content = request(url)
    if hashlib.new(algorithm, content).hexdigest() != expected_hash:
        raise ValueError(f"Artifact checksum mismatch: {url}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(content)
    return target.resolve()


def applies(rules):
    if not rules:
        return True
    allowed = False
    host_os = {"Windows": "windows", "Darwin": "osx", "Linux": "linux"}[platform.system()]
    for rule in rules:
        os_rule = rule.get("os", {})
        matches = os_rule.get("name", host_os) == host_os
        if "arch" in os_rule:
            matches = matches and re.fullmatch(os_rule["arch"], platform.machine().lower()) is not None
        if "version" in os_rule:
            matches = matches and re.search(os_rule["version"], platform.version()) is not None
        if rule.get("features"):
            matches = False
        if matches:
            allowed = rule["action"] == "allow"
    return allowed


def properties(path):
    return dict(re.findall(r'^([\w_]+)=(.*)$', path.read_text(), re.M))


def manifest_jvm_arguments(mc, runtime, run):
    """Honor Minecraft's native-access/export flags and native extraction paths."""
    substitutions = {
        'natives_directory': str(run / 'natives'),
        'launcher_name': 'AutismAccountManagerQA',
        'launcher_version': '1.0',
        'classpath': os.pathsep.join(map(str, runtime)),
    }
    result = []
    for argument in mc['arguments']['jvm']:
        if isinstance(argument, dict):
            if not applies(argument.get('rules')):
                continue
            argument = argument['value']
        for value in argument if isinstance(argument, list) else [argument]:
            for key, replacement in substitutions.items():
                value = value.replace('${' + key + '}', replacement)
            if '${' in value:
                raise ValueError(f'Unknown Minecraft JVM argument placeholder: {value}')
            result.append(value)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--minecraft', choices=SUPPORTED,
                        help='Minecraft build profile (defaults to gradle.properties)')
    parser.add_argument('--jar', type=Path, help='Exact installable JAR to test')
    parser.add_argument('--qa-jar', type=Path, help='Separately packaged matching QA mod')
    parser.add_argument('--loader', help='Override Fabric Loader version')
    parser.add_argument('--fabric-api', help='Override Fabric API version')
    parser.add_argument('--assets-dir', type=Path, help='Shared Minecraft assets directory')
    parser.add_argument('--native-picker', action='store_true',
                        help='Also wait for selection of the synthetic TXT file in the real OS chooser')
    parser.add_argument('--timeout', type=int, default=180)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    props = properties(repo / 'gradle.properties')
    version = args.minecraft or props['minecraft_version']
    profile_path = repo / 'versions' / f'{version}.properties'
    if profile_path.is_file():
        props.update(properties(profile_path))
    loader = args.loader or props['loader_version']
    build = repo / 'build' / version if version.startswith('26.') else repo / 'build'
    distribution = (args.jar or build / 'libs' / f"autism-account-manager-{props['version']}.jar").resolve()
    qa = (args.qa_jar or distribution.with_name(distribution.stem + '-qa.jar')).resolve()
    java = args.java_home.resolve() / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    if not distribution.is_file() or not qa.is_file():
        raise ValueError(f'Build the distribution and QA JAR for {version} first: {distribution}, {qa}')
    with zipfile.ZipFile(distribution) as jar:
        mod = json.loads(jar.read('fabric.mod.json'))
    if mod['depends']['minecraft'] != version:
        raise ValueError(f"JAR targets {mod['depends']['minecraft']}, requested {version}")
    java_info = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
    java_description = (java_info.stderr + java_info.stdout).strip()
    java_match = re.search(r'version "(\d+)', java_description)
    expected_java = SUPPORTED[version]
    if not java_match or int(java_match[1]) != expected_java:
        raise ValueError(f'Use Java {expected_java} to verify the minimum supported runtime')
    tools = repo / '.tools'
    cache = tools / 'production-runtime'
    gradle = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    run = build / 'qa' / (time.strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:6])
    (run / 'mods').mkdir(parents=True)
    (run / 'natives/lwjgl').mkdir(parents=True)
    (run / 'natives/jna').mkdir(parents=True)
    (run / 'natives/java').mkdir(parents=True)
    (run / 'natives/netty').mkdir(parents=True)
    (tools / 'latest-qa-run.txt').write_text(str(run))
    (tools / f'latest-qa-run-{version}.txt').write_text(str(run))
    for source in (distribution, qa):
        shutil.copyfile(source, run / 'mods' / source.name)
    manifest = metadata('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', cache / 'manifest.json')
    entry = next((item for item in manifest['versions'] if item['id'] == version), None)
    if entry is None:
        # The version manifest is mutable; refresh an old cache when adding a target.
        (cache / 'manifest.json').write_bytes(request('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
        manifest = json.loads((cache / 'manifest.json').read_bytes())
        entry = next(item for item in manifest['versions'] if item['id'] == version)
    mc = metadata(entry['url'], cache / f'{version}.json', entry['sha1'])
    client_info = dict(mc['downloads']['client'], path=f'net/minecraft/client/{version}/client.jar')
    cached_client = gradle / f'caches/fabric-loom/{version}/minecraft-client.jar'
    client = cached_client if cached_client.exists() and digest(cached_client, 'sha1') == client_info['sha1'] else artifact(client_info, cache, gradle, tools)
    runtime = [client.resolve()]
    for library in mc['libraries']:
        if applies(library.get('rules')) and library['downloads'].get('artifact'):
            runtime.append(artifact(library['downloads']['artifact'], cache, gradle, tools, library['name']))
    profile = metadata(f'https://meta.fabricmc.net/v2/versions/loader/{version}/{loader}/profile/json', cache / f'loader-{version}-{loader}.json')
    for library in profile['libraries']:
        runtime.append(artifact(library, cache, gradle, tools, library['name']))
    api = args.fabric_api or props['fabric_api_version']
    api_path = artifact({'url': 'https://maven.fabricmc.net/'}, cache, gradle, tools, f'net.fabricmc.fabric-api:fabric-api:{api}')
    shutil.copyfile(api_path, run / 'mods' / api_path.name)
    assets = (args.assets_dir or gradle / 'caches/fabric-loom/assets').resolve()
    index = next((path for path in (assets / 'indexes').glob('*.json') if digest(path, 'sha1') == mc['assetIndex']['sha1']), None)
    if index is None:
        raise ValueError(f'Minecraft {version} assets are missing; run gradlew -PtargetMinecraft={version} downloadAssets')
    (run / 'options.txt').write_text('lang:en_us\nguiScale:2\nfullscreen:false\nmaxFps:60\nshowMenuBackground:true\n')
    expected = digest(distribution)
    arguments = [
        '-Xmx2G', '-Dfabric.development=false', '-Dfabric.noGui=true',
        '-Dmixin.debug.countInjections=true', '-Dmixin.debug.strict.targets=true',
        f'-Dautism.qa.result={run / "result.json"}', f'-Dautism.qa.sha256={expected}',
        f'-Dautism.qa.nativePicker={str(args.native_picker).lower()}',
        *manifest_jvm_arguments(mc, runtime, run), profile['mainClass'],
        '--username', 'AutismQa', '--version', version, '--versionType', 'release',
        '--gameDir', str(run), '--assetsDir', str(assets), '--assetIndex', index.stem,
        '--accessToken', '0', '--uuid', uuid.uuid3(uuid.NAMESPACE_DNS, 'OfflinePlayer:AutismQa').hex,
        '--width', '1280', '--height', '720'
    ]
    argfile = run / 'java.args'
    argfile.write_text('\n'.join('"' + str(arg).replace('\\', '\\\\').replace('"', '\\"') + '"' for arg in arguments), encoding='utf-8')
    plan = {'minecraft': version, 'loader': loader, 'fabricApi': api, 'java': java_description,
            'jar': str(distribution), 'sha256': expected, 'development': False,
            'nativePicker': args.native_picker,
            'qaJar': str(qa), 'qaSha256': digest(qa),
            'runtime': [{'path': str(path), 'sha256': digest(path)} for path in runtime]}
    (run / 'launch-plan.json').write_text(json.dumps(plan, indent=2))
    print(f'Packaged QA run: {run}', flush=True)
    environment = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
        environment.pop(key, None)
    with (run / 'launcher.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen([str(java), '@' + str(argfile)], cwd=run, env=environment,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            exit_code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise RuntimeError(f'Client exceeded timeout; inspect {run / "launcher.log"}')
    result_file = run / 'result.json'
    result = json.loads(result_file.read_bytes()) if result_file.exists() else {'status': 'NO_RESULT'}
    verdict = {'passed': exit_code == 0 and result.get('status') == 'PASSED'
                          and result.get('sha256') == expected, 'exitCode': exit_code,
               'minecraft': version, 'sourceSha256': expected, 'result': result}
    (run / 'verdict.json').write_text(json.dumps(verdict, indent=2))
    print(json.dumps(verdict, indent=2))
    if not verdict['passed']:
        raise RuntimeError(f'Packaged client QA failed; inspect {run / "launcher.log"}')


if __name__ == '__main__':
    main()
