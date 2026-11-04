# Minecraft 1.21.11 backport

[Download the installable JAR](autism-account-manager-5.0-backport.4+1.21.11.jar)
or choose Minecraft 1.21.11 in the [all-version 5.0.3 release](https://github.com/TheApple05/CurryAccountManger-Appa/releases/tag/v5.0.3).

Requires Minecraft 1.21.11, Fabric Loader 0.20.1 or newer, Java 21 or newer, and
Fabric API 0.141.6+1.21.11. Install the JAR in the instance's `mods` folder and
open **Multiplayer → Accounts → Session Token Importer**, beside **Session Token
Switcher**. Paste tokens or load a UTF-8 TXT, then click **Validate & Import**.
Session tokens are validated; Microsoft refresh tokens create fresh sessions.
The importer saves verified accounts to encrypted storage, updates duplicate
credentials, shows progress/results, and supports cancellation.

The JAR is the exact artifact tested in a fresh production-mode instance on
Java 21.0.13. Forty-seven runtime checks passed; see
[validation-importer.json](validation-importer.json). The build also passed 161
deterministic authentication, import, and encryption policy checks.
The runtime test checks encrypted storage using synthetic data. Microsoft OAuth, real session tokens, TheAltening,
multiplayer authentication, other mods, and Linux/macOS remain untested.

The **5.0-backport.4** build retains the same exact indigo palette as the other
four ports: `#06070B` menu, `#101114` panels/idle buttons, `#000000` fields,
`#1A3BD1` primary actions/selected tabs, `#293C94` default outlines, `#526FFF`
focus rings/active usernames, `#8181D9` hover outlines, `#FCFCFC` text, and
`#C8C9D1` placeholders. Hovering changes the outline while the primary fill stays
deep blue. Current screenshot color evidence is in
[palette-colors-importer.json](palette-colors-importer.json). The earlier binaries
and validation.json are retained as historical artifacts.

Source and build instructions are at the repository root. Original authorship
belongs to Melonik; this is an unofficial account-manager backport.

![In-game Session Token Importer](10-session-token-importer.png)

![Importer results](11-import-validation-results.png)
