# Curryfarts Account Manager ports

Download the matching installable JAR from the [GitHub releases](https://github.com/TheApple05/CurryAccountManger-Appa/releases).
Each update has one release containing all seven Minecraft versions, checksums,
and validation results. [Browse updates](releases/README.md).

- **1.8.9** — Forge 11.15.1.2318, Java 8. [Install/build/test details](ports/forge-1.8.9/README.md).
- **1.12.2** — Forge 14.23.5.2847, Java 8. [Install/build/test details](ports/forge-1.12.2/README.md).
- **1.16.5** — Fabric Loader 0.20.1, full Fabric API 0.42.0+1.16, Java 8. [Install/build/test details](ports/fabric-1.16.5/README.md).
- **1.21.10** — Fabric Loader 0.20.1, Fabric API 0.138.4+1.21.10, Java 21. [Install/build/test details](ports/fabric-1.21.10/README.md).
- **1.21.11** — Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Java 21. [Release and validation](releases/1.21.11/README.md).
- **26.2** — Fabric Loader 0.20.1, Fabric API 0.153.0+26.2, Java 25. [Release and validation](releases/26.2/README.md).
- **26.3** — Fabric Loader 0.20.1, Fabric API 0.161.0+26.3, Java 25. [Release and validation](releases/26.3/README.md).

Put one matching account-manager JAR in the instance's `mods` folder, install the
listed loader and dependencies, then open **Multiplayer → Accounts**. Use the versioned release assets for your Minecraft version. These ports retain Melonik's account-manager authorship,
GPL license, artwork, and account-manager functionality.

The five older versions use the requested indigo menu palette: `#06070B` background,
`#101114` panels and idle buttons, black input fields, `#1A3BD1` primary actions
and selected tabs, `#293C94` outlines, `#526FFF` focus/active usernames,
`#8181D9` hover outlines, `#FCFCFC` text, and `#C8C9D1` placeholders.
The four older port releases are **5.0-port.3**; Minecraft 1.21.11 uses **5.0-backport.4**.
Each includes **Session Token Importer** beside **Session Token Switcher**:
paste or load a TXT, validate sessions or refresh Microsoft credentials, and
import verified accounts without leaving Minecraft. Existing accounts receive
updated verified credentials while retaining their stable IDs.

The separate port projects live under `ports/`. Their `validation/` directories
contain screenshots and exact packaged-JAR test results. All four must pass
before `python tools/package_ports.py` collects the JARs and checksums. Build
JDKs differ from client Java versions; follow the instructions in each project.
Successful live Microsoft/session/TheAltening sign-in, multiplayer servers,
other mod combinations, and non-Windows platforms have not been validated.

To publish an update, bump `release.json`, include the tested artifacts and notes,
and push to `origin main`. GitHub Actions automatically publishes one complete
`vVERSION` release containing every supported Minecraft version. See
[release preparation and automation](docs/releases.md). The 26.x source lives at
the commit recorded in the release manifest; the root `main` build remains the
1.21.11 project.

---

# AUTISM Account Manager for Minecraft 1.21.11

Unofficial backport of Melonik's **AUTISM Account Manager 5.0 for 26.2**, supplied in
[TheApple05/Autism-Client](https://github.com/TheApple05/Autism-Client).
The original repository contains a compiled JAR, so this project reconstructs the
account manager's source and adapts it to Minecraft 1.21.11. Original authorship,
artwork, authentication code, and the bundled GPL license are retained.

## Install

Download the [Minecraft 1.21.11 JAR](releases/1.21.11/autism-account-manager-5.0-backport.4+1.21.11.jar).
The [release folder](releases/1.21.11) includes SHA-256 checksums, runtime results,
and screenshots from the packaged client.

1. Use Minecraft **1.21.11**, **Fabric Loader 0.20.1 or newer**, and **Java 21 or newer**.
2. Install [Fabric API 0.141.6+1.21.11](https://modrinth.com/mod/fabric-api/versions?g=1.21.11).
3. Put `autism-account-manager-5.0-backport.4+1.21.11.jar` in that instance's `mods` folder.
4. Open **Multiplayer**, then click **Accounts** in the upper-right corner.

## Import session and refresh tokens inside Minecraft

Open **Multiplayer → Accounts → Session Token Importer**, next to **Session Token
Switcher**. Paste multiple lines, click **Open TXT**, or drop one UTF-8 `.txt` file
onto the importer. Click **Validate & Import** to check the batch. File loading
alone does not start authentication. No separate Token-refresher app is required.

Supported inputs are Minecraft session JWTs, Microsoft refresh tokens starting
with `M.C` (or containing `MsaArtifacts`), `username:token` lines, and pipe-separated
`MCTOKEN:` / `REFRESHTOKEN:` fields. Blank lines, `#` comments and `===` separators
are ignored. A usable refresh field takes priority over a session field; supplied
names and passwords are discarded. Limits: 1 MiB per input/file and 1,000 entries.

Session tokens are checked against Minecraft's profile service. Refresh tokens
run the Microsoft → Xbox → XSTS → Minecraft flow and verify the resulting profile.
Only service-verified usernames and UUIDs enter the account store. Expired session
tokens need a usable refresh token or a new sign-in; they cannot renew themselves.
Imports do not change the active session. Switch using the saved account's
**Login** button when ready.

The importer shows per-line results and counts, runs off the render thread, and
supports cancellation. Refresh-account attempts are spaced at least four seconds
apart; rate limiting stops the batch for a later retry. Duplicate UUIDs retain
their stable account ID while receiving the latest verified session and rotated
refresh credential. Input clears when processing begins or the importer closes.

The embedded Java adapter independently implements the protocol/configuration
from [Token-refresher commit fa96d074](https://github.com/TheApple05/Token-refresher/tree/fa96d07434d50eb451cbc5609d45d9787fc8ce48).
Its HTTP endpoints are fixed, redirects are disabled, response sizes/time are
bounded, and raw credentials/server error bodies are excluded from results and
logs. Imports use the existing encrypted NBT storage and fail if encryption is
unavailable; legacy plaintext-format records remain readable. See
[TOKEN-REFRESHER-PROVENANCE.json](TOKEN-REFRESHER-PROVENANCE.json).

Use the installable JAR, rather than the sources or QA JAR. Keep one version of
the account manager in the instance. The bundled `waybackauthlib` dependency is
already included.

## Color theme

The account manager uses the same indigo theme as the four other Minecraft
ports: deep blue `#1A3BD1` primary actions and selected tabs, `#06070B` background,
`#101114` panels and idle buttons, black fields, `#293C94` default outlines,
`#526FFF` focus rings and active usernames, and `#8181D9` hover outlines.
Headings/button text use `#FCFCFC`; placeholders and empty states use `#C8C9D1`.
Shared tokens live in `AccountPalette.java`. Primary fills retain their exact
color on hover. See [the design review](design-qa.md) for current screenshot
and pixel evidence and the earlier theme history.

## Scope and behavior

This ports the uploaded **account manager**. It retains the account
screen, account categories, offline account generation, checking, saved accounts,
renaming, skin previews, notifications, and launcher-session restoration. The
uploaded JAR's full-client modules and macros are inactive; they are not enabled
or included in this backport. Shared full-client configuration/command callbacks
and the unrelated Meteor proxy importer were decoupled from account management.
Meteor account import is retained.

Accounts use the original `autism-accounts.nbt` path in the Minecraft instance.
Settings use `config/autism/config.json`, preserving unrelated existing root keys.
Windows credentials retain the DPAPI storage format; the original AES fallback
is retained on other platforms. Encrypted Windows records remain tied to the
same Windows user and machine, as in the original build.
New writes require encryption; the old plaintext fallback is no longer written.

## Build

Set `JAVA_HOME` to a JDK 21 or newer, then run:

```powershell
.\gradlew.bat build
python tools\verify_distribution.py
```

The installable JAR and the recovered-source JAR are written to `build/libs`.
This repository includes the wrapper and bundled auth library needed to rebuild.
Minecraft 1.21.11 uses Mojang mappings during development and Fabric intermediary
names in the installable JAR.

## Validation

The exact installable JAR was launched with development mode disabled in a fresh
Minecraft 1.21.11 / Fabric 0.20.1 / Java 21.0.13 instance. Forty-seven runtime checks
passed, including the existing switcher, importer tab navigation, TXT loading,
clipboard paste, malformed input rejection, cancellation, resize, duplicate
credential rotation, encrypted reload, and preserving temporarily unavailable
accounts during Clear Expired. Actual game screenshots and the result are
included in the distribution.

`gradlew build` also runs 161 deterministic checks: 113 authentication checks,
39 parser/import lifecycle checks, and 9 encryption policy checks. These use
synthetic credentials and fake HTTP responses; no real account is required.

The packaged Java 21 test also checks Windows DPAPI encryption/decryption with
synthetic data. The artifact verifier checks metadata, mixins, nested libraries,
Java 21 bytecode, license, and absence of QA classes.

Microsoft OAuth, real session tokens, TheAltening, real saved-account migration,
multiplayer authentication, other mods, and Linux/macOS were **not tested**.
Authentication paths were recovered from the supplied build; successful offline
tests do not establish those external service results. Startup authentication
warnings in the QA log concern the synthetic test user, which has no valid token.

To repeat the isolated client test (it uses only fake accounts and a new directory
under `build/qa`):

```powershell
.\gradlew.bat build remapQaJar downloadAssets
python tools\qa\production_smoke.py --java-home C:\path\to\java21
```

`DpapiCompatibility.java` supplies a synthetic fixture for checking the old and
new native encryption bridges. The QA mod is packaged separately and is never
included in the installable artifact.

## Source provenance

Original commit: `SOURCE-PROVENANCE.json`. Original binary SHA-256:
`4a52fb89455cf32e3122fecd6323f25130bf1ce00f19d4298454a02023221af8`.

Source was recovered with **Vineflower 1.12.0**, then edited for 1.21.11. The
maintained implementation is `src/main/java`; it does not require decompilation
to build. `tools/recovered-source-selection.json` records the recovered classes.
`tools/recover_sources.py` is a recovery aid, not a build step. To recreate the
unmodified recovery cache, run Vineflower against the original JAR with output
directory `.tools/recovered`.

The main adaptations are `GuiGraphics`/screen rendering, `PlayerModel` previews,
Minecraft's pre-friends session-service layout, Java 21 DPAPI through Minecraft's
bundled JNA libraries, and repaired generic types lost during decompilation.
The original binary is retained unchanged at the repository root.
