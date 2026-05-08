# AccountSwap

<div align="center">

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62b047?style=flat-square)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-Loader-dbb543?style=flat-square)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21-f89820?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/dreamyfx/accountswap/main.yml?branch=main&style=flat-square)](https://github.com/dreamyfx/accountswap/actions)

**Modern Minecraft account manager. Press P.**

</div>

---

## Features

- **Full Microsoft auth** — browser-based OAuth, no Azure app needed, no passwords ever stored
- **Offline/cracked accounts** — enter a username, done
- **Multi-account** — unlimited accounts, switch instantly in-game
- **Full session injection** — sets `User`, `UserApiService`, `PlayerSocialManager`, `ProfileKeyPairManager`, `ReportingContext` on switch
- **Encrypted storage** — AES-256-GCM, key stored locally
- **Async skin loading** — 8×8 head icons in the list, local cache
- **Vanilla-feeling UI** — dark panels, smooth open/close spring animation, auto-rotating 3D player model, scrollable list, right-click context menu

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for MC 1.21.11
2. Drop the jar into `.minecraft/mods/`
3. Launch → press **P**

---

## Auth flow

**Microsoft:** click *Add Microsoft Account* → your browser opens Microsoft's sign-in page → sign in → browser redirects back to the local callback server → tokens are exchanged, profile is fetched, game ownership is verified. No passwords, no Azure registration required.

**Offline:** type a username → offline UUID is derived → done. Works on `online-mode=false` servers and singleplayer.

---

## Build from source

```bash
git clone https://github.com/dreamyfx/accountswap
cd accountswap
gradle wrapper
./gradlew build
```

Jar at `build/libs/accountswap-1.0.0.jar`.

> Check [fabricmc.net/develop](https://fabricmc.net/develop) for the correct `fabric_version` and `loader_version` for 1.21.11 and update `gradle.properties` if needed.

---

## File locations

```
.minecraft/config/accountswap/
├── accounts.json    ← encrypted account list
├── .key             ← AES key (do not delete or share)
└── skin_cache/      ← cached skin PNGs
```

---

## License

MIT — [LICENSE](LICENSE)
