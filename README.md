# Curryfarts Account Manager ports

Download the matching installable JAR from the [GitHub releases](https://github.com/TheApple05/CurryAccountManger-Appa/releases).
Each Minecraft version has its own release and SHA-256 checksum:

- **1.8.9** — Forge 11.15.1.2318, Java 8. [Install/build/test details](ports/forge-1.8.9/README.md).
- **1.12.2** — Forge 14.23.5.2847, Java 8. [Install/build/test details](ports/forge-1.12.2/README.md).
- **1.16.5** — Fabric Loader 0.20.1, full Fabric API 0.42.0+1.16, Java 8. [Install/build/test details](ports/fabric-1.16.5/README.md).
- **1.21.10** — Fabric Loader 0.20.1, Fabric API 0.138.4+1.21.10, Java 21. [Install/build/test details](ports/fabric-1.21.10/README.md).

Put one matching account-manager JAR in the instance's `mods` folder, install the
listed loader and dependencies, then open **Multiplayer → Accounts**. The earlier **1.21.11** release is also available; use the versioned release
assets for these four ports. These ports retain Melonik's account-manager authorship,
GPL license, artwork, and account-manager functionality.

The four ports use the requested indigo menu palette: `#06070B` background,
`#101114` panels and idle buttons, black input fields, `#1A3BD1` primary actions
and selected tabs, `#293C94` outlines, `#526FFF` focus/active usernames,
`#8181D9` hover outlines, `#FCFCFC` text, and `#C8C9D1` placeholders.
The updated releases are **5.0-port.2**. The original 1.21.11 build below retains
its earlier theme.

The separate port projects live under `ports/`. Their `validation/` directories
contain screenshots and exact packaged-JAR test results. All four must pass
before `python tools/package_ports.py` collects the JARs and checksums. Build
JDKs differ from client Java versions; follow the instructions in each project.
Live Microsoft/session/TheAltening accounts, multiplayer servers, other mod
combinations, and non-Windows platforms have not been validated.

---

# AUTISM Account Manager for Minecraft 1.21.11

Unofficial backport of Melonik's **AUTISM Account Manager 5.0 for 26.2**, supplied in
[TheApple05/Autism-Client](https://github.com/TheApple05/Autism-Client).
The original repository contains a compiled JAR, so this project reconstructs the
account manager's source and adapts it to Minecraft 1.21.11. Original authorship,
artwork, authentication code, and the bundled GPL license are retained.

## Install

Download the [Minecraft 1.21.11 JAR](releases/1.21.11/autism-account-manager-5.0-backport.2+1.21.11.jar).
The [release folder](releases/1.21.11) includes SHA-256 checksums, runtime results,
and screenshots from the packaged client.

1. Use Minecraft **1.21.11**, **Fabric Loader 0.20.1 or newer**, and **Java 21 or newer**.
2. Install [Fabric API 0.141.6+1.21.11](https://modrinth.com/mod/fabric-api/versions?g=1.21.11).
3. Put `autism-account-manager-5.0-backport.2+1.21.11.jar` in that instance's `mods` folder.
4. Open **Multiplayer**, then click **Accounts** in the upper-right corner.

Use the installable JAR, rather than the sources or QA JAR. Keep one version of
the account manager in the instance. The bundled `waybackauthlib` dependency is
already included.

## Color theme

The account manager uses the supplied bowl palette: deep navy surfaces, electric
blue primary actions, teal controls, golden current-account/focus markers, herb
green success states, spice orange destructive actions, and spoon brown scroll
accents. Shared tokens live in `AccountPalette.java`. Text and icon colors use
readable neutral/tinted foregrounds. See [the Product Design review](design-qa.md)
for screenshots, contrast measurements, and the review history.

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
Minecraft 1.21.11 / Fabric 0.20.1 / Java 21.0.13 instance. Seventeen runtime checks
passed, including the injected button, opening and rendering the account screen,
adding/logging into a fake offline account through the UI, rename, disk reload
with a stable ID, deletion, resize, and original-session restoration. Actual
game screenshots and the result are included in the distribution.

Windows DPAPI encryption/decryption was also tested in both directions between
the unmodified original JAR on Java 25 and the backport on Java 21, using synthetic
data. The artifact verifier checks metadata, mixins, nested libraries, Java 21
bytecode, license, and absence of QA classes.

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
