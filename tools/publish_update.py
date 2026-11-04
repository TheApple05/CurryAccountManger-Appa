#!/usr/bin/env python3
"""Publish one verified, resumable GitHub release. Credentials come from the environment."""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


class PublishError(Exception):
    pass


class Missing(PublishError):
    pass


class Uncertain(PublishError):
    """A write may have reached GitHub; reconcile before taking another action."""


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


class GitHub:
    def __init__(self, token):
        if not token or any(c in token for c in "\r\n"):
            raise PublishError("Set GH_TOKEN or GITHUB_TOKEN in the environment.")
        self.token = token
        self.opener = urllib.request.build_opener(NoRedirects())

    def request(self, method, path, data=None):
        url = path if path.startswith("https://") else "https://api.github.com" + path
        parsed = urllib.parse.urlsplit(url)
        if (parsed.scheme != "https" or parsed.hostname not in {"api.github.com", "uploads.github.com"}
                or parsed.username or parsed.password or parsed.port not in (None, 443)):
            raise PublishError("Refusing an unexpected GitHub API host.")
        binary = isinstance(data, bytes)
        payload = data if binary or data is None else json.dumps(data).encode("utf-8")
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
                   "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "Curry-release-publisher"}
        if payload is not None:
            headers["Content-Type"] = "application/octet-stream" if binary else "application/json"
        request = urllib.request.Request(url, data=payload, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=120) as response:
                raw = response.read(8 * 1024 * 1024 + 1)
                if len(raw) > 8 * 1024 * 1024:
                    raise PublishError("GitHub API response exceeded the size limit.")
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as failure:
            failure.close()
            if failure.code == 404:
                raise Missing("GitHub resource not found.") from None
            error = Uncertain if method != "GET" and failure.code >= 500 else PublishError
            raise error("GitHub returned HTTP " + str(failure.code) + ".") from None
        except (urllib.error.URLError, OSError, TimeoutError, http.client.HTTPException):
            error = PublishError if method == "GET" else Uncertain
            raise error("GitHub request failed; rerun the same plan to resume.") from None
        except (ValueError, UnicodeError):
            error = PublishError if method == "GET" else Uncertain
            raise error("GitHub returned an invalid response.") from None


def basename(value):
    return (isinstance(value, str) and value not in ("", ".", "..")
            and not any(c in value for c in '/\\:') and not any(ord(c) < 32 for c in value))


def load_plan(path):
    path = Path(path).resolve()
    try:
        plan = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        raise PublishError("Cannot read the release plan.") from None
    if not isinstance(plan, dict):
        raise PublishError("Release plan must be an object.")
    if not re.fullmatch(r"\d+\.\d+\.\d+", str(plan.get("version", ""))):
        raise PublishError("Plan version must be MAJOR.MINOR.PATCH.")
    if plan.get("tag") != "v" + plan["version"]:
        raise PublishError("Plan tag must equal vVERSION.")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", str(plan.get("commit", ""))):
        raise PublishError("Plan commit must be a full 40-character SHA.")
    plan["commit"] = plan["commit"].lower()
    if plan.get("repository") != "TheApple05/CurryAccountManger-Appa":
        raise PublishError("Plan repository does not match the supported repository.")
    if not isinstance(plan.get("title"), str) or not plan["title"].strip():
        raise PublishError("Plan needs a release title.")
    if not basename(plan.get("notesPath")):
        raise PublishError("Release notes must be a file beside the plan.")
    root = path.parent
    notes = (root / plan["notesPath"]).resolve()
    if notes.parent != root:
        raise PublishError("Release notes escape the plan directory.")
    try:
        plan["body"] = notes.read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        raise PublishError("Cannot read the release notes.") from None
    assets = plan.get("assets")
    if not isinstance(assets, list) or not assets:
        raise PublishError("Plan needs release assets.")
    names, paths = set(), set()
    for asset in assets:
        if not isinstance(asset, dict) or not basename(asset.get("name")) or not basename(asset.get("path")):
            raise PublishError("Asset names and paths must be basenames.")
        name = asset["name"]
        if name in names or asset["path"] in paths:
            raise PublishError("Duplicate asset name or local path.")
        if name.lower().endswith(".jar") and re.search(r"(?:^|[-_.+ ])(?:qa|dev|test)(?:[-_.+ ]|$)", name, re.I):
            raise PublishError("QA, development, and test JARs cannot be published.")
        if not re.fullmatch(r"[0-9a-fA-F]{64}", str(asset.get("sha256", ""))):
            raise PublishError("Asset needs a SHA-256 digest.")
        if type(asset.get("size")) is not int or asset["size"] <= 0:
            raise PublishError("Asset size must be a positive integer.")
        local = (root / asset["path"]).resolve()
        if local.parent != root or not local.is_file():
            raise PublishError("Asset is missing or escapes the plan directory.")
        asset["sha256"] = asset["sha256"].lower()
        asset["local"] = local
        checked_bytes(asset)  # Validate every local asset before the first API mutation.
        names.add(name)
        paths.add(asset["path"])
    plan["root"] = root
    return plan


def checked_bytes(asset):
    try:
        data = asset["local"].read_bytes()
    except OSError:
        raise PublishError("Cannot read asset: " + asset["name"]) from None
    if len(data) != asset["size"] or hashlib.sha256(data).hexdigest() != asset["sha256"]:
        raise PublishError("Local asset hash or size mismatch: " + asset["name"])
    return data


class Publisher:
    def __init__(self, api, plan):
        self.api, self.plan = api, plan
        self.repo = "/repos/" + plan["repository"]

    def pages(self, path):
        for page in range(1, 1001):
            items = self.api.request("GET", path + "?per_page=100&page=" + str(page))
            if not isinstance(items, list) or not all(isinstance(item, dict) for item in items):
                raise PublishError("Unexpected GitHub listing response.")
            yield from items
            if len(items) < 100:
                return
        raise PublishError("GitHub pagination limit exceeded.")

    def find_release(self):
        return next((r for r in self.pages(self.repo + "/releases") if r.get("tag_name") == self.plan["tag"]), None)

    def check_commit(self, release=None):
        try:
            ref = self.api.request("GET", self.repo + "/git/ref/tags/" + urllib.parse.quote(self.plan["tag"], safe=""))
        except Missing:
            if release and (not release.get("draft") or release.get("target_commitish") != self.plan["commit"]):
                raise PublishError("Release commit cannot be verified.") from None
            return
        obj = ref.get("object", {})
        for _ in range(8):
            if obj.get("type") == "commit":
                if obj.get("sha") != self.plan["commit"]:
                    raise PublishError("Existing tag points to a different commit.")
                return
            if obj.get("type") != "tag" or not re.fullmatch(r"[0-9a-f]{40}", str(obj.get("sha", ""))):
                break
            obj = self.api.request("GET", self.repo + "/git/tags/" + obj["sha"]).get("object", {})
        raise PublishError("Cannot resolve the release tag to a commit.")

    def assets(self, release):
        return list(self.pages(self.repo + "/releases/" + str(release["id"]) + "/assets"))

    @staticmethod
    def matches(remote, local):
        return remote.get("size") == local["size"] and remote.get("digest") == "sha256:" + local["sha256"]

    def validate_assets(self, assets, complete):
        expected = {a["name"]: a for a in self.plan["assets"]}
        present = {}
        for asset in assets:
            name = asset.get("name")
            if name not in expected or name in present or not self.matches(asset, expected[name]):
                raise PublishError("Existing release assets differ from the verified plan; nothing was overwritten.")
            present[name] = asset
        if complete and set(present) != set(expected):
            raise PublishError("Release assets are incomplete; rerun the same plan to resume.")
        return present

    def upload_url(self, release, name):
        base = str(release.get("upload_url", "")).split("{", 1)[0]
        expected = "https://uploads.github.com" + self.repo + "/releases/" + str(release["id"]) + "/assets"
        if base != expected:
            raise PublishError("Unexpected GitHub upload URL.")
        return base + "?" + urllib.parse.urlencode({"name": name})

    def latest_flag(self):
        current = tuple(map(int, self.plan["version"].split(".")))
        # Queue order can differ from event order. Completing an older queued
        # update must not displace a newer published stable unified release.
        for release in self.pages(self.repo + "/releases"):
            match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", str(release.get("tag_name", "")))
            if (match and release.get("draft") is False and not release.get("prerelease")
                    and tuple(map(int, match.groups())) > current):
                return "false"
        return "true"

    def receipt(self, release, assets, status):
        download = "https://github.com/" + self.plan["repository"] + "/releases/download/" + urllib.parse.quote(self.plan["tag"], safe="") + "/"
        return {"status": status, "repository": self.plan["repository"], "version": self.plan["version"],
                "tag": self.plan["tag"], "commit": self.plan["commit"], "releaseId": release["id"],
                "url": "https://github.com/" + self.plan["repository"] + "/releases/tag/" + self.plan["tag"],
                "assets": [{"name": a["name"], "size": a["size"], "sha256": a["digest"][7:],
                            "url": download + urllib.parse.quote(a["name"], safe="")} for a in assets]}

    def run(self):
        release = self.find_release()
        self.check_commit(release)
        if release and release.get("draft") is False:
            if release.get("name") != self.plan["title"] or release.get("body") != self.plan["body"]:
                raise PublishError("Published title or notes differ; use a new version. Nothing was overwritten.")
            assets = self.assets(release)
            self.validate_assets(assets, complete=True)
            return self.receipt(release, assets, "unchanged")
        if release is None:
            try:
                release = self.api.request("POST", self.repo + "/releases", {
                    "tag_name": self.plan["tag"], "target_commitish": self.plan["commit"],
                    "name": self.plan["title"], "body": self.plan["body"], "draft": True, "prerelease": False})
            except Uncertain:
                release = self.find_release()
                if release is None:
                    raise PublishError("Draft creation is uncertain; rerun the same plan to reconcile.") from None
        if release.get("draft") is not True or type(release.get("id")) is not int:
            raise PublishError("Expected a draft release; refusing to change a published release.")
        self.check_commit(release)
        present = self.validate_assets(self.assets(release), complete=False)
        for asset in self.plan["assets"]:
            if asset["name"] in present:
                continue
            url, data = self.upload_url(release, asset["name"]), checked_bytes(asset)
            try:
                self.api.request("POST", url, data)
            except PublishError:
                # Never blindly retry an upload: a timed-out write may already exist.
                reconciled = self.validate_assets(self.assets(release), complete=False)
                if asset["name"] not in reconciled:
                    raise PublishError("Asset upload is incomplete or uncertain; rerun the same plan to resume.") from None
        assets = self.assets(release)
        self.validate_assets(assets, complete=True)
        self.check_commit(release)
        path = self.repo + "/releases/" + str(release["id"])
        latest = self.latest_flag()
        try:
            published = self.api.request("PATCH", path, {"draft": False, "prerelease": False,
                "name": self.plan["title"], "body": self.plan["body"], "make_latest": latest})
        except Uncertain:
            published = self.api.request("GET", path)
        if published.get("draft") is not False:
            raise PublishError("Publication is incomplete or uncertain; rerun the same plan to reconcile.")
        self.check_commit(published)
        assets = self.assets(published)
        self.validate_assets(assets, complete=True)
        return self.receipt(published, assets, "published")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        plan = load_plan(args.plan)
        api = GitHub(os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"))
        receipt = Publisher(api, plan).run()
        output = json.dumps(receipt, indent=2) + "\n"
        (plan["root"] / "publish-receipt.json").write_text(output, encoding="utf-8")
        print(output, end="")
        return 0
    except (PublishError, OSError) as failure:
        message = str(failure) if isinstance(failure, PublishError) else "Could not save the publish receipt."
        print("publish_update: " + message, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
