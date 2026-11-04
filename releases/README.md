# Downloads by update

Each new release contains all seven supported Minecraft versions. Choose the JAR for
your Minecraft version **and** loader; install only one matching account-manager
JAR in an instance.

- [5.0.4 — Minecraft 26.2 and 26.3](https://github.com/TheApple05/CurryAccountManger-Appa/releases/tag/v5.0.4):
  Forge 1.8.9 / 1.12.2 and Fabric 1.16.5 / 1.21.10 / 1.21.11 / 26.2 / 26.3.
  Adds the two Java 25 ports while retaining the five tested 5.0.3 JARs unchanged.
  Includes sources and packaged-client validation for the new ports.
- [5.0.3 — In-game Session Token Importer](https://github.com/TheApple05/CurryAccountManger-Appa/releases/tag/v5.0.3):
  Forge 1.8.9 / 1.12.2 and Fabric 1.16.5 / 1.21.10 / 1.21.11. Includes the indigo
  theme, Token-refresher-compatible validation/refresh, TXT/paste importing,
  encrypted storage, duplicate updates, and cancellation.
- [5.0.2 — Indigo menu update](https://github.com/TheApple05/CurryAccountManger-Appa/releases/tag/v5.0.2):
  the five earlier indigo builds, consolidated without changing their binary bytes.
- [5.0.1 — Initial supported-version bundle](https://github.com/TheApple05/CurryAccountManger-Appa/releases/tag/v5.0.1):
  the five baseline builds, consolidated without changing their binary bytes.

Every release includes `SHA256SUMS.txt` and version-specific validation results.
Historical bundles also include original source tags and artifact origins.
Original source tags remain in Git. Download all supported Minecraft builds for
an update from its single grouped release page.

New updates publish automatically when a commit changing `release.json` is pushed
to `main`. Follow [the release workflow](../docs/releases.md) to bump the update
version, retain the full matrix, and supply tested artifacts, digests and notes.
One update creates one `vVERSION` release; do not publish per-Minecraft releases.

The loader/runtime requirements remain version-specific:

- **1.8.9:** Forge 11.15.1.2318, Java 8.
- **1.12.2:** Forge 14.23.5.2847, Java 8.
- **1.16.5:** Fabric Loader 0.20.1, full Fabric API 0.42.0+1.16, Java 8.
- **1.21.10:** Fabric Loader 0.20.1, Fabric API 0.138.4+1.21.10, Java 21.
- **1.21.11:** Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Java 21.
- **26.2:** Fabric Loader 0.20.1, Fabric API 0.153.0+26.2, Java 25. [Port details](26.2/README.md).
- **26.3:** Fabric Loader 0.20.1, Fabric API 0.161.0+26.3, Java 25. [Port details](26.3/README.md).
