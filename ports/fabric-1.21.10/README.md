# Curryfarts for Minecraft 1.21.10 â€” Fabric

Port of the indigo-themed account manager originally supplied through the former
`TheApple05/Curryfarts` repository. Published in
[TheApple05/CurryAccountManger-Appa](https://github.com/TheApple05/CurryAccountManger-Appa), preserving
Melonik's authorship and the supplied GPL license.

## Install

Use Minecraft **1.21.10**, **Java 21+**, **Fabric Loader 0.20.1+**, and
**Fabric API 0.138.4+1.21.10**. Put
`curryfarts-5.0-port.2+1.21.10-fabric.jar` in that instance's `mods` folder.
Open **Multiplayer â†’ Accounts**. Install only one account-manager build in an
instance; this port retains the original `autism` mod ID.

## Build and validation

With `JAVA_HOME` pointing to a JDK 21 or newer, run from this directory:

```powershell
.\gradlew.bat build
python tools\verify_distribution.py
```

For the isolated packaged-client test, use a Java 21 runtime:

```powershell
.\gradlew.bat build remapQaJar downloadAssets
python tools\qa\production_smoke.py --java-home C:\path\to\java21
```

The exact remapped JAR passed **17 production-client checks** with development
mode disabled on Java 21.0.13, including the actual multiplayer button, account
screen, field cursor positioning, fake offline account login, rename, encrypted
NBT disk reload, stable IDs, launcher-session restoration, generator/confirmation
popups, deletion, and native-window resize. It exited with code 0. Actual game
screenshots and sanitized results are under `validation/`.

The tested artifact hash is recorded in `validation/runtime.json` and the release checksum file.

The adaptations cover resource/mapping names, model/render-type locations,
the pre-1.21.11 button renderer, and texture filtering/addressing before the
sampler-cache API. The production launcher resolves overlapping vanilla/loader
libraries by Maven artifact and classifier so Fabric's ASM wins consistently.

The full account-manager feature paths and indigo palette are retained. Real
Microsoft OAuth, session-token/Altening authentication, multiplayer joining,
other mods, and non-Windows platforms remain unverified. Synthetic-account
authentication/profile lookup warnings in the isolated log are expected.

Saved accounts and encryption retain the upstream `autism-accounts.nbt` and
`config/autism` formats. The test uses a fresh instance and never opens an
existing user account store. The QA mod is separate from the installable JAR.

API migration reference: [NeoForged's 1.21.10 â†’ 1.21.11 primer](https://docs.neoforged.net/primer/docs/1.21.11/)
(used in reverse, with the target game's mapped classes verified locally).

## Indigo menu palette

Background `#06070B`, panels and idle buttons `#101114`, fields `#000000`, primary actions and selected tabs `#1A3BD1`, default outlines `#293C94`, focus rings and the active username `#526FFF`, hover outlines `#8181D9`, text `#FCFCFC`, and placeholders/empty states `#C8C9D1`. Hover changes the outline while primary fills retain their exact color.
