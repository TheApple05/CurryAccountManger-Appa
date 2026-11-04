# Publishing an update

Each update has one GitHub release named `vVERSION`, containing a matching
installable JAR for every supported Minecraft version. The update number in
[`release.json`](../release.json) is independent of the versions embedded in the
individual JARs. An unchanged, previously tested build can be carried forward
without renaming or modifying its bytes.

The supported set is Forge **1.8.9 / 1.12.2** and Fabric **1.16.5 / 1.21.10 /
1.21.11 / 26.2 / 26.3**. Preserve this complete set when preparing an update.
Keep older updates available in the [release history](../releases/README.md).

## Prepare the artifacts and manifest

1. Build and test each changed mod in its source checkout, including a smoke test
   of the exact installable JAR. Save its validation results. Existing unchanged
   builds may retain their recorded validation and SHA-256 digest.
2. Put new artifacts and validation files at their intended repository paths, or
   use a stable GitHub release asset URL for an existing artifact. Calculate the
   SHA-256 of every file. Include the required installable JAR for each supported
   Minecraft/loader pair, plus the desired source JARs, validation files and
   bundles in `attachments`.
3. Bump `version` in `release.json` to a new update number, update `title`, and
   point `notes` to a committed Markdown release-notes file. Update artifact
   paths/URLs, filenames, digests, loader/Java requirements, validation metadata
   and `sourceRef` commits as needed. Keep `supported` and `artifacts` complete.
4. Describe changed behavior and the actual validation performed in the notes.
   Identify carried-forward builds and relevant testing limits. A release
   version bump does not require rebuilding unchanged Minecraft versions.
5. Run the automation checks and prepare a local plan for review:

   ```powershell
   python -m unittest discover -s tools/tests -v
   python -m py_compile tools/prepare_update.py tools/publish_update.py
   $releaseCommit = git rev-parse HEAD
   python tools/prepare_update.py --manifest release.json --output dist/github-release --commit $releaseCommit --repository TheApple05/CurryAccountManger-Appa
   ```

   Use a fresh output directory for each local preparation. Review `plan.json`,
   `notes.md`, `release-manifest.json`, `SHA256SUMS.txt` and the collected assets.
   This preparation verifies files and creates local output; it does not publish.
6. Commit the manifest, notes, artifacts and validation evidence, then push the
   update to `origin main`. A push to `main` that changes `release.json`
   automatically publishes the complete `vVERSION` release. Do not create a
   separate release or release tag for each Minecraft version.

For update **5.0.4**, the five older JARs are carried forward from **5.0.3** and
the 26.2/26.3 JARs are added from their tested local release bundles. Their
[maintained 26.x source commit](https://github.com/TheApple05/CurryAccountManger-Appa/tree/f09a7e4c838e76878fb0a0bc434a62184071483f)
and source JAR attachments identify the modern implementation. The root build on
`main` remains the 1.21.11 project; adding modern release artifacts does not make
that root build target 26.x. Use the build instructions at the recorded source
commit for those ports.

## What the workflow does

[`publish-release.yml`](../.github/workflows/publish-release.yml) checks out the
exact triggering commit and runs the release automation's unit tests and Python
syntax checks. The preparer checks the full supported-version matrix, validates
artifact metadata and SHA-256 digests, and produces the complete publication
plan, checksums and release manifest.

The publisher uses the workflow's built-in GitHub token to upload the planned
assets and publish one complete release. No personal token secret is required.
The workflow defaults to read access; its publishing job has repository contents
write access. Third-party action references are pinned to full commit SHAs.

This workflow publishes previously built and tested artifacts. It does **not**
compile the mods, launch Minecraft, rerun packaged-client tests or authenticate
real accounts. Record those checks accurately before updating the manifest.

Release runs share `release-publisher` concurrency and use `queue: max` with
`cancel-in-progress: false`, preserving up to 100 pending updates while one runs.
GitHub documents this behavior in its
[concurrency reference](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency).

## Reruns and failed publication

The workflow also supports **Run workflow** on `main`. Rerunning an already
published version is harmless when its tag, commit, notes and exact asset set
match the plan: the publisher verifies the release and reports `unchanged`.
An existing release with conflicting content causes a failure; the publisher
does not silently replace its assets.

Read the failed step before retrying. Correct a missing file, incomplete matrix
or digest mismatch before publishing. For a new update to an already published
release, bump `version` and publish a new grouped release. Keep source tags and
historical releases intact.
