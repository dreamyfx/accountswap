# AccountSwap

<div align="center">

![AccountSwap Banner](https://img.shields.io/badge/AccountSwap-1.0.0-5b8dd9?style=for-the-badge&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==)

**A modern, Minecraft-native account manager with real Microsoft authentication.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62b047?style=flat-square&logo=minecraft)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-Loader-dbb543?style=flat-square)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21-f89820?style=flat-square&logo=openjdk)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/dreamyfx/accountswap/main.yml?branch=main&style=flat-square&label=Build)](https://github.com/dreamyfx/accountswap/actions)

*Press **P** in-game to open*

</div>

---

## What is AccountSwap?

AccountSwap is a Fabric mod for Minecraft 1.21.11 that lets you manage and switch between multiple Minecraft accounts without ever leaving the game. It supports full **Microsoft / Xbox Live / Minecraft authentication** using the same official auth flow as Prism Launcher, as well as **offline/cracked accounts** for testing or LAN play.

The UI is designed to feel like a first-party Minecraft screen — vanilla layout, vanilla font, vanilla scaling — but with a cleaner and darker visual style: semi-transparent panels, subtle rounded corners, smooth open/close animations, and a live 3D player model that auto-rotates.

Everything is **100% client-side**. No servers, no telemetry, no webhooks, no third-party account services.

---

## Features

### Authentication
- **Full Microsoft OAuth device code flow** — opens your browser automatically, no credentials ever touch the mod
- **Xbox Live + XSTS token exchange** — standard chain used by every major open-source launcher
- **Minecraft Services authentication** — official `api.minecraftservices.com` endpoints only
- **Token refresh** — silently re-authenticates with a stored refresh token, no re-login needed
- **Offline / Cracked accounts** — enter any username, get a deterministic offline UUID

### Account Management
- **Multi-account support** — store and switch between as many accounts as you want
- **Instant session switching** — injects a new `Session` into `MinecraftClient` at runtime via mixin
- **Encrypted token storage** — access and refresh tokens are AES-256-GCM encrypted before being written to disk
- **Persistent account list** — survives restarts, stored in `config/accountswap/accounts.json`
- **Search & filter** — live search filters the account list as you type

### UI
- **Vanilla-native feel** — Minecraft font, Minecraft scaling, Minecraft layout philosophy
- **Dark semi-transparent panels** — subtle glassmorphism without going overboard
- **Smooth open/close animation** — easeOutBack spring curve
- **Auto-rotating 3D player model** — real entity renderer, not a static image
- **Scrollable account list** — smooth inertia scrolling, scroll bar appears when needed
- **Right-click context menu** — Login / Refresh Token / Remove per account
- **Player head icons** — async-loaded from Mojang's skin API, cached locally
- **Add Account screen** with device code display, copy-to-clipboard, and auto browser open
- **Config screen** for Azure client ID setup

### Skin System
- Fetches full skin PNG from Mojang's session API (no auth required)
- Extracts the face + hat layers for the account list head icons
- Caches skins locally in `config/accountswap/skin_cache/`
- Non-blocking — loads on a background thread, falls back to a placeholder until ready
- Invalidation support — re-downloads on demand

---

## Installation

### Requirements
| Requirement | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.16.14 |
| Fabric API | matching 1.21.11 |
| Java | 21 |

### Steps
1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11
2. Download the latest `accountswap-X.X.X.jar` from [Releases](https://github.com/dreamyfx/accountswap/releases)
3. Drop the jar into your `.minecraft/mods/` folder
4. Launch the game

---

## First-Time Setup (Microsoft Accounts)

AccountSwap uses the **OAuth 2.0 Device Code flow**. This means you authenticate through Microsoft's website — the mod never sees your password.

To use Microsoft account sign-in, you need a **free Azure Active Directory application** (takes about 3 minutes):

### Register an Azure App (free)

1. Go to [portal.azure.com](https://portal.azure.com) and sign in with any Microsoft account
2. Navigate to **Azure Active Directory → App registrations → New registration**
3. Fill in:
   - **Name:** anything (e.g. `AccountSwap`)
   - **Supported account types:** Personal Microsoft accounts only
   - **Redirect URI:** `Mobile and desktop applications` → `https://login.microsoftonline.com/common/oauth2/nativeclient`
4. Click **Register**
5. Copy your **Application (client) ID** (a UUID like `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)
6. Go to **Authentication** → under **Advanced settings** → enable **Allow public client flows** → Save
7. Go to **API permissions** → confirm `openid` and `profile` are listed (they are by default)

No secrets, no certificates, no paid tier needed. The free tier is sufficient.

### Enter the Client ID in-game

1. Launch Minecraft with the mod installed
2. Press **P** to open AccountSwap
3. Click **Add Account → Add Microsoft Account**
4. If no client ID is configured, the **Settings screen** opens automatically
5. Paste your Application (client) ID and click **Save**
6. Click **Add Microsoft Account** again — your browser will open to `https://microsoft.com/devicelogin`
7. Enter the code shown in the mod, sign in, and you're done

Your client ID is saved to `config/accountswap/config.json` and never needs to be entered again.

---

## Usage

### Opening the mod
Press **P** (default keybind — rebindable in Controls settings) from anywhere in the game.

### Account list (left panel)
- **Click** an account to select it
- **Search bar** at the top filters by username
- **Scroll** with the mouse wheel when you have more accounts than fit
- **Right-click** any account for a context menu (Login, Refresh Token, Remove)
- Active account is marked with a green dot on the right edge

### Right panel
- Shows a **3D auto-rotating player model** (uses the current in-game player entity)
- Displays username, account type badge, and short UUID
- Shows **Active** / **Ready** / **Expired** status

### Buttons
| Button | Action |
|---|---|
| **Login** | Switch the active Minecraft session to this account |
| **Remove** | Delete the account from the list |
| **Refresh** | Re-authenticate with stored refresh token (Microsoft only) |
| **+** (top right of list) | Open the Add Account screen |

### Switching accounts
Clicking **Login** injects a new `Session` object into `MinecraftClient`. The game's displayed username and UUID update instantly. You will need to **reconnect to any servers** you are currently on for the server to recognize the new account.

---

## Offline / Cracked Accounts

Offline accounts don't require any authentication. They use a deterministic UUID derived from the username (same algorithm as vanilla offline mode: `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`).

- Works on servers with `online-mode=false`
- Works for singleplayer
- Will not authenticate on online-mode servers

---

## File Locations

All files are stored under your Minecraft config directory:

```
.minecraft/
└── config/
    └── accountswap/
        ├── accounts.json     ← encrypted account list
        ├── config.json       ← Azure client ID
        ├── .key              ← AES-256 encryption key (auto-generated, keep private)
        └── skin_cache/
            ├── <uuid>.png    ← cached skin textures
            └── ...
```

> **Important:** The `.key` file is your encryption key. Do not share it. Do not delete it unless you are also deleting `accounts.json` — without the key the tokens cannot be decrypted.

---

## Security

- **Tokens are encrypted at rest** using AES-256-GCM with a randomly generated key stored locally
- **No network calls are made** except to official Microsoft / Xbox / Mojang endpoints and Mojang's session server for skin fetching
- **No telemetry, no analytics, no webhook logging** of any kind
- **No token exfiltration** — this is open source, you can verify every network call in [`HttpUtil.java`](src/main/java/dev/dreamyfx/accountswap/util/HttpUtil.java)
- **Refresh tokens are stored encrypted** — if someone gets your `accounts.json` without the `.key`, they cannot read the tokens
- The mod does not store your Microsoft password (it never sees it — authentication happens in your browser)

---

## Building from Source

### Prerequisites
- JDK 21 ([Adoptium](https://adoptium.net))
- Git

### Steps

```bash
git clone https://github.com/dreamyfx/accountswap.git
cd accountswap
gradle wrapper          # generates gradlew if not present
./gradlew build
```

The built jar will be at `build/libs/accountswap-1.0.0.jar`.

> **Note:** Before building, verify `gradle.properties` has the correct Fabric API and yarn mappings version for Minecraft 1.21.11. Check [fabricmc.net/develop](https://fabricmc.net/develop) for the latest versions.

### Development environment

```bash
./gradlew genSources     # generate Minecraft sources for IDE navigation
./gradlew runClient      # launch Minecraft with the mod for testing
```

---

## Architecture

```
src/main/java/dev/dreamyfx/accountswap/
│
├── AccountSwapMod.java           Main entry, keybind registration
│
├── auth/
│   ├── MicrosoftAuthFlow.java    Device code flow, token polling, refresh
│   ├── XboxAuthService.java      Xbox Live + XSTS token exchange
│   ├── MinecraftAuthService.java Minecraft login + profile fetch
│   ├── AuthResult.java           Result carrier (success/failure + tokens)
│   └── DeviceCodeResponse.java   Device code POJO
│
├── account/
│   ├── Account.java              Account model (tokens, UUID, type, active state)
│   ├── AccountType.java          MICROSOFT | OFFLINE
│   └── AccountManager.java       List management + session injection
│
├── storage/
│   ├── AccountStorage.java       JSON read/write (Gson)
│   └── EncryptionUtil.java       AES-256-GCM key management + encrypt/decrypt
│
├── ui/
│   ├── AccountSwapScreen.java    Main screen (layout, rendering, input)
│   ├── AddAccountScreen.java     Add Microsoft / offline account flow
│   ├── ConfigScreen.java         Azure client ID configuration
│   ├── ModernButton.java         Hover-animated dark Minecraft button
│   └── ContextMenu.java          Right-click popup menu
│
├── skin/
│   └── SkinManager.java          Async skin fetch, head crop, texture registration
│
├── animation/
│   └── AnimationUtil.java        Easing functions, lerp helpers
│
├── mixin/
│   └── MinecraftClientAccessor   @Mutable @Accessor for Session injection
│
└── util/
    └── HttpUtil.java             java.net.http wrapper, no external dependencies
```

---

## Authentication Flow (Technical)

```
User clicks "Add Microsoft Account"
        │
        ▼
POST https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode
  body: client_id=YOUR_ID&scope=XboxLive.signin%20offline_access
        │
        ▼
← { device_code, user_code, verification_uri, expires_in, interval }
        │
        ▼
Show user_code + open verification_uri in browser
        │
        ▼
Poll https://login.microsoftonline.com/consumers/oauth2/v2.0/token
  (every `interval` seconds until authorized or expired)
        │
        ▼
← { access_token (MSA), refresh_token, expires_in }
        │
        ▼
POST https://user.auth.xboxlive.com/user/authenticate
  body: { Properties: { AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com",
          RpsTicket: "d=<MSA_TOKEN>" }, RelyingParty: "http://auth.xboxlive.com" }
        │
        ▼
← { Token (XBL), DisplayClaims.xui[0].uhs }
        │
        ▼
POST https://xsts.auth.xboxlive.com/xsts/authorize
  body: { Properties: { SandboxId: "RETAIL", UserTokens: [XBL_TOKEN] },
          RelyingParty: "rp://api.minecraftservices.com/" }
        │
        ▼
← { Token (XSTS) }
        │
        ▼
POST https://api.minecraftservices.com/authentication/login_with_xbox
  body: { identityToken: "XBL3.0 x=<UHS>;<XSTS_TOKEN>" }
        │
        ▼
← { access_token (Minecraft) }
        │
        ▼
GET https://api.minecraftservices.com/minecraft/profile
  header: Authorization: Bearer <MC_TOKEN>
        │
        ▼
← { id (UUID), name (username) }
        │
        ▼
Account added, tokens encrypted and saved to disk
```

---

## FAQ

**Q: Do I need to buy Minecraft to use this?**
A: For Microsoft accounts, yes — the Minecraft profile endpoint will return an error if the account doesn't own the game. For offline accounts, no purchase is needed.

**Q: Is this safe to use?**
A: The source code is fully open. No data leaves your machine except to official Microsoft/Mojang endpoints. Tokens are encrypted on disk.

**Q: Will this get me banned?**
A: AccountSwap only uses official authentication endpoints and does not modify game packets or automate gameplay. However, use at your own risk on servers with strict anti-cheat.

**Q: The 3D model doesn't show in the main menu.**
A: The 3D model renderer requires an active player entity (i.e. you must be loaded into a world). In the main menu a placeholder is shown instead. This is a Minecraft engine limitation.

**Q: Token refresh failed.**
A: Microsoft refresh tokens expire after 90 days of inactivity. Re-add the account via the full device code flow.

**Q: I lost my `.key` file.**
A: Delete `accounts.json` and re-add your accounts. The tokens cannot be recovered without the key.

**Q: Can I use this on servers?**
A: You can switch accounts freely. To play on an online-mode server with the new account, disconnect and reconnect — the server authenticates on connection.

---

## Contributing

Pull requests are welcome. For significant changes, please open an issue first.

1. Fork the repo
2. Create a branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Run `./gradlew build` to verify it compiles
5. Submit a PR

---

## License

MIT — see [LICENSE](LICENSE) for full text.

---

## Credits

- **dreamyfx** — mod author
- **FabricMC** — modding toolchain
- **Mojang** — Minecraft
- Auth flow reference: [wiki.vg/Microsoft_Authentication_Scheme](https://wiki.vg/Microsoft_Authentication_Scheme), Prism Launcher

---

<div align="center">
<sub>Made with care. Vanilla feeling, modern power.</sub>
</div>
