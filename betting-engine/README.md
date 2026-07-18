# BETTING ENGINE 🌐

A full-featured Chromium-based browser for Android built with WebView (Blink/V8 engine), implementing all 18 feature categories from the Chromium Browser Features Checklist.

---

## ✅ Feature Checklist

| # | Category | Status |
|---|----------|--------|
| 1 | Browser Engine (Chromium/Blink/V8, WebGL, WebRTC, Service Workers, IndexedDB) | ✅ |
| 2 | Browser UI (Tabs, Incognito, Downloads, History, Bookmarks, Settings, Find in Page, Reader Mode, Fullscreen, Desktop Mode, Dark Mode, Zoom, Pull-to-Refresh, Swipe Navigation) | ✅ |
| 3 | Extension System (Install, Enable/Disable, Remove, Update, Import CRX, Import ZIP, Import Unpacked) | ✅ |
| 4 | Chrome Extension APIs (chrome.runtime, storage, tabs, action, scripting, permissions, webNavigation, webRequest, cookies, notifications, contextMenus, downloads, alarms, i18n) | ✅ |
| 5 | Manifest Support (MV2, MV3, icons, permissions, content scripts, background service workers, commands, host permissions) | ✅ |
| 6 | Content Script Injection (detect page load, inject JS, inject CSS, floating panel) | ✅ |
| 7 | Background Runtime (execution, timers, messaging, storage, API requests, event listeners) | ✅ |
| 8 | Messaging (Content ↔ Background, Popup ↔ Background, Options ↔ Background, Browser ↔ Extension) | ✅ |
| 9 | Extension Storage (chrome.storage.local, chrome.storage.sync, IndexedDB, Local Storage) | ✅ |
| 10 | Permissions (Active Tab, Storage, Downloads, Notifications, Clipboard, Host permissions, Cookies, Tabs) | ✅ |
| 11 | Security (Sandboxing, HTTPS, Certificate validation, Permission prompts, Site isolation) | ✅ |
| 12 | Downloads (Pause/Resume, Rename, Notifications, History) | ✅ |
| 13 | JavaScript (ES6+, WebAssembly, Service Workers, Workers, Shared Workers) | ✅ |
| 14 | Website Compatibility (HTML5, CSS3, JS, React, Angular, Vue, Canvas, SVG, WebGL) | ✅ |
| 15 | Performance (GPU acceleration, Hardware rendering, V8 optimization, Lazy loading, Multi-threading) | ✅ |
| 16 | Android Integration (Camera, Microphone, File picker, Notifications, Clipboard, Share, Storage access) | ✅ |
| 17 | Single Website / Kiosk (Auto-open URL, No address bar, No tabs, Full screen, Block external sites, Auto-reload on crash) | ✅ |
| 18 | Floating Extension Support (Content-script injection, DOM manipulation, JS/CSS injection, Background runtime, Messaging APIs, Storage APIs, Permission handling) | ✅ |

---

## 📦 Project Structure

```
betting-engine/
├── .github/
│   └── workflows/
│       └── build-apk.yml       ← GitHub Actions CI/CD
├── app/
│   └── src/main/
│       ├── java/com/auto/engine/
│       │   ├── MainActivity.kt
│       │   ├── browser/
│       │   │   ├── BrowserActivity.kt      ← Main browser UI
│       │   │   ├── BrowserWebViewClient.kt
│       │   │   ├── BrowserWebChromeClient.kt
│       │   │   └── TabsAdapter.kt
│       │   ├── tab/
│       │   │   ├── Tab.kt
│       │   │   └── TabManager.kt
│       │   ├── history/
│       │   │   ├── HistoryManager.kt       ← Room DB
│       │   │   └── HistoryActivity.kt
│       │   ├── bookmarks/
│       │   │   ├── BookmarkManager.kt      ← Room DB
│       │   │   └── BookmarksActivity.kt
│       │   ├── extensions/
│       │   │   ├── Extension.kt            ← Data models
│       │   │   ├── ExtensionManager.kt     ← Install/inject/manage
│       │   │   ├── ChromeApisBridge.kt     ← chrome.* APIs via JavascriptInterface
│       │   │   └── ExtensionsActivity.kt
│       │   ├── download/
│       │   │   ├── DownloadsActivity.kt
│       │   │   └── DownloadService.kt
│       │   ├── kiosk/
│       │   │   └── KioskActivity.kt        ← Single-site locked mode
│       │   └── settings/
│       │       └── SettingsActivity.kt
│       ├── res/                 ← Layouts, drawables, values
│       └── AndroidManifest.xml
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## 🚀 Build Instructions

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK 35

### Build Debug APK
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Build Release APK (unsigned)
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Build via Android Studio
1. Open the `betting-engine/` folder in Android Studio
2. Wait for Gradle sync
3. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**

---

## 🤖 GitHub Actions APK Build

Every push to `main` or `master` automatically:
1. Builds a **debug APK** and **unsigned release APK**
2. Uploads both as GitHub Actions **artifacts** (downloadable from the Actions tab)

### Creating a Signed Release
1. Push a version tag: `git tag v1.0.0 && git push origin v1.0.0`
2. GitHub Actions will build, sign (if keystore secrets are set), and create a **GitHub Release** with the APK attached.

### Setting up signing (optional)
Add these secrets to your GitHub repo → Settings → Secrets:
- `KEYSTORE_BASE64` — Base64-encoded `.jks` keystore file
- `KEY_ALIAS` — Key alias
- `KEYSTORE_PASSWORD` — Keystore password
- `KEY_PASSWORD` — Key password

Generate a keystore:
```bash
keytool -genkey -v -keystore betting-engine.jks -alias betting -keyalg RSA -keysize 2048 -validity 10000
base64 -i betting-engine.jks | pbcopy   # macOS — paste into KEYSTORE_BASE64 secret
```

---

## 🔧 Extension Installation

1. Open the browser → Menu → **Extensions**
2. Tap **Import ZIP** to install a Chrome extension from a `.zip` file
3. Tap **Import CRX** to install from a `.crx` file
4. Toggle extensions on/off or remove them from the Extensions screen

All Chrome Extension APIs are bridged via `ChromeApisBridge.kt`:
- `chrome.storage.local` / `chrome.storage.sync`
- `chrome.runtime`, `chrome.tabs`, `chrome.action`
- `chrome.scripting`, `chrome.permissions`
- `chrome.webNavigation`, `chrome.webRequest`
- `chrome.cookies`, `chrome.notifications`
- `chrome.contextMenus`, `chrome.downloads`
- `chrome.alarms`, `chrome.i18n`

---

## 🏖️ Kiosk Mode

1. Open browser → Menu → **Kiosk Mode**
2. Enter the URL to lock the browser to
3. The app goes fullscreen with no address bar, no tabs, no navigation UI
4. External site navigation is blocked
5. Auto-reloads on network errors or crashes
6. **Long-press the invisible exit button** (bottom-right corner) to exit

---

## 📋 Package Info

| Property | Value |
|----------|-------|
| Package Name | `com.auto.engine` |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |
| Language | Kotlin |
| Browser Engine | WebView (Chromium/Blink/V8) |
| Database | Room (SQLite) |
| Architecture | Single-module, Activity-based |

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.
