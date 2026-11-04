# Curryfarts Account Manager — Minecraft 1.16.5 Fabric

Native Minecraft 1.16.5 port of the supplied Curryfarts account manager, retaining
Melonik's attribution, original license, account data format, and bowl palette.
This is an independent port, not a metadata-only version override.

## Install

- Minecraft **1.16.5** with **Java 8**.
- Fabric Loader **0.20.1**.
- The full official **Fabric API 0.42.0+1.16** release JAR.
- Install `curryfarts-5.0-port.1+1.16.5-fabric.jar` in the instance's `mods` folder.
- Open **Multiplayer → Accounts**.

Use the complete Fabric API download from
[FabricMC's release](https://github.com/FabricMC/fabric-api/releases/tag/0.42.0%2B1.16).
The old Maven API artifact is only a small dependency aggregator and cannot be
installed alone.

## Preserved behavior

The native GUI exposes Cracked/offline, Microsoft, session-token, and TheAltening
categories; add/login; original-launcher restoration; saved-account rename/delete;
bulk account checking; expired/clear-list actions with a clear confirmation;
search and type filters; token copy; and draggable 3D player-skin previews.
The preview starts at a front three-quarter angle. Resolved skin texture metadata
selects the classic or slim-arm model; the UUID-derived default is used only while
the skin is unresolved.

The generator accepts random names or pasted name lists with spaces, commas,
semicolons, and line breaks. `name:password` input uses the name portion, matching
the original parser. Password generation and a shared-password mode are retained.
The legacy GUI is adapted to the 1.16.5 screen/render APIs, so its layout is not
pixel-identical to the 1.21.11 interface. No inactive full-client modules, macros,
proxy system, or server password automation are enabled.

Session switching updates the old `User`, session service, skin manager, cached
profile properties, social service, and social manager on the client thread.
Launcher restoration restores the original identity and original service objects.
Real Microsoft/session accounts use authlib's social-interaction checks rather
than the offline-account service.

## Saved data and credentials

The port uses the original **uncompressed `autism-accounts.nbt`** file in the game
directory. It preserves `id`, `type`, `username`, `uuid`, `encToken`,
`encSessionToken`, `encPassword`, `encLabel`/`label`, and `sessionExpiresAt`.
Windows DPAPI flag-1 and the original AES-GCM flag-2 record layouts are retained;
new secrets are never written as plaintext. The fallback key path remains
`config/autism/autism-seal.key`. Windows DPAPI records still require the same
Windows user and machine. Read/decryption failures preserve the existing file.
Saves use a temporary file, `.bak`, and an atomic replacement when supported.

Meteor account import and the original `autism-meteor-import.nbt` marker are
supported. Source files are preserved, repeated imports are deduplicated, and
unrelated marker fields are retained. Generator settings are read from the old
configuration and written to `config/autism/generator-1.16.5.json`, leaving other
version-specific settings untouched.

## Build and validation

Building uses a JDK **21 or newer** for Gradle/Loom; the resulting mod bytecode is
Java 8. The tested build toolchain was JDK 25, Gradle 9.8.2, and Fabric Loom 1.19.3.

```powershell
.\gradlew.bat build
.\gradlew.bat remapQaJar downloadAssets
python tools\qa\production_smoke.py --java-home C:\path\to\jdk8
```

The packaged-game harness starts a fresh instance under `build/qa`, supplies only
synthetic accounts, verifies the exact distribution SHA-256 and production loader
environment, and writes screenshots, runtime inputs, and a verdict. The separate
QA mod is not part of the distributable. Java 8 launches use a direct argument
list because Java 8 does not support Java 9+ argument files. Loader library
versions override matching vanilla dependencies to avoid duplicate ASM versions.
Focused synthetic callback checks also verify that resolved classic/slim metadata
overrides the default model. They do not establish live custom-skin service results.

See `validation/` for the final runtime verdict and captured UI. The synthetic
upstream NBT compatibility check separately verified all identity/credential/expiry
fields through read → save → reload. Two-way DPAPI interoperability was verified
between the supplied original 26.2 binary on Java 25 and this port on Java 8.

## Validation limits

Real Microsoft OAuth, real session tokens, TheAltening service authentication,
joining real multiplayer servers, other mods, Linux, and macOS have **not** been
tested. Those network/authentication flows are ported, not established as working
against live accounts. The synthetic game identity causes expected vanilla
Realms/profile lookup warnings. Windows narrator startup availability is separate
from the mod and was not an accessibility validation gate.
