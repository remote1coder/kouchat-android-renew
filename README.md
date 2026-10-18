# KouChat Android — `renew` fork

KouChat is a serverless, peer-to-peer LAN chat application for Android. It needs no
server and no internet connection: peers discover each other with UDP multicast on the
local network, then exchange chat messages, private messages and files directly.

This repository is the **`renew` fork** of
[KouChat Android v1.5.0](https://github.com/blurpy/kouchat-android), originally written
by Christian Ihle. The fork adds end-to-end encryption, selectable network transports,
dark mode, chat font settings and several reliability fixes.

| | |
|---|---|
| Application ID | `net.usikkert.kouchat.android` |
| Version | 1.5.0 (versionCode 17) |
| Minimum Android | 5.0 / API 21 |
| Compile / target SDK | 36 |
| Language | Java (188 files) + Kotlin (4 files) |
| License | GNU LGPL v3 — see `COPYING` |

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Build from source](#build-from-source)
- [Install and run](#install-and-run)
- [Project structure](#project-structure)
- [Architecture](#architecture)
- [Settings reference](#settings-reference)
- [Tests](#tests)
- [Troubleshooting](#troubleshooting)
- [Repository and remotes](#repository-and-remotes)
- [License and credits](#license-and-credits)

## Features

- **End-to-end encryption** — RSA-4096 identity keys, RSA-OAEP wrapped AES-256-GCM
  session keys, SHA-256 fingerprints for the trust dialog. Trusted keys survive
  restarts.
- **Four network modes** — Multicast (default), Broadcast, Unicast (plaintext mesh) and
  P2P (encrypted mesh). The active mode and connection state are shown in a status bar
  above the chat input.
- **Automatic unicast channels** — on networks that drop multicast/broadcast packets,
  every message is also sent directly to each peer and duplicates are removed on the
  receiving side (all new devices, trusted devices only, or off).
- **Long messages** — up to 4 MB. Messages that do not fit in one UDP datagram are sent
  over TCP.
- **Reliable delivery** — duplicate messages are dropped using a 3-second, 1000-entry
  hash cache; lost TCP connections are re-established.
- **File transfer** — 60-second timeout for unanswered requests, so one ignored request
  cannot block the transfer list. Files can be auto-accepted from encrypted peers
  (images only by default, or all files).
- **Inline images** — images pasted in a desktop-client chat are shown in the message
  list and can be opened full-screen.
- **Dark / night mode** — automatic by time of day, follow system, light or dark.
- **Chat font and size** — eight font families and sizes from 10 to 40 sp, applied
  immediately, including already displayed history.
- **Share sheet support** — other apps can send files to KouChat through
  `ACTION_SEND` (see `SendFileController` in `AndroidManifest.xml`).

## Requirements

| Component | Version | Notes |
|---|---|---|
| JDK | **17** | Required to run Android Gradle Plugin 8.5.0. Java 8 will not work. |
| Gradle | 8.7 | Supplied by the wrapper (`gradlew` / `gradlew.bat`); do not install separately. |
| Android SDK Platform | **36** | `compileSdk 36` and `targetSdk 36`. |
| Android SDK Build-Tools | 34.0.0 or newer | AGP 8.5.0 defaults to 34.0.0. |
| Android Studio | Optional | Any recent version with SDK 36 is fine. |

The first build downloads AGP, Kotlin 1.9.24 and the AndroidX dependencies from
`google()` and `mavenCentral()`, so an internet connection is required once.

## Build from source

### 1. Get the code

```bash
git clone git@github.com:remote1coder/kouchat-android-renew.git
cd kouchat-android-renew
```

### 2. Point Gradle at your Android SDK

`local.properties` is intentionally **not** tracked. Create it in the repository root:

```properties
sdk.dir=/path/to/Android/Sdk
```

Typical locations:

- Windows: `C:/Users/<you>/AppData/Local/Android/Sdk`
- macOS: `/Users/<you>/Library/Android/sdk`
- Linux: `/home/<you>/Android/Sdk`

Forward slashes work on every platform and avoid `properties` escaping problems.
Android Studio creates this file automatically when you open the project.

### 3. Use JDK 17

`gradle.properties` contains:

```properties
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

That path is the JBR bundled with Android Studio on the machine where the fork was
built. **If it does not exist on your machine, change it to your JDK 17 home or delete
the line** and set `JAVA_HOME` instead. A wrong value fails immediately with
`Java home supplied is invalid`.

Check the toolchain:

```bash
java -version        # must report 17.x
```

### 4. Build

```bash
# macOS / Linux / Git Bash
./gradlew clean test lint assembleDebug

# Windows cmd / PowerShell
gradlew.bat clean test lint assembleDebug
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Other useful tasks:

| Task | Purpose |
|---|---|
| `./gradlew assembleDebug` | Build the debug APK only. |
| `./gradlew test` | Run JUnit unit tests (JVM). |
| `./gradlew lint` | Run Android Lint. `abortOnError` is `false`, so findings are reported but do not fail the build. |
| `./gradlew connectedAndroidTest` | Run Robotium UI tests on a connected device or emulator. |
| `./gradlew clean` | Delete build outputs. |

### 5. Release build and signing

`app/build.gradle` does **not** define a `signingConfigs` block. As a result:

```bash
./gradlew assembleRelease
```

produces an **unsigned** APK at
`app/build/outputs/apk/release/app-release-unsigned.apk`.

The `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` entries in `gradle.properties` are placeholders
(`mock.keystore` / `replace-me`) and are **not referenced by any build script**. Do not
put real passwords in that tracked file.

To produce a signed release APK, either:

- use **Android Studio → Build → Generate Signed Bundle / APK**, or
- sign the unsigned APK manually:

  ```bash
  "$ANDROID_HOME/build-tools/34.0.0/apksigner" sign \
    --ks my-release.jks \
    --out app-release-signed.apk \
    app/build/outputs/apk/release/app-release-unsigned.apk
  ```

or add a `signingConfigs` block that reads the values from environment variables, for
example:

```groovy
android {
    signingConfigs {
        release {
            storeFile file(System.getenv("KOUCHAT_STORE_FILE"))
            storePassword System.getenv("KOUCHAT_STORE_PASSWORD")
            keyAlias System.getenv("KOUCHAT_KEY_ALIAS")
            keyPassword System.getenv("KOUCHAT_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

### AGP / compileSdk note

AGP 8.5.0 was tested up to `compileSdk 34`. Building with `compileSdk 36` still works,
but Gradle prints:

```
WARNING: We recommend using a newer Android Gradle plugin to use compileSdk = 36
```

To silence it, add this line to `gradle.properties`:

```properties
android.suppressUnsupportedCompileSdk=36
```

## Install and run

1. Enable **Developer options → USB debugging** on the device and connect it.
2. Install the APK:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Start KouChat and set a nickname in **Settings**.
4. Connect at least two devices to the **same Wi-Fi / LAN**. Mobile data alone is not
   enough for peer discovery.
5. Peers appear in the user list automatically. Tap a user for a private chat, or use
   the menu to send a file.
6. Open **Settings → Network mode** and try **Broadcast** or **Unicast** if devices do
   not see each other — many access points block multicast or enable client isolation.

On Android 13 and newer, allow notifications when prompted; the foreground-service
notification is part of normal operation.

## Project structure

```
kouchat-android/
├── app/
│   ├── build.gradle                  # app module: compileSdk 36, minSdk 21, dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # activities, foreground service, FileProvider
│       │   ├── java/net/usikkert/kouchat/
│       │   │   ├── android/          # Android layer (UI, service, settings, theme, ...)
│       │   │   └── ...               # shared core (net, crypto, message, misc, ...)
│       │   └── res/                  # layouts, preference XML, strings/arrays, night theme
│       ├── test/                     # 73 Java files: JUnit + Mockito unit tests
│       └── androidTest/              # 36 Java files: Robotium UI tests
├── build.gradle                      # AGP 8.5.0, Kotlin 1.9.24
├── gradle/wrapper/                   # Gradle 8.7 wrapper
├── gradle.properties                 # AndroidX, JVM args, unused release placeholders
├── settings.gradle                   # includes :app
├── README.md
├── CHANGES                           # upstream changelog
├── COPYING                           # GNU LGPL v3
├── INSTALL                           # upstream build notes (signing part is outdated)
├── annotation-config/                # IntelliJ/Android Studio inspection config
├── checkstyle.xml                    # code style rules
├── icons/                            # source SVG icons
└── old/                              # archived build files
```

### Package map

| Package | Responsibility |
|---|---|
| `net.usikkert.kouchat.android.app` | `KouChatApp` — applies the theme and creates notification channels before any activity starts. |
| `net.usikkert.kouchat.android.controller` | Activities: main chat, private chat, settings, send file, receive file, trust key. |
| `net.usikkert.kouchat.android.service` | `ChatService` (foreground service, `specialUse` type) and its binder. Holds the live chat session. |
| `net.usikkert.kouchat.android.chatwindow` | `AndroidUserInterface` — the bridge between the shared core and the Android UI; also private chat window, message styling and inline images. |
| `net.usikkert.kouchat.android.settings` | `AndroidSettings` (extends the core `Settings`) plus `SharedPreferences` loader/saver. |
| `net.usikkert.kouchat.android.notification` | Notification channels and message/file/trust notifications. |
| `net.usikkert.kouchat.android.theme` | `AndroidThemeHelper` — light/dark/automatic theme selection. |
| `net.usikkert.kouchat.android.userlist`, `.component`, `.smiley`, `.filetransfer` | UI adapters, custom preference widgets, smileys and file UI helpers. |
| `net.usikkert.kouchat.net`, `.net.tcp` | UDP multicast/broadcast/unicast, private-chat UDP, TCP fallback, file transfer, message parsing and deduplication. |
| `net.usikkert.kouchat.crypto` | Identity keys, `CryptoManager`, `EncryptedChannel`, `TrustedKeys`, trust prompt/callback. |
| `net.usikkert.kouchat.misc` | `Controller`, `MessageController`, `CommandParser`, user list, topic, chat logger, error handling. |
| `net.usikkert.kouchat.message`, `.settings`, `.event`, `.ui`, `.util`, `.jmx`, `.autocomplete` | Messages, settings model, listeners, UI interfaces, helpers, JMX and nickname autocomplete. |

## Architecture

### Startup and layers

1. `KouChatApp.onCreate()` applies the stored theme and creates the notification
   channels (API 26+).
2. `MainChatController` (the launcher activity) starts and binds `ChatService`, a
   foreground service so the network session is not killed while the app is in the
   background.
3. `ChatService.onCreate()` builds:
   - `AndroidSettings` loaded from `SharedPreferences`;
   - `AndroidUserInterface` (implements the core `UserInterface`, `ChatWindow` and
     `TrustPrompt` interfaces);
   - `NotificationService` and `LockHandler` (Wi-Fi multicast lock and optional wake
     lock).
4. `AndroidUserInterface` creates the core `Controller`, `MessageController` and
   `CommandParser`. `Controller` owns the user list, settings, `CryptoManager` and
   `NetworkService`.
5. `NetworkService` wires the transports:
   - `MessageReceiver` / `MessageSender` — main chat over UDP multicast or subnet
     broadcast;
   - `UDPSender` / `UDPReceiver` — private chat over UDP;
   - `TCPNetworkService` (`TCPServer`, `TCPClient`, `TCPConnector`, `TCPUserClient`) —
     TCP fallback for messages too large for one datagram, private chat and file
     transfer;
   - `MessageDeduplicator` — drops duplicate copies that arrive over both paths.
6. UI events flow from the activities to `AndroidUserInterface`, then to the core
   controller; core callbacks come back through the same interface and are posted to
   the Android main thread.

### Network transports

| Mode (stored value) | Main chat | Private chat | Encryption |
|---|---|---|---|
| Multicast (`multicast`) — default | UDP multicast group `224.168.5.200:40556` | UDP unicast, TCP fallback | Group chat plaintext; private chat encrypted after trust |
| Broadcast (`lanbroadcast`) | UDP subnet broadcast | UDP unicast, TCP fallback | Same as multicast |
| Unicast (`unicast`) | Plain UDP unicast to every known peer; discovery still multicast | UDP/TCP unicast | None (explicitly unencrypted) |
| P2P (`p2p`) | Encrypted unicast mesh to trusted peers; discovery still multicast | Encrypted unicast | AES-256-GCM on established channels |

**Automatic unicast channels** (`multicast_unicast_mode`) add a redundant unicast copy
of every message in Multicast/Broadcast mode. `all` covers every new device, `trusted`
only devices with a trusted key, `off` disables the feature. Duplicate copies are
removed by `MessageDeduplicator` (3-second window, 1000 hashes).

### Ports

| Port | Protocol | Purpose |
|---|---|---|
| 40556 | UDP | Main chat, multicast group `224.168.5.200` |
| 50050 | UDP | Temporary/discovery traffic, group `224.168.5.250` |
| 40656 | UDP | Private chat (starting port; `+1` while busy) |
| 40656 | TCP | Chat/private chat over TCP (starting port; `+1` while busy) |
| 40756 | TCP | Incoming file transfers (starting port; `+1` while busy) |

Port constants live in `app/src/main/java/net/usikkert/kouchat/Constants.java`.

### End-to-end encryption

`CryptoUtils` documents the primitives used:

- **RSA-4096** long-term identity keys (`Identity`, persisted);
- **RSA-OAEP (SHA-256, MGF1)** to wrap a per-conversation **AES-256** session key;
- **SHA256withRSA** signature over the key exchange to detect tampering/MITM;
- **AES-256-GCM** with a 12-byte IV and a 128-bit tag for message confidentiality;
- **SHA-256** fingerprint of the public key shown in the trust dialog.

`TrustedKeys` stores accepted fingerprints, so a returning peer with the same key is
trusted without a new prompt. `TrustKeyController` displays the trust dialog; the
decision is delivered back through `TrustPrompt`/`TrustCallback` even if the activity is
recreated (rotation or system dark-mode change).

### Message path and size limits

- `MESSAGE_MAX_BYTES_CHAT = 4 * 1024 * 1024` (4 MB).
- Messages larger than one UDP datagram are written to the TCP channel.
- `TCPConnectionHandler` retries peers that lost all TCP connections, so a single failed
  connection attempt does not permanently remove a peer.
- File transfer requests are auto-rejected after
  `FILE_TRANSFER_ANSWER_TIMEOUT = 60000` ms.

## Settings reference

All preferences are defined in `app/src/main/res/xml/settings.xml` (and
`xml-v26/settings.xml`) and stored through `AndroidSettings`.

| Setting | Key | Values | Default |
|---|---|---|---|
| Nickname | `nick_name` | text | device/user name |
| Own message color | `own_color` | ARGB integer | `-15987646` |
| System message color | `sys_color` | ARGB integer | `-16759040` |
| Keep screen awake | `wake_lock` | on/off | off |
| Network interface | `network_interface` | interface list | automatic |
| Network mode | `network_mode` | `multicast`, `lanbroadcast`, `unicast`, `p2p` | `multicast` |
| Theme mode | `theme_mode` | `auto`, `system`, `light`, `dark` | `light` |
| Dark theme start hour | `dark_start_hour` | `00`–`23` | `18` (used by `auto`) |
| Dark theme end hour | `dark_end_hour` | `00`–`23` | `9` (used by `auto`) |
| Chat font | `font_family` | `""` (default), `sans-serif`, `sans-serif-light`, `sans-serif-condensed`, `sans-serif-thin`, `serif`, `serif-monospace`, `monospace` | default |
| Chat font size | `font_size` | `0` (default), `10`–`40` sp | default |
| Auto-accept files | `auto_accept_files` | `off`, `images`, `all` | `images` |
| Automatic unicast channels | `multicast_unicast_mode` | `all`, `trusted`, `off` | `all` |
| Automatic encrypted handshake | `auto_encrypt_unicast` | on/off | on |
| Notification light | `notification_light` | on/off | on |
| Notification sound | `notification_sound` | on/off | on |
| Notification vibration | `notification_vibration` | on/off | on |

## Tests

```bash
# JVM unit tests (JUnit 4 + Mockito), including the crypto suite
./gradlew test

# Instrumented UI tests (Robotium) — requires a device or emulator
./gradlew connectedAndroidTest
```

Unit tests live in `app/src/test/java`; instrumented tests and their assets live in
`app/src/androidTest/`.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `SDK location not found` | Create `local.properties` with `sdk.dir=...` (see step 2). |
| `Java home supplied is invalid` | `org.gradle.java.home` in `gradle.properties` points to a missing directory. Fix or remove it and set `JAVA_HOME` to JDK 17. |
| `Unsupported class file major version` / AGP errors | Gradle is running on Java 8 or 11. Use JDK 17. |
| Peers never appear | Devices must be on the same subnet. Disable AP/client isolation; allow multicast. Try Broadcast or Unicast mode. |
| Messages arrive in one direction only | The AP drops multicast. Enable **Automatic unicast channels** (`all` or `trusted`) or switch to Unicast/P2P. |
| File transfer hangs | Requests auto-reject after 60 s. Check the TCP port range (40756+) in the firewall. |
| Release APK is unsigned | Expected — no `signingConfigs` is configured. See “Release build and signing”. |
| `compileSdk 36` warning | Optional: add `android.suppressUnsupportedCompileSdk=36` to `gradle.properties`. |
| Notifications missing on Android 13+ | Grant the `POST_NOTIFICATIONS` permission. |

## Repository and remotes

```
origin    git@github.com:remote1coder/kouchat-android-renew.git   # push here
upstream  https://github.com/blurpy/kouchat-android               # read-only
```

`upstream` is the original project and is configured as **fetch-only**:
`git remote set-url --push upstream no_push`. Never push fork commits to
`blurpy/kouchat-android`; send changes to `origin` instead.

Documentation policy: only `README.md` (and an optional `readme.md`) is tracked.
`*.md` is ignored by `.gitignore`, so local notes such as `changes.md` stay out of the
repository.

## License and credits

Copyright 2006–2019 Christian Ihle and contributors.

KouChat is free software, distributed under the **GNU Lesser General Public License
v3**. See `COPYING` for the full text.

- Project home: <https://www.kouchat.net/>
- Original repository: <https://github.com/blurpy/kouchat-android>
- Smileys in `icons/smileys` come from a GNOME icon theme; see the README in that
  folder.
- `notification_sound.ogg` is derived from `error.wav` by Nuno Povoa (KDE Oxygen
  theme), licensed under the GNU LGPL.
