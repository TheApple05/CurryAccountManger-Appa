"""Validate and prepare one complete, shared-version GitHub release locally."""
import argparse
import hashlib
import io
import json
import re
import tempfile
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath, PureWindowsPath


DEFAULT_REPOSITORY = 'TheApple05/CurryAccountManger-Appa'
MAX_SOURCE_BYTES = 128 * 1024 * 1024
MAX_TOTAL_BYTES = 512 * 1024 * 1024
MAX_EXPANDED_BYTES = 256 * 1024 * 1024
MAX_ZIP_ENTRIES = 20000
GENERATED_NAMES = {'plan.json', 'notes.md', 'release-manifest.json', 'SHA256SUMS.txt'}
SEMVER = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\Z')
CLASSIFIER = re.compile(r'(?:^|[-_.])(qa|dev|test|tests|sources)(?:[-_.]|$)', re.I)
UNSAFE_CLASS_PARTS = {'qa', 'dev', 'test', 'tests'}
DOWNLOAD_HOSTS = {'github.com', 'release-assets.githubusercontent.com',
                  'objects.githubusercontent.com', 'github-releases.githubusercontent.com'}


class ValidationError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ValidationError(message)


def sha256(content):
    return hashlib.sha256(content).hexdigest()


def json_bytes(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode('utf-8')


def safe_relative(value, root, label):
    require(isinstance(value, str) and value and '\x00' not in value, f'Invalid {label} path')
    normalized = value.replace('\\', '/')
    path = PurePosixPath(normalized)
    require(not path.is_absolute() and not PureWindowsPath(value).drive
            and '..' not in path.parts, f'{label} must be a repository-relative path')
    resolved = (root / normalized).resolve()
    require(resolved.is_relative_to(root), f'{label} escapes the repository')
    return resolved


def checked_filename(value):
    require(isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]{0,199}', value)
            and value not in GENERATED_NAMES, 'Invalid or reserved asset filename')
    return value


def checked_hash(value, length=64):
    require(isinstance(value, str) and re.fullmatch(r'[0-9a-f]{' + str(length) + '}', value),
            f'Expected a lowercase {length}-character hexadecimal digest')
    return value


def checked_pair(value):
    require(isinstance(value, dict), 'Supported/artifact entry must be an object')
    minecraft, loader = value.get('minecraft'), value.get('loader')
    require(isinstance(minecraft, str) and re.fullmatch(r'\d+(?:\.\d+){1,2}', minecraft),
            'Invalid Minecraft version')
    require(loader in ('fabric', 'forge'), 'Loader must be fabric or forge')
    return minecraft, loader


def validate_download_url(url, repository):
    require(isinstance(url, str), 'Download URL must be a string')
    parsed = urllib.parse.urlsplit(url)
    path = urllib.parse.unquote(parsed.path)
    prefix = '/' + repository + '/releases/download/'
    require(parsed.scheme == 'https' and parsed.netloc.lower() == 'github.com'
            and not parsed.query and not parsed.fragment and path.casefold().startswith(prefix.casefold())
            and '..' not in PurePosixPath(path).parts and '\\' not in path
            and len(path[len(prefix):].split('/')) == 2
            and all(path[len(prefix):].split('/')), 'Source URL must be a release asset in this GitHub repository')
    return url


def allowed_download_target(url):
    parsed = urllib.parse.urlsplit(url)
    return parsed.scheme == 'https' and parsed.hostname in DOWNLOAD_HOSTS and not parsed.username and not parsed.password


class ReleaseRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        require(allowed_download_target(new_url), 'Release download redirected outside GitHub asset hosts')
        return super().redirect_request(request, response, code, message, headers, new_url)


def download_url(url):
    # Public release assets only. No account token or other authentication is sent.
    request = urllib.request.Request(url, headers={'User-Agent': 'CurryAccountManager-Release-Preparer/1.0'})
    opener = urllib.request.build_opener(ReleaseRedirectHandler())
    with opener.open(request, timeout=60) as response:
        require(allowed_download_target(response.geturl()), 'Invalid final release download host')
        length = response.headers.get('Content-Length')
        require(length is None or (length.isdigit() and int(length) <= MAX_SOURCE_BYTES),
                'Release asset exceeds the download size limit')
        content = response.read(MAX_SOURCE_BYTES + 1)
    require(len(content) <= MAX_SOURCE_BYTES, 'Release asset exceeds the download size limit')
    return content


def source_bytes(source, root, repository, downloader, remote_allowed):
    require(isinstance(source, dict) and len(source) == 1 and set(source) <= {'path', 'url'},
            'Source must contain exactly one path or URL')
    if 'path' in source:
        path = safe_relative(source['path'], root, 'Source')
        require(path.is_file() and path.stat().st_size <= MAX_SOURCE_BYTES, 'Source is missing or too large')
        content = path.read_bytes()
    else:
        require(remote_allowed, 'Attachments must use local repository paths')
        content = downloader(validate_download_url(source['url'], repository))
    require(isinstance(content, bytes) and len(content) <= MAX_SOURCE_BYTES, 'Source exceeds the size limit')
    return content


def archive_entries(content, depth=0, budget=None, parents=(), include_parents=False):
    """Check CRCs, expansion limits, and paths, including nested dependency archives."""
    require(depth <= 3, 'Archive nesting is too deep')
    budget = budget if budget is not None else [0, 0]
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            infos = archive.infolist()
            names = [info.filename for info in infos]
            require(len(names) == len(set(names)), 'Archive contains duplicate entries')
            budget[0] += len(infos)
            budget[1] += sum(info.file_size for info in infos)
            require(budget[0] <= MAX_ZIP_ENTRIES and budget[1] <= MAX_EXPANDED_BYTES,
                    'Archive exceeds expansion limits')
            for info in infos:
                path = PurePosixPath(info.filename.replace('\\', '/'))
                require(not path.is_absolute() and '..' not in path.parts
                        and not PureWindowsPath(info.filename).drive, 'Unsafe archive entry path')
                require(not info.flag_bits & 1, 'Encrypted archive entries are not allowed')
            require(archive.testzip() is None, 'Archive CRC check failed')
            for info in infos:
                if info.is_dir():
                    continue
                data = archive.read(info)
                yield (info.filename, data, parents) if include_parents else (info.filename, data)
                if info.filename.lower().endswith(('.jar', '.zip')):
                    yield from archive_entries(data, depth + 1, budget, parents + (info.filename,), include_parents)
    except (zipfile.BadZipFile, RuntimeError, NotImplementedError, EOFError) as error:
        raise ValidationError('Invalid or unsupported ZIP/JAR archive') from error


def check_class(name, content, java):
    normalized = name.replace('\\', '/').lower()
    parts = PurePosixPath(normalized).parts
    require(not set(parts) & UNSAFE_CLASS_PARTS and not normalized.endswith('test.class'),
            'Production archive includes QA, development, or test classes')
    require(not normalized.startswith(('net/minecraft/', 'com/mojang/blaze3d/')),
            'Production archive includes vanilla Minecraft classes')
    require(len(content) >= 8 and content[:4] == b'\xca\xfe\xba\xbe', 'Invalid Java class header')
    major = int.from_bytes(content[6:8], 'big')
    require(45 <= major <= java + 44, 'Class bytecode exceeds the declared Java runtime')


def inspect_artifact(content, entry):
    require(entry['filename'].endswith('.jar') and not CLASSIFIER.search(entry['filename']),
            'Main artifacts must be installable JARs without QA/dev/test/sources classifiers')
    count = 0
    for name, data in archive_entries(content):
        if name.endswith('.class'):
            check_class(name, data, entry['java'])
            count += 1
    require(count > 0, 'Installable JAR has no Java classes')
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as jar:
            if entry['loader'] == 'fabric':
                metadata = json.loads(jar.read('fabric.mod.json'))
                require(metadata.get('depends', {}).get('minecraft') == entry['minecraft'],
                        'Fabric metadata targets a different Minecraft version')
            else:
                metadata = json.loads(jar.read('mcmod.info'))
                records = metadata if isinstance(metadata, list) else metadata.get('modList', [metadata])
                require(isinstance(records, list) and records
                        and all(isinstance(mod, dict) and mod.get('mcversion') == entry['minecraft'] for mod in records),
                        'Forge metadata targets a different Minecraft version')
    except (KeyError, ValueError, TypeError, AttributeError) as error:
        if isinstance(error, ValidationError):
            raise
        raise ValidationError('Missing or invalid mod metadata') from error


def check_public_text(content, filename):
    try:
        text = content.decode('utf-8-sig')
    except UnicodeDecodeError as error:
        raise ValidationError('Public text attachment is not UTF-8') from error
    require(not re.search(r'\beyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', text),
            'Attachment contains a session-token-shaped value')
    for token in re.findall(r'\bM\.C[^\s"<>]{32,}', text):
        require('synthetic' in token.lower(), 'Attachment contains a refresh-token-shaped value')
    if filename.lower().endswith('.json'):
        try:
            value = json.loads(text)
        except ValueError as error:
            raise ValidationError('Invalid JSON attachment') from error
        def visit(item):
            if isinstance(item, dict):
                for key, value in item.items():
                    if re.sub('[^a-z]', '', key.lower()) in ('token', 'sessiontoken', 'accesstoken', 'refreshtoken',
                                                            'password', 'secret', 'clientsecret', 'rawinput', 'inputtext'):
                        require(value in ('', None, '[REDACTED]', 'REDACTED')
                                or (isinstance(value, str) and 'synthetic' in value.lower()),
                                'Attachment contains populated credential fields')
                    visit(value)
            elif isinstance(item, list):
                for value in item:
                    visit(value)
        visit(value)


def inspect_attachment(content, filename, known_jar_hashes):
    require(not re.search(r'(?:^|[-_.])(qa|dev|test|tests)(?:[-_.]|$)', filename, re.I),
            'QA, development, or test attachments are not release assets')
    require(filename.lower().endswith(('.json', '.zip', '-sources.jar', '.txt', '.md', '.png', '.jpg', '.jpeg', '.pdf')),
            'Unsupported attachment type')
    entries = (archive_entries(content, include_parents=True) if filename.lower().endswith(('.zip', '.jar'))
               else [(filename, content, ())])
    for name, data, parents in entries:
        lower = name.replace('\\', '/').lower()
        leaf = PurePosixPath(lower).name
        suffix = PurePosixPath(leaf).suffix
        sensitive_text_name = (suffix in ('.txt', '.csv', '.tsv')
                               and re.search(r'(?:^|[-_.])(credentials?|tokens?|passwords?)(?:[-_.]|$)', leaf))
        sensitive_json_name = suffix == '.json' and PurePosixPath(leaf).stem in (
            'accounts', 'credentials', 'tokens', 'passwords', 'sessions')
        require(not leaf.startswith('.env') and suffix not in ('.nbt', '.key', '.dpapi', '.log')
                and not sensitive_text_name and not sensitive_json_name,
                'Account data or credential files cannot be release attachments')
        if lower.endswith('.class'):
            check_class(name, data, 99)
        if lower.endswith('.java'):
            require(not set(PurePosixPath(lower).parts) & UNSAFE_CLASS_PARTS
                    and not lower.endswith('test.java'), 'Source attachment contains QA or test code')
        if lower.endswith('.jar') and not lower.endswith('-sources.jar'):
            require(not re.search(r'(?:^|[-_.])(qa|dev|test|tests)(?:[-_.]|$)', leaf),
                    'Bundle contains a QA, development, or test JAR')
            inside_main_jar = any(parent.lower().endswith('.jar') and not parent.lower().endswith('-sources.jar')
                                  for parent in parents)
            if not inside_main_jar:
                require(sha256(data) in known_jar_hashes, 'Bundle contains a JAR outside the supported release matrix')
        if lower.endswith(('.json', '.txt', '.md')):
            check_public_text(data, name)


def prepare(manifest_path, output, commit, repository=DEFAULT_REPOSITORY, *, root=None, downloader=None):
    root = (root or Path(__file__).resolve().parents[1]).resolve()
    require(re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository) is not None,
            'Repository must be owner/name')
    checked_hash(commit, 40)
    manifest_path = Path(manifest_path)
    manifest_path = manifest_path.resolve() if manifest_path.is_absolute() else safe_relative(str(manifest_path), root, 'Manifest')
    require(manifest_path.is_relative_to(root) and manifest_path.is_file()
            and manifest_path.stat().st_size <= 1024 * 1024, 'Manifest is missing, outside the repository, or too large')
    output = Path(output)
    output = output.resolve() if output.is_absolute() else safe_relative(str(output), root, 'Output')
    require(output.is_relative_to(root) and output != root and not output.exists(),
            'Output must be a new directory inside the repository')
    try:
        manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    except (ValueError, UnicodeError) as error:
        raise ValidationError('Invalid UTF-8 JSON manifest') from error
    require(isinstance(manifest, dict) and type(manifest.get('schema')) is int and manifest['schema'] == 1,
            'Unsupported release manifest schema')
    version = manifest.get('version')
    require(isinstance(version, str) and SEMVER.fullmatch(version), 'Use a shared semantic version without a +Minecraft suffix')
    title = manifest.get('title')
    require(isinstance(title, str) and title.strip() and len(title) <= 256 and '\n' not in title and '\r' not in title,
            'Release title must be one nonempty line')
    notes_path = safe_relative(manifest.get('notes'), root, 'Notes')
    require(notes_path.is_file() and notes_path.stat().st_size <= 256 * 1024, 'Notes are missing or too large')
    notes = notes_path.read_text(encoding='utf-8-sig').strip()
    require(notes, 'Release notes cannot be empty')
    supported, artifacts, attachments = (manifest.get(key) for key in ('supported', 'artifacts', 'attachments'))
    require(isinstance(supported, list) and 0 < len(supported) <= 64 and isinstance(artifacts, list)
            and isinstance(attachments, list) and len(artifacts) <= 64 and len(attachments) <= 64, 'Invalid release asset lists')
    pairs = [checked_pair(value) for value in supported]
    require(len(pairs) == len(set(pairs)), 'Supported matrix contains duplicate pairs')
    artifact_pairs = [checked_pair(value) for value in artifacts]
    require(len(artifact_pairs) == len(set(artifact_pairs)), 'Artifact matrix contains duplicate pairs')
    require(set(pairs) == set(artifact_pairs), 'Artifact matrix must match every supported pair exactly')
    names = []
    for entry in artifacts + attachments:
        require(isinstance(entry, dict), 'Asset entry must be an object')
        names.append(checked_filename(entry.get('filename')))
        checked_hash(entry.get('sha256'))
        source = entry.get('source')
        require(isinstance(source, dict) and len(source) == 1 and set(source) <= {'path', 'url'}, 'Invalid asset source')
        if 'path' in source:
            safe_relative(source['path'], root, 'Source')
        else:
            require(entry in artifacts, 'Attachments must use local repository paths')
            validate_download_url(source['url'], repository)
    require(len(names) == len(set(name.casefold() for name in names)), 'Release asset filenames must be unique')
    for entry in artifacts:
        require(type(entry.get('java')) is int and 8 <= entry['java'] <= 99, 'Invalid Java version')
        checked_hash(entry.get('sourceRef'), 40)
        require(isinstance(entry.get('requirements'), str) and entry['requirements'].strip(), 'Artifact requirements are missing')
        validation = entry.get('validation')
        require(isinstance(validation, dict) and all(type(validation.get(key)) is int and validation[key] >= 0
                for key in ('runtimeChecks', 'deterministicChecks')), 'Invalid artifact validation counts')

    payloads = {}
    downloader = downloader or download_url
    for entry in artifacts + attachments:
        content = source_bytes(entry['source'], root, repository, downloader, entry in artifacts)
        require(sha256(content) == entry['sha256'], f"SHA-256 mismatch for {entry['filename']}")
        payloads[entry['filename']] = content
        require(sum(map(len, payloads.values())) <= MAX_TOTAL_BYTES, 'Release exceeds the total size limit')
    for entry in artifacts:
        inspect_artifact(payloads[entry['filename']], entry)
    known_hashes = {entry['sha256'] for entry in artifacts}
    for entry in attachments:
        inspect_attachment(payloads[entry['filename']], entry['filename'], known_hashes)

    public_artifacts = [{**{key: entry[key] for key in ('minecraft', 'loader', 'java', 'filename', 'sha256',
                         'requirements', 'sourceRef')},
                         'validation': {key: entry['validation'][key] for key in ('runtimeChecks', 'deterministicChecks')}}
                        for entry in artifacts]
    public_artifacts.sort(key=lambda entry: entry['filename'])
    public = {'schema': 1, 'version': version, 'title': title, 'commit': commit, 'repository': repository,
              'supported': [{'minecraft': mc, 'loader': loader} for mc, loader in sorted(pairs)],
              'artifacts': public_artifacts,
              'attachments': [{key: entry[key] for key in ('filename', 'sha256')}
                              for entry in sorted(attachments, key=lambda entry: entry['filename'])]}
    payloads['release-manifest.json'] = json_bytes(public)
    payloads['SHA256SUMS.txt'] = ''.join(f'{sha256(data)}  {name}\n' for name, data in sorted(payloads.items())).encode('utf-8')
    notes += '\n\n## Supported downloads\n\n' + '\n'.join(
        f"- **Minecraft {entry['minecraft']} / {entry['loader'].title()}**: `{entry['filename']}` — {entry['requirements']}"
        for entry in sorted(artifacts, key=lambda entry: pairs.index((entry['minecraft'], entry['loader'])))) + '\n'
    plan = {'version': version, 'tag': 'v' + version, 'title': title, 'commit': commit,
            'repository': repository, 'notesPath': 'notes.md',
            'assets': [{'name': name, 'path': name, 'sha256': sha256(data), 'size': len(data)}
                       for name, data in sorted(payloads.items())]}
    # Validation completes before creating any output directory or staged release files.
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.' + output.name + '-', dir=output.parent) as temporary:
        staging = Path(temporary) / 'release'
        staging.mkdir()
        for name, data in payloads.items():
            (staging / name).write_bytes(data)
        (staging / 'notes.md').write_bytes(notes.encode('utf-8'))
        (staging / 'plan.json').write_bytes(json_bytes(plan))
        staging.rename(output)
    return plan


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', default='release.json')
    parser.add_argument('--output', default='dist/github-release')
    parser.add_argument('--commit', required=True)
    parser.add_argument('--repository', default=DEFAULT_REPOSITORY)
    args = parser.parse_args()
    try:
        plan = prepare(args.manifest, args.output, args.commit, args.repository)
    except (ValidationError, OSError) as error:
        parser.exit(1, f'Release preparation failed: {error}\n')
    print(json.dumps({'version': plan['version'], 'tag': plan['tag'], 'assets': len(plan['assets']),
                      'plan': str(Path(args.output) / 'plan.json')}))


if __name__ == '__main__':
    main()
