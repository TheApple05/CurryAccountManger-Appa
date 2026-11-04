# Curryfarts Account Manager for Minecraft 1.12.2 — Forge

Native Java 8 port of Melonik's uploaded account manager, retaining the supplied
GPL license, artwork, saved-account format, and the requested indigo menu palette.

## Install

Use **Minecraft 1.12.2**, **Forge 14.23.5.2847**, and **Java 8**. Download
`curryfarts-5.0-port.2+1.12.2-forge.jar` from this version's GitHub release
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

The tested build used **JDK 25** for RetroFuturaGradle 2.0.6 / Gradle 9.2.0, with a separate
**JDK 8** compiler/runtime selected through `JAVA8_HOME`. Install both JDKs, then
run in this directory (replace the example paths):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk25'
$env:JAVA8_HOME = 'C:\path\to\jdk8'
.\gradlew.bat --console=plain build reobfQaJar exportQaClasspath
python tools\production_smoke.py
```

The Windows native harness uses a fresh game directory and synthetic records,
loads the exact reobfuscated installable JAR, exercises the actual account UI,
and requires clean process exit. The QA JAR is separate and must not be installed
for normal play. The final JAR passed **18 runtime checks** and **7 synthetic compatibility checks**, with exit code 0. Results and actual game screenshots are in `validation/`.

The additional `CoreCompatibility` check reads an upstream-schema fixture,
verifies all nine fields and encrypted save/reload, and checks Meteor import
without touching a real account file. Create a synthetic fixture with the root
`tools/qa/create_compat_fixture.py` script, then run
`python ../../tools/qa/legacy_compatibility.py --project .` after exporting the
QA classpath. Java 8 is the tested client runtime (8u504 on Windows).

Real Microsoft OAuth, session-token/TheAltening authentication, multiplayer
joining, other mods, and non-Windows systems remain unverified. This is an
unofficial port; successful offline tests do not prove those live integrations.
