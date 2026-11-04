"""Offline stateful API tests: no credentials, network, or actual release writes."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from urllib.parse import parse_qs, urlsplit

SPEC = importlib.util.spec_from_file_location("publish_update", Path(__file__).parents[1] / "publish_update.py")
publisher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(publisher)
COMMIT = "a" * 40
REPO = "/repos/TheApple05/CurryAccountManger-Appa"


class FakeGitHub:
    def __init__(self, release=None, assets=(), commit=None):
        self.release = copy.deepcopy(release)
        self.uploaded = copy.deepcopy(list(assets))
        self.commit = commit
        self.calls = []
        self.bad_upload_digest = False
        self.uncertain_upload = False
        self.upload_absent = False
        self.uncertain_create = False
        self.uncertain_publish = False
        self.earlier_releases = []
        self.extra_upload = None

    @staticmethod
    def draft():
        return {"id": 42, "tag_name": "v5.0.4", "draft": True, "target_commitish": COMMIT,
                "name": "Curry Account Manager 5.0.4", "body": "All supported Minecraft versions.",
                "upload_url": "https://uploads.github.com" + REPO + "/releases/42/assets{?name,label}"}

    def mutations(self):
        return [call for call in self.calls if call[0] != "GET"]

    def request(self, method, url, data=None):
        self.calls.append((method, url, copy.deepcopy(data)))
        parsed = urlsplit(url)
        path = parsed.path
        query = parse_qs(parsed.query)
        if path == REPO + "/git/ref/tags/v5.0.4" and method == "GET":
            if self.commit is None:
                raise publisher.Missing("Not found")
            return {"object": {"type": "commit", "sha": self.commit}}
        if path == REPO + "/releases" and method == "GET":
            all_releases = self.earlier_releases + ([self.release] if self.release else [])
            page = int(query["page"][0])
            return copy.deepcopy(all_releases[(page - 1) * 100:page * 100])
        if path == REPO + "/releases" and method == "POST":
            if self.release:
                raise AssertionError("Duplicate draft creation")
            self.release = self.draft()
            self.release.update(data)
            if self.uncertain_create:
                raise publisher.Uncertain("Creation timed out after reaching GitHub")
            return copy.deepcopy(self.release)
        if path == REPO + "/releases/42/assets" and method == "GET":
            page = int(query["page"][0])
            return copy.deepcopy(self.uploaded[(page - 1) * 100:page * 100])
        if path == REPO + "/releases/42/assets" and method == "POST":
            name = query["name"][0]
            if any(asset["name"] == name for asset in self.uploaded):
                raise AssertionError("Blind duplicate asset upload")
            digest = "0" * 64 if self.bad_upload_digest else hashlib.sha256(data).hexdigest()
            asset = {"id": len(self.uploaded) + 1, "name": name, "size": len(data), "digest": "sha256:" + digest}
            if not self.upload_absent:
                self.uploaded.append(asset)
            if self.extra_upload:
                self.uploaded.append(self.extra_upload)
            if self.uncertain_upload:
                raise publisher.Uncertain("Upload timed out after reaching GitHub")
            return copy.deepcopy(asset)
        if path == REPO + "/releases/42" and method == "PATCH":
            self.release.update(data)
            self.commit = self.release["target_commitish"]
            if self.uncertain_publish:
                raise publisher.Uncertain("Publication timed out after reaching GitHub")
            return copy.deepcopy(self.release)
        if path == REPO + "/releases/42" and method == "GET":
            return copy.deepcopy(self.release)
        raise AssertionError("Unexpected API operation: " + method + " " + url)


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.raw_plan = {"version": "5.0.4", "tag": "v5.0.4", "title": "Curry Account Manager 5.0.4",
                         "commit": COMMIT, "repository": "TheApple05/CurryAccountManger-Appa",
                         "notesPath": "notes.md", "assets": []}
        (self.root / "notes.md").write_text("All supported Minecraft versions.", encoding="utf-8")
        for name, data in [("curry-5.0.4+26.2.jar", b"first packaged artifact"),
                           ("curry-5.0.4+26.3.jar", b"second packaged artifact")]:
            (self.root / name).write_bytes(data)
            self.raw_plan["assets"].append({"name": name, "path": name, "size": len(data),
                                             "sha256": hashlib.sha256(data).hexdigest()})

    def plan(self):
        path = self.root / "plan.json"
        path.write_text(json.dumps(self.raw_plan), encoding="utf-8")
        return publisher.load_plan(path)

    def remote(self, index):
        local = self.raw_plan["assets"][index]
        return {"id": index + 1, "name": local["name"], "size": local["size"], "digest": "sha256:" + local["sha256"]}

    def run_plan(self, api):
        return publisher.Publisher(api, self.plan()).run()

    def test_fresh_complete_release_publishes_once_after_verified_uploads(self):
        api = FakeGitHub()
        receipt = self.run_plan(api)
        self.assertEqual(receipt["status"], "published")
        self.assertEqual(len(receipt["assets"]), 2)
        writes = api.mutations()
        self.assertEqual([call[0] for call in writes], ["POST", "POST", "POST", "PATCH"])
        self.assertTrue(writes[0][2]["draft"])
        self.assertEqual(writes[0][2]["target_commitish"], COMMIT)
        self.assertEqual(writes[-1][2]["make_latest"], "true")
        self.assertFalse(writes[-1][2]["draft"])
        self.assertTrue(all(asset["url"].startswith("https://github.com/") for asset in receipt["assets"]))

    def test_partial_draft_resumes_only_missing_assets(self):
        api = FakeGitHub(FakeGitHub.draft(), [self.remote(0)])
        self.run_plan(api)
        writes = api.mutations()
        self.assertEqual([call[0] for call in writes], ["POST", "PATCH"])
        self.assertIn("26.3.jar", writes[0][1])

    def test_older_queued_update_does_not_replace_newer_stable_latest(self):
        api = FakeGitHub(FakeGitHub.draft(), [self.remote(0), self.remote(1)])
        api.earlier_releases = [{"tag_name": "v5.0.5", "draft": False, "prerelease": False}]
        self.assertEqual(self.run_plan(api)["status"], "published")
        patch = next(call for call in api.mutations() if call[0] == "PATCH")
        self.assertEqual(patch[2]["make_latest"], "false")

    def test_latest_comparison_ignores_older_per_minecraft_and_prereleases(self):
        api = FakeGitHub()
        api.earlier_releases = [
            {"tag_name": "v5.0.3", "draft": False, "prerelease": False},
            {"tag_name": "v5.0.99+26.3", "draft": False, "prerelease": False},
            {"tag_name": "v9.0.0", "draft": False, "prerelease": True},
            {"tag_name": "v8.0.0", "draft": True, "prerelease": False},
            {"tag_name": "v7.0.0-rc.1", "draft": False, "prerelease": False},
        ]
        self.run_plan(api)
        patch = next(call for call in api.mutations() if call[0] == "PATCH")
        self.assertEqual(patch[2]["make_latest"], "true")

    def test_published_rerun_is_read_only_and_idempotent(self):
        api = FakeGitHub()
        self.run_plan(api)
        api.calls.clear()
        receipt = self.run_plan(api)
        self.assertEqual(receipt["status"], "unchanged")
        self.assertEqual(api.mutations(), [])

    def test_changed_published_asset_is_never_replaced(self):
        release = FakeGitHub.draft()
        release["draft"] = False
        wrong = self.remote(0)
        wrong["digest"] = "sha256:" + "0" * 64
        api = FakeGitHub(release, [wrong, self.remote(1)], COMMIT)
        with self.assertRaisesRegex(publisher.PublishError, "differ"):
            self.run_plan(api)
        self.assertEqual(api.mutations(), [])

    def test_changed_published_notes_or_title_prevent_noop(self):
        for field in ("body", "name"):
            with self.subTest(field=field):
                release = FakeGitHub.draft()
                release.update({"draft": False, field: "Conflicting manually edited content"})
                api = FakeGitHub(release, [self.remote(0), self.remote(1)], COMMIT)
                with self.assertRaisesRegex(publisher.PublishError, "title or notes differ"):
                    self.run_plan(api)
                self.assertEqual(api.mutations(), [])

    def test_published_asset_set_size_and_missing_digest_must_match(self):
        release = FakeGitHub.draft()
        release["draft"] = False
        for assets in ([self.remote(0)], [self.remote(0), dict(self.remote(1), size=1)],
                       [self.remote(0), dict(self.remote(1), digest=None)]):
            with self.subTest(assets=assets):
                api = FakeGitHub(release, assets, COMMIT)
                with self.assertRaises(publisher.PublishError):
                    self.run_plan(api)
                self.assertEqual(api.mutations(), [])

    def test_wrong_tag_commit_is_rejected_before_any_write(self):
        api = FakeGitHub(FakeGitHub.draft(), commit="b" * 40)
        with self.assertRaisesRegex(publisher.PublishError, "different commit"):
            self.run_plan(api)
        self.assertEqual(api.mutations(), [])

    def test_uncreated_draft_tag_still_requires_exact_target_commit(self):
        release = FakeGitHub.draft()
        release["target_commitish"] = "main"
        api = FakeGitHub(release)
        with self.assertRaisesRegex(publisher.PublishError, "commit cannot be verified"):
            self.run_plan(api)
        self.assertEqual(api.mutations(), [])

    def test_upload_digest_mismatch_prevents_publication(self):
        api = FakeGitHub()
        api.bad_upload_digest = True
        with self.assertRaisesRegex(publisher.PublishError, "differ"):
            self.run_plan(api)
        self.assertTrue(api.release["draft"])
        self.assertFalse(any(call[0] == "PATCH" for call in api.calls))

    def test_unexpected_extra_remote_asset_prevents_publication(self):
        api = FakeGitHub()
        api.extra_upload = {"name": "unexpected.jar", "size": 1, "digest": "sha256:" + "0" * 64}
        with self.assertRaisesRegex(publisher.PublishError, "differ"):
            self.run_plan(api)
        self.assertTrue(api.release["draft"])

    def test_uncertain_upload_reconciles_without_duplicate_mutation(self):
        api = FakeGitHub()
        api.uncertain_upload = True
        self.assertEqual(self.run_plan(api)["status"], "published")
        uploads = [call for call in api.mutations() if "uploads.github.com" in call[1]]
        self.assertEqual(len(uploads), 2)

    def test_uncertain_upload_absent_fails_resumably_without_retry(self):
        api = FakeGitHub()
        api.uncertain_upload = api.upload_absent = True
        with self.assertRaisesRegex(publisher.PublishError, "uncertain"):
            self.run_plan(api)
        self.assertEqual(len([call for call in api.mutations() if "uploads.github.com" in call[1]]), 1)
        self.assertTrue(api.release["draft"])

    def test_uncertain_create_reconciles_single_existing_draft(self):
        api = FakeGitHub()
        api.uncertain_create = True
        self.assertEqual(self.run_plan(api)["status"], "published")
        self.assertEqual(len([call for call in api.mutations() if call[1] == REPO + "/releases"]), 1)

    def test_uncertain_publish_reconciles_publication(self):
        api = FakeGitHub()
        api.uncertain_publish = True
        self.assertEqual(self.run_plan(api)["status"], "published")
        self.assertEqual(len([call for call in api.calls if call[0] == "PATCH"]), 1)

    def test_release_pagination_finds_draft_after_first_hundred(self):
        api = FakeGitHub(FakeGitHub.draft(), [self.remote(0), self.remote(1)])
        api.earlier_releases = [{"tag_name": "old-" + str(i)} for i in range(100)]
        self.run_plan(api)
        self.assertTrue(any(call[1].endswith("releases?per_page=100&page=2") for call in api.calls))
        self.assertEqual([call[0] for call in api.mutations()], ["PATCH"])

    def test_local_hash_size_and_path_are_checked_before_api(self):
        for change in ({"sha256": "0" * 64}, {"size": 1}, {"path": "../outside.jar"}):
            with self.subTest(change=change):
                saved = dict(self.raw_plan["assets"][0])
                self.raw_plan["assets"][0].update(change)
                with self.assertRaises(publisher.PublishError):
                    self.plan()
                self.raw_plan["assets"][0] = saved

    def test_qa_dev_test_jars_are_rejected_but_source_attachment_allowed(self):
        for name in ("curry-qa.jar", "curry-qa-dev.jar", "curry-dev.jar", "curry-test.jar"):
            with self.subTest(name=name):
                self.raw_plan["assets"][0]["name"] = name
                with self.assertRaisesRegex(publisher.PublishError, "cannot be published"):
                    self.plan()
        self.raw_plan["assets"][0]["name"] = "curry-sources.jar"
        self.assertEqual(len(self.plan()["assets"]), 2)

    def test_unexpected_upload_host_never_receives_request(self):
        release = FakeGitHub.draft()
        release["upload_url"] = "https://example.invalid/steal{?name}"
        api = FakeGitHub(release)
        with self.assertRaisesRegex(publisher.PublishError, "upload URL"):
            self.run_plan(api)
        self.assertEqual(api.mutations(), [])

    def test_http_client_rejects_non_github_host_before_opening(self):
        api = publisher.GitHub("synthetic-not-a-real-token")
        with self.assertRaisesRegex(publisher.PublishError, "unexpected"):
            api.request("POST", "https://example.invalid/", b"artifact")


if __name__ == "__main__":
    unittest.main()
