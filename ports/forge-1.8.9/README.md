# Curryfarts Account Manager for Minecraft 1.8.9 — Forge

Native Java 8 port of Melonik's uploaded account manager, retaining the supplied
GPL license, artwork, saved-account format, and the requested indigo menu palette.

## Install

Use **Minecraft 1.8.9**, **Forge 11.15.1.2318**, and **Java 8**. Download
`curryfarts-5.0-port.3+1.8.9-forge.jar` from this version's GitHub release
and place it in that instance's `mods` folder. Open **Multiplayer → Accounts**.
Fabric API is not required. Keep one account-manager build in an instance.

The screens include offline, Microsoft, session-token, and TheAltening categories;
login and launcher-session restoration; rename, delete, search, filters, bulk
checking, clear confirmation, token copying, a 3D skin preview, and the random or
clipboard-list generator with optional passwords. The controls temporarily use a
readable GUI scale and restore your original setting when you leave. A window
of at least 640 × 360 pixels is recommended. Generator selections last for the
current dialog. Full-client modules and macros are outside this account-manager port.

## Menu palette

The menu uses `#06070B` backgrounds, `#101114` panels and idle buttons, and
black fields. Primary actions and selected tabs use `#1A3BD1`; outlines use
`#293C94`, focus rings and the active username use `#526FFF`, and hovered
outlines use `#8181D9`. Headings/button text are `#FCFCFC`; placeholders and
empty states are `#C8C9D1`. Destructive actions keep their labels and confirmation
with their existing labels and confirmation. Hovering changes the outline only.

## Saved data

Accounts use the upstream uncompressed `autism-accounts.nbt` schema and stable IDs.
Tokens, Microsoft refresh credentials, and generated passwords remain encrypted.
Windows DPAPI records require the same Windows user and machine. The original
AES-GCM fallback and `config/autism/autism-seal.key` layout are retained.
Unreadable credentials stop loading instead of overwriting the source; saves
use a temporary file and `.bak` copy. Meteor accounts import from
`meteor-client/accounts.nbt` with the original import marker and deduplication.
The Meteor source file is left intact.

## Build and packaged-client validation

The tested build used **JDK 17** for Essential Loom 0.10.0.5 / Gradle 7.5, with a separate
**JDK 8** compiler/runtime selected through `JAVA8_HOME`. Install both JDKs, then
run in this directory (replace the example paths):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk17'
$env:JAVA8_HOME = 'C:\path\to\jdk8'
.\gradlew.bat --console=plain build remapQaJar exportRuntime exportQaClasspath
python tools\production_smoke.py
```

The Windows native harness uses a fresh game directory and synthetic records,
loads the exact reobfuscated installable JAR, exercises the actual account UI,
and requires clean process exit. The QA JAR is separate and must not be installed
for normal play. The final JAR passed **29 runtime checks** and **7 synthetic compatibility checks**, with exit code 0. Results and actual game screenshots are in `validation/`.

The additional `CoreCompatibility` check reads an upstream-schema fixture,
verifies all nine fields and encrypted save/reload, and checks Meteor import
without touching a real account file. Create a synthetic fixture with the root
`tools/qa/create_compat_fixture.py` script, then run
`python ../../tools/qa/legacy_compatibility.py --project .` after exporting the
QA classpath. Java 8 is the tested client runtime (8u504 on Windows).

Real Microsoft OAuth, session-token/TheAltening authentication, multiplayer
joining, other mods, and non-Windows systems remain unverified. This is an
unofficial port; successful offline tests do not prove those live integrations.

## In-game token importer (v5.0.3)

The **Session Token Importer** tab sits beside **Session Token Switcher**.
Paste a multiline batch or choose a UTF-8 `.txt` file, then click **Validate & Import**.
Input previews hide credential text. Tokens are checked in the background; per-line
results and cancellation stay inside Minecraft. Only verified account identities
are saved. Repeated UUIDs keep their stable IDs and receive fresh session data;
Microsoft refresh-token rotations are retained in the encrypted account store.
Network failures remain retryable, and rate limits stop the batch.

This embeds an independent Java 8 implementation of the public authentication flow
used by [Token-refresher](https://github.com/TheApple05/Token-refresher) at commit
`fa96d07434d50eb451cbc5609d45d9787fc8ce48`. It uses the same approved Live/Xbox/Minecraft
endpoints and Live client, blocks redirects, bounds response size/time, and never
logs credentials or remote error bodies. Raw Minecraft access tokens are validated;
only Microsoft refresh tokens can obtain fresh sessions. No external helper runs.

All current version JARs are grouped in [release v5.0.3](https://github.com/TheApple05/CurryAccountManger-Appa/releases/tag/v5.0.3).
Run `tokenImportTest` for deterministic synthetic protocol/parser/cancellation checks.
The separate QA mod also exercises the importer UI, duplicate refresh updates,
encrypted disk reload and cancellation with synthetic data. Real account login,
OAuth consent and real-server authentication remain unverified.
