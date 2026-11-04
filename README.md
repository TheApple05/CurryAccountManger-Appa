# AUTISM Account Manager for Minecraft 26.2 and 26.3

Unofficial ports of Melonik's **AUTISM Account Manager 5.0**, supplied in
[TheApple05/Autism-Client](https://github.com/TheApple05/Autism-Client).
The original repository contains a compiled JAR; this project reconstructs the
account manager's source and maintains its importer and theme on Minecraft 26.x.
Original authorship, artwork, authentication code, and GPL license are retained.

## Install

- **26.2:** [Download the JAR](releases/26.2/autism-account-manager-5.0-port.1+26.2.jar), with [Fabric API 0.153.0+26.2](https://modrinth.com/mod/fabric-api/versions?g=26.2).
- **26.3:** [Download the JAR](releases/26.3/autism-account-manager-5.0-port.1+26.3.jar), with [Fabric API 0.161.0+26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3).

Use **Java 25** and **Fabric Loader 0.20.1 or newer**. Put the matching account
manager and Fabric API JARs in that Minecraft instance's `mods` folder. Keep only
one account-manager version installed. The bundled `waybackauthlib` dependency
is included; sources and QA JARs are not installable releases.
Open **Multiplayer**, then click **Accounts** in the upper-right corner.
Each release folder contains its checksums and validation evidence.

The [archived 1.21.11 backport](releases/1.21.11) remains available, including its
[JAR](releases/1.21.11/autism-account-manager-5.0-backport.3+1.21.11.jar) and original
validation. Current source builds target 26.2 and 26.3.

## Import session and refresh tokens inside Minecraft

Open **Multiplayer → Accounts → Session Token Importer**, next to **Session Token
Switcher**. Paste multiple lines, click **Open TXT**, or drop one UTF-8 `.txt`
file. Click **Validate & Import** to check the batch. Loading a file alone does
not authenticate. No separate Token-refresher app is required.

Supported inputs are Minecraft session JWTs, Microsoft refresh tokens starting
with `M.C` (or containing `MsaArtifacts`), `username:token` lines, and pipe-separated
`MCTOKEN:` / `REFRESHTOKEN:` fields. Blank lines, `#` comments and `===` separators
are ignored. A usable refresh field takes priority over a session field; supplied
names and passwords are discarded. Limits: 1 MiB per input/file and 1,000 entries.

Session tokens are checked against Minecraft's profile service. Refresh tokens
run the Microsoft → Xbox → XSTS → Minecraft flow and verify the resulting profile.
Only service-verified usernames and UUIDs enter the account store. Expired session
tokens need a usable refresh token or a new sign-in; they cannot renew themselves.
Imports leave the active session unchanged. Use a saved account's **Login** button
to switch when ready.

The importer shows per-line results and counts, runs off the render thread, and
supports cancellation. Refresh attempts are spaced at least four seconds apart;
rate limiting stops the batch for a later retry. Duplicate UUIDs retain their
stable account ID while receiving the latest verified session and rotated refresh
credential. Input clears when processing begins or the importer closes.

The embedded Java adapter independently implements the protocol/configuration
from [Token-refresher commit fa96d074](https://github.com/TheApple05/Token-refresher/tree/fa96d07434d50eb451cbc5609d45d9787fc8ce48).
HTTP endpoints are fixed, redirects are disabled, response sizes/time are bounded,
and raw credentials/server error bodies are excluded from results and logs.
Imports require encrypted NBT storage; legacy plaintext records remain readable.
See [TOKEN-REFRESHER-PROVENANCE.json](TOKEN-REFRESHER-PROVENANCE.json).

## Theme, scope, and storage

The bowl palette retains deep navy surfaces, electric blue primary actions, teal
controls, golden current-account/focus markers, herb green success states, spice
orange destructive actions, and spoon brown scroll accents. Shared tokens live in
`AccountPalette.java`; see [the Product Design review](design-qa.md) for the palette
and its prior screenshot/contrast review.

This ports the uploaded **account manager**: account categories, offline account
generation, checking, saved accounts, renaming, skin previews, notifications,
launcher-session restoration, and Meteor account import. Full-client modules,
macros, unrelated configuration/command callbacks, and Meteor proxy importing are
not included.

Accounts retain `autism-accounts.nbt` in the Minecraft instance. Settings retain
`config/autism/config.json`, preserving unrelated root keys. Windows credentials
use DPAPI and remain tied to the same Windows user and machine; the original AES
fallback remains on other platforms. New writes require encryption; the old
plaintext fallback is no longer written.

## Build and repeat validation

Set `JAVA_HOME` to a **JDK 25**, then run each target separately:

```powershell
.\gradlew.bat '-PtargetMinecraft=26.2' build qaJar downloadAssets
python tools\verify_distribution.py --minecraft 26.2
python tools\qa\production_smoke.py --minecraft 26.2 --java-home C:\path\to\jdk25

.\gradlew.bat '-PtargetMinecraft=26.3' build qaJar downloadAssets
python tools\verify_distribution.py --minecraft 26.3
python tools\qa\production_smoke.py --minecraft 26.3 --java-home C:\path\to\jdk25
```

The default target is **26.3**. Profiles live in `versions/<version>.properties`;
artifacts are written to `build/<version>/libs`. Both Minecraft versions ship
unobfuscated, so release JARs use official class names without remapping.
Shared source lives in `src/main/java`; target adapters in `src/26.2/java` and
`src/26.3/java` handle session services, GPU textures, native file picking, and
opening the sign-in browser.

`build` runs 161 deterministic checks: 113 authentication checks, 39 parser/import
lifecycle checks, and 9 encryption-policy checks, using synthetic credentials and
fake HTTP responses. The verifier checks metadata, mixins, nested dependencies,
Java 25 bytecode, official names, license, and absence of QA classes.
The separate QA mod runs the exact release JAR with development mode disabled,
using fake accounts in a fresh `build/<version>/qa` directory, and checks its hash.
It is excluded from the installable artifact.

Both ports passed all 161 build checks and **61 packaged-client checks each** on
Java 25.0.5 with Fabric Loader 0.20.1. Each exact installable JAR ran in production
mode and exited with code zero. Tests cover platform mouse/text input, generator
and Escape handling, skin previews, offline session switching and restoration,
encrypted NBT persistence, TXT drag/drop, clipboard paste, import cancellation,
credential rotation, and temporary authentication failures. Release folders
contain the checksums, structured results, source JARs, and in-game screenshots.
The native Open TXT selection path remains unverified: desktop capture timed out
during its optional UI test. TXT loading through drag/drop passed on both ports.

Two refresh-token entries from an explicitly supplied list were tested separately,
one against each exact release artifact. Both returned `INVALID`; no additional
entries were attempted. Sanitized evidence is in each release's
`live-validation.json`. This does not establish successful live sign-in.

Microsoft OAuth, direct real session-token import, TheAltening, real saved-account migration,
multiplayer authentication, other mods, and Linux/macOS have not been tested for
these ports. Synthetic authentication tests do not establish external-service
results. Startup authentication warnings in QA concern its synthetic test user.

## Source provenance

See [SOURCE-PROVENANCE.json](SOURCE-PROVENANCE.json) for the original commit. The
unchanged original binary remains at the repository root, with SHA-256
`4a52fb89455cf32e3122fecd6323f25130bf1ce00f19d4298454a02023221af8`.
Source was recovered with **Vineflower 1.12.0**, adapted for 1.21.11, then ported to
26.2/26.3. Building uses maintained source directly and requires no decompilation.
`tools/recovered-source-selection.json` records recovered classes;
`tools/recover_sources.py` is a recovery aid, not a build step.
