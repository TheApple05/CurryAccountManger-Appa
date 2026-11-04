"""Release preparation must reject incomplete or unverified distributions."""
import copy
import hashlib
import importlib.util
import io
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location('prepare_update', Path(__file__).resolve().parents[1] / 'prepare_update.py')
prepare_update = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(prepare_update)
COMMIT = 'a' * 40
REPOSITORY = 'TheApple05/CurryAccountManger-Appa'


def jar_bytes(minecraft, loader='fabric', java=25, extra=None):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w', zipfile.ZIP_DEFLATED) as archive:
        if loader == 'fabric':
            archive.writestr('fabric.mod.json', json.dumps({'id': 'autism', 'depends': {'minecraft': minecraft}}))
        else:
            archive.writestr('mcmod.info', json.dumps([{'modid': 'curryfarts', 'mcversion': minecraft}]))
        archive.writestr('autismclient/ReleaseFixture.class', b'\xca\xfe\xba\xbe\x00\x00' + (java + 44).to_bytes(2, 'big'))
        for name, content in (extra or {}).items():
            archive.writestr(name, content)
    return stream.getvalue()


def digest(content):
    return hashlib.sha256(content).hexdigest()


class PrepareUpdateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='prepare-update-tests-')
        self.addCleanup(self.temporary.cleanup)
        self.sandbox = Path(self.temporary.name)
        self.root = self.sandbox / 'repo'
        (self.root / 'releases').mkdir(parents=True)
        (self.root / 'releases/notes.md').write_text('Account manager update.\n', encoding='utf-8')
        self.output = self.root / 'dist/github-release'
        self.manifest = {'schema': 1, 'version': '5.0.4', 'title': 'Account manager 5.0.4',
                         'notes': 'releases/notes.md', 'supported': [], 'artifacts': [], 'attachments': []}
        for minecraft, loader, java in (('1.8.9', 'forge', 8), ('26.3', 'fabric', 25)):
            self.add_artifact(minecraft, loader, java)
        attachment = b'{"status":"PASSED","checks":2}\n'
        (self.root / 'releases/evidence.json').write_bytes(attachment)
        self.manifest['attachments'].append({'filename': 'evidence.json', 'sha256': digest(attachment),
                                             'source': {'path': 'releases/evidence.json'}})

    def add_artifact(self, minecraft, loader, java):
        name = f'account-manager-{minecraft}-{loader}.jar'
        content = jar_bytes(minecraft, loader, java)
        (self.root / 'releases' / name).write_bytes(content)
        self.manifest['supported'].append({'minecraft': minecraft, 'loader': loader})
        self.manifest['artifacts'].append({'minecraft': minecraft, 'loader': loader, 'java': java,
            'filename': name, 'sha256': digest(content), 'source': {'path': 'releases/' + name},
            'requirements': f'{loader.title()}; Java {java}', 'sourceRef': 'b' * 40,
            'validation': {'runtimeChecks': 61, 'deterministicChecks': 161}})

    def replace_jar(self, index, content):
        entry = self.manifest['artifacts'][index]
        (self.root / entry['source']['path']).write_bytes(content)
        entry['sha256'] = digest(content)

    def run_prepare(self, downloader=None):
        path = self.root / 'release.json'
        path.write_text(json.dumps(self.manifest), encoding='utf-8')
        return prepare_update.prepare(path, self.output, COMMIT, REPOSITORY,
                                      root=self.root, downloader=downloader)

    def assert_rejected(self, message):
        with self.assertRaisesRegex(prepare_update.ValidationError, message):
            self.run_prepare()
        self.assertFalse(self.output.exists())
        self.assertFalse(self.output.parent.exists(), 'Failure must precede output staging')

    def test_complete_seven_version_release_has_publishable_plan_and_exact_checksums(self):
        for mc, loader, java in (('1.12.2', 'forge', 8), ('1.16.5', 'fabric', 8),
                                ('1.21.10', 'fabric', 21), ('1.21.11', 'fabric', 21), ('26.2', 'fabric', 25)):
            self.add_artifact(mc, loader, java)
        plan = self.run_prepare()
        self.assertEqual(plan['tag'], 'v5.0.4')
        self.assertEqual(plan['commit'], COMMIT)
        self.assertEqual(len(plan['assets']), 10)  # 7 JARs, attachment, manifest, checksums
        self.assertEqual([asset['name'] for asset in plan['assets']], sorted(asset['name'] for asset in plan['assets']))
        for asset in plan['assets']:
            data = (self.output / asset['path']).read_bytes()
            self.assertEqual(asset['name'], asset['path'])
            self.assertEqual(asset['sha256'], digest(data))
            self.assertEqual(asset['size'], len(data))
        for line in (self.output / 'SHA256SUMS.txt').read_text().splitlines():
            expected, filename = line.split('  ', 1)
            self.assertEqual(expected, digest((self.output / filename).read_bytes()))
        public = json.loads((self.output / 'release-manifest.json').read_text())
        self.assertEqual(len(public['supported']), 7)
        self.assertTrue(all(entry['sourceRef'] == 'b' * 40 for entry in public['artifacts']))
        self.assertTrue(all('source' not in entry for entry in public['artifacts'] + public['attachments']))
        self.assertNotIn('releases/', json.dumps(public))
        names = {asset['name'] for asset in plan['assets']}
        self.assertNotIn('plan.json', names)
        self.assertNotIn('notes.md', names)
        self.assertIn('Minecraft 26.2 / Fabric', (self.output / 'notes.md').read_text(encoding='utf-8'))

    def test_missing_supported_artifact_fails_before_output(self):
        self.manifest['artifacts'].pop()
        self.assert_rejected('match every supported pair')

    def test_duplicate_artifact_pair_is_rejected(self):
        self.manifest['artifacts'].append(copy.deepcopy(self.manifest['artifacts'][0]))
        self.assert_rejected('duplicate pairs')

    def test_duplicate_filenames_case_insensitive_are_rejected(self):
        self.manifest['artifacts'][1]['filename'] = self.manifest['artifacts'][0]['filename'].upper()
        self.assert_rejected('filenames must be unique')

    def test_hash_mismatch_cannot_create_partial_output(self):
        self.manifest['artifacts'][1]['sha256'] = '0' * 64
        self.assert_rejected('SHA-256 mismatch')

    def test_source_path_escape_is_rejected(self):
        self.manifest['artifacts'][1]['source'] = {'path': '../outside.jar'}
        self.assert_rejected('repository-relative')

    def test_source_symlink_escape_is_rejected(self):
        outside = self.sandbox / 'outside.jar'
        outside.write_bytes(b'outside')
        link = self.root / 'releases/link.jar'
        try:
            os.symlink(outside, link)
        except (OSError, NotImplementedError):
            self.skipTest('Host does not permit unprivileged symlinks')
        self.manifest['artifacts'][1]['source'] = {'path': 'releases/link.jar'}
        self.assert_rejected('escapes the repository')

    def test_wrong_fabric_minecraft_is_rejected_after_matching_hash(self):
        self.replace_jar(1, jar_bytes('26.2'))
        self.assert_rejected('Fabric metadata targets')

    def test_wrong_forge_minecraft_is_rejected_after_matching_hash(self):
        self.replace_jar(0, jar_bytes('1.12.2', 'forge', 8))
        self.assert_rejected('Forge metadata targets')

    def test_qa_classes_are_rejected(self):
        self.replace_jar(1, jar_bytes('26.3', extra={'autismclient/qa/Smoke.class': b'\xca\xfe\xba\xbe\0\0\0E'}))
        self.assert_rejected('QA, development, or test classes')

    def test_vanilla_classes_are_rejected(self):
        self.replace_jar(1, jar_bytes('26.3', extra={'net/minecraft/client/Minecraft.class': b'\xca\xfe\xba\xbe\0\0\0E'}))
        self.assert_rejected('vanilla Minecraft classes')

    def test_bytecode_newer_than_declared_java_is_rejected(self):
        self.replace_jar(0, jar_bytes('1.8.9', 'forge', 21))
        self.assert_rejected('exceeds the declared Java')

    def test_sources_classifier_cannot_be_an_installable_artifact(self):
        self.manifest['artifacts'][1]['filename'] = 'account-manager-sources.jar'
        self.assert_rejected('installable JARs')

    def test_wrong_repository_url_is_rejected_before_network(self):
        self.manifest['artifacts'][1]['source'] = {'url': 'https://github.com/other/repo/releases/download/v5/a.jar'}
        called = []
        with self.assertRaisesRegex(prepare_update.ValidationError, 'this GitHub repository'):
            self.run_prepare(lambda url: called.append(url))
        self.assertEqual(called, [])
        self.assertFalse(self.output.exists())

    def test_remote_release_source_uses_injected_download_without_network(self):
        entry = self.manifest['artifacts'][1]
        content = (self.root / entry['source']['path']).read_bytes()
        url = f'https://github.com/{REPOSITORY}/releases/download/v5.0.3/account%2B26.3.jar'
        entry['source'] = {'url': url}
        called = []
        def download(source_url):
            called.append(source_url)
            return content
        plan = self.run_prepare(download)
        self.assertEqual(called, [url])
        self.assertEqual(len(plan['assets']), 5)

    def test_oversized_download_is_rejected(self):
        self.manifest['artifacts'][1]['source'] = {'url': f'https://github.com/{REPOSITORY}/releases/download/v5.0.3/a.jar'}
        # Keep local fixtures under the limit; make the fake response one byte larger.
        with patch.object(prepare_update, 'MAX_SOURCE_BYTES', 4096):
            with self.assertRaisesRegex(prepare_update.ValidationError, 'size limit'):
                self.run_prepare(lambda url: b'x' * 4097)
        self.assertFalse(self.output.exists())

    def test_existing_output_is_never_overwritten(self):
        self.output.mkdir(parents=True)
        marker = self.output / 'keep.txt'
        marker.write_text('keep')
        with self.assertRaisesRegex(prepare_update.ValidationError, 'new directory'):
            self.run_prepare()
        self.assertEqual(marker.read_text(), 'keep')

    def test_minecraft_build_suffix_cannot_be_shared_release_version(self):
        self.manifest['version'] = '5.0.4+26.3'
        self.assert_rejected('shared semantic version')

    def test_prerelease_versions_are_not_stable_grouped_releases(self):
        self.manifest['version'] = '5.0.4-rc.1'
        self.assert_rejected('shared semantic version')

    def test_raw_credential_attachment_is_rejected_but_aggregate_receipts_are_allowed(self):
        content = b'{"refreshToken":"private-value-that-is-not-a-real-token"}'
        (self.root / 'releases/evidence.json').write_bytes(content)
        self.manifest['attachments'][0]['sha256'] = digest(content)
        self.assert_rejected('populated credential fields')

    def test_source_jar_attachment_is_allowed(self):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            archive.writestr('autismclient/Example.java', 'package autismclient; class Example {}')
        content = stream.getvalue()
        (self.root / 'releases/source.jar').write_bytes(content)
        self.manifest['attachments'].append({'filename': 'account-manager-sources.jar', 'sha256': digest(content),
                                             'source': {'path': 'releases/source.jar'}})
        self.assertEqual(len(self.run_prepare()['assets']), 6)

    def test_release_bundle_preserves_a_verified_main_jars_nested_dependency(self):
        dependency = io.BytesIO()
        with zipfile.ZipFile(dependency, 'w') as archive:
            archive.writestr('org/example/Dependency.class', b'\xca\xfe\xba\xbe\0\0\0A')
        self.replace_jar(1, jar_bytes('26.3', extra={'META-INF/jars/dependency.jar': dependency.getvalue()}))
        entry = self.manifest['artifacts'][1]
        bundle = io.BytesIO()
        with zipfile.ZipFile(bundle, 'w') as archive:
            archive.writestr(entry['filename'], (self.root / entry['source']['path']).read_bytes())
        content = bundle.getvalue()
        (self.root / 'releases/bundle.zip').write_bytes(content)
        self.manifest['attachments'].append({'filename': 'account-manager-bundle.zip', 'sha256': digest(content),
                                             'source': {'path': 'releases/bundle.zip'}})
        self.assertEqual(len(self.run_prepare()['assets']), 6)

    def test_unknown_installable_jar_inside_nested_zip_is_rejected(self):
        inner = io.BytesIO()
        with zipfile.ZipFile(inner, 'w') as archive:
            archive.writestr('unknown.jar', jar_bytes('26.2'))
        outer = io.BytesIO()
        with zipfile.ZipFile(outer, 'w') as archive:
            archive.writestr('inner.zip', inner.getvalue())
        content = outer.getvalue()
        (self.root / 'releases/bundle.zip').write_bytes(content)
        self.manifest['attachments'].append({'filename': 'account-manager-bundle.zip', 'sha256': digest(content),
                                             'source': {'path': 'releases/bundle.zip'}})
        self.assert_rejected('outside the supported release matrix')

    def test_bundle_allows_token_importer_screenshot_and_provenance_names(self):
        bundle = io.BytesIO()
        with zipfile.ZipFile(bundle, 'w') as archive:
            archive.writestr('screenshots/10-session-token-importer.png', b'\x89PNG\r\n\x1a\nfixture')
            archive.writestr('TOKEN-REFRESHER-PROVENANCE.json', '{"source":"public protocol reference"}')
        content = bundle.getvalue()
        (self.root / 'releases/bundle.zip').write_bytes(content)
        self.manifest['attachments'].append({'filename': 'account-manager-bundle.zip', 'sha256': digest(content),
                                             'source': {'path': 'releases/bundle.zip'}})
        self.assertEqual(len(self.run_prepare()['assets']), 6)


if __name__ == '__main__':
    unittest.main()
