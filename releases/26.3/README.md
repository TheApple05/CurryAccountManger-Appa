# AUTISM Account Manager for Minecraft 26.3

Unofficial port of Melonik's AUTISM Account Manager 5.0, retaining the importer,
theme, encrypted account storage, and original GPL-3.0-only attribution.

## Install

Use Minecraft **26.3**, **Java 25**, **Fabric Loader 0.20.1 or newer**,
and **Fabric API 0.161.0+26.3**. Put `autism-account-manager-5.0-port.1+26.3.jar` and the matching
Fabric API JAR in the instance's `mods` folder. Keep one account-manager version
installed. The bundled waybackauthlib is included. Open **Multiplayer → Accounts**.
The sources JAR is for development; the QA mod is excluded from this release.

## Validation

The exact installable JAR passed **61 packaged-client checks** with development
mode disabled, using synthetic accounts in an isolated Minecraft 26.3 instance.
The process exited with code zero. Checks cover account UI and platform input,
offline switching/restoration, encrypted persistence, importer navigation, TXT
loading, clipboard paste, cancellation, duplicate credential rotation, and
preserving accounts during temporary authentication failures.
The native Open TXT file chooser was not exercised in this run.

`validation.json` records artifact metadata and SHA-256 verification.
`runtime-result.json` and `production-verdict.json` contain the checks and verdict.
Selected in-game screenshots are in `screenshots`; `SHA256SUMS.txt` covers this bundle.

A separate live validation of one supplied refresh-token entry returned `INVALID`.
See `live-validation.json`; successful live sign-in remains unverified.

Microsoft OAuth, direct real session-token import, TheAltening, real saved-account migration,
multiplayer authentication, other mods, and Linux/macOS were not tested. Synthetic
tests do not establish external-service results. Startup authentication warnings
concern the synthetic QA user. See the repository README for importer inputs,
reproducible build commands, retained behavior, and source provenance.
