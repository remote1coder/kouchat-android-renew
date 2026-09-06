# KouChat-Android Change Log

> Summary of all changes to `kouchat-android` (approach + files involved). Shared code is synced from the desktop project via `kouchat-renew/copy-to-kouchat-android.sh`; see `kouchat-renew/changes.md` for those changes. Only Android-specific work is listed here.

## 1. End-to-End Encryption (Android wiring)

**Approach**: Wired the shared crypto layer into the Android UI — when a peer's public key needs a trust decision, a notification is posted and the TrustKey activity is launched; the decision is passed back to the core. The private chat window shows encryption state and plaintext-fallback notices.

**Files**:
- New `android/controller/TrustKeyController.java`, `android/notification/TrustKeyNotificationService.java`
- `android/chatwindow/AndroidUserInterface.java` (implements TrustPrompt, encrypted private-chat sending)
- `android/chatwindow/AndroidPrivateChatWindow.java`, `android/chatwindow/MessageStylerWithHistory.java`
- `app/src/main/AndroidManifest.xml` (foreground service permission, new activity registration)

## 2. Inline Image Display and Other Earlier Features

**Approach**: Images sent/received in chats are displayed inline in the chat text flow (the receiving side of the desktop paste-screenshot feature).

**Files**:
- New `android/chatwindow/InlineImageViewer.java`
- `android/controller/MainChatController.java`, `android/controller/PrivateChatController.java` (integration)
- `res/menu/private_chat_menu.xml` (new) and related resources

## 3. Four Network Modes + Status Bar

**Approach**: The settings screen "Network mode" preference now offers 4 options (Multicast/Broadcast/Unicast/P2P, matching the desktop, persisted in SharedPreferences). A status bar TextView was added above the chat input, showing the current mode and connection state, refreshed on mode changes and network events. Fixed the bug where the setting was invisible on API 26+ devices (missing entries in the v26 resource variant).

**Files**:
- `res/values/arrays.xml` (new 4-entry array), `res/values/strings.xml`
- `res/xml/settings.xml`, `res/xml-v26/settings.xml` (preferences added to both — **the v26 variant overrides the default resources on Android 8+, so new preferences must be added to both**)
- `android/controller/SettingsFragment.java` (4-value summary + legacy value handling)
- `android/chatwindow/AndroidUserInterface.java` (`formatNetworkStatus` + refresh after mode switch)
- `android/controller/MainChatController.java` (status bar TextView + `updateNetworkStatus`)
- `res/layout/main_chat.xml` (new `mainChatStatusBar`)

## 4. Build Fixes

**Approach**: Made the Gradle build and unit tests work on the modern toolchain.

**Files**:
- `gradle.properties`: `org.gradle.java.home` pointing at the Android Studio JBR (Java 21), in-process Kotlin compiler, **`android.enableJetifier=false`** (Jetifier fails on Mockito 5's byte-buddy)
- `app/build.gradle`: Mockito upgraded to 5.19.0, removed PowerMock/system-rules, JUnit 4.13.2
- `gradle/wrapper/gradle-wrapper.properties`, root `build.gradle`
- Test compatibility: `junit/ExpectedSystemOut.java`, new `junit/RestoreSystemProperties.java`

## 5. Shared Code (script-synced, not hand-edited)

The following directories are mirror copies of `kouchat-renew` (via `copy-to-kouchat-android.sh`); the source of truth is the desktop project:

- `app/src/main/java/net/usikkert/kouchat/crypto/` (crypto core)
- `settings/` (NetworkMode enum, etc.)
- `net/` (NetworkService mode dispatch, MessageSender subnet broadcast, TCPClient new framing, MessageDeduplicator rewrite, TCPConnectionHandler race fix and reconnection)
- `misc/` (Controller mode/long-message/crypto wiring), `util/`, `message/`, `event/`
- `app/src/main/resources/messages/core.properties`
- `app/src/test/java/net/usikkert/kouchat/` (corresponding unit tests)

**Note**: Shared packages must not use Swing / `java.util.Base64` / `java.nio.file` (minSdk 21 constraint). Always change `kouchat-renew` first, then run the sync script.
