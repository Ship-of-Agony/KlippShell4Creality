<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.shipofagony.klippshell4creality%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FShip-of-Agony%2FKlippShell4Creality%22%2C%22author%22%3A%22Ship-of-Agony%22%2C%22name%22%3A%22KlippShell4Creality%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22includeZips%5C%22%3Afalse%2C%5C%22zippedApkFilterRegEx%5C%22%3A%5C%22%5C%22%7D%22%2C%22overrideSource%22%3Anull%7D">
    <img 
      src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png?raw=true" 
      alt="Add to Obtainium" 
      width="182" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Target-Phone%20%7C%20Android%20TV-FF4500?style=for-the-badge&logo=android" alt="Target: Phone & Android TV" />
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img src="https://img.shields.io/badge/License-Apache_2.0-D22128?style=for-the-badge&logo=apache&logoColor=white" alt="License: Apache 2.0" />
  </a>
  <a href="https://github.com/Ship-of-Agony/KlippShell4Creality/releases">
    <img src="https://img.shields.io/github/downloads/Ship-of-Agony/KlippShell4Creality/total?style=for-the-badge&color=2ea44f&logo=github&logoColor=white" alt="Total Downloads" />
  </a>
</p>


# KlippShell 4 Creality

KlippShell 4 Creality brings your Klipper-powered 3D printers natively to Android devices – uncompromisingly optimized for smartphones, tablets, and Android TV boxes. Comfortably monitor and control your printing processes directly from your couch, without having to open a separate web interface on your PC.

## Camera Stream (Fluidd/Mainsail)

If the camera stream fails to load in web interfaces like Fluidd/Mainsail or external applications, you will likely need the community fix provided by **DnG-Crafts**.

* **Fix & Guide:** [DnG-Crafts/K2-Camera](https://github.com/DnG-Crafts/K2-Camera)

This fix injects the required parameters into Moonraker and provides an alternative stream path. Detailed installation steps via SSH and uninstallation scripts can be found directly in the linked repository.

---

## 🚀 Key Features & Highlights

### 📱 Home Screen Widget
* **Native Android Home Screen Widget Integration:** Engineered a responsive application widget utilizing remote view bindings to display real-time Klipper status, current nozzle/bed temperatures, and print job progress percentages instantly without opening the main application window.

### 📺 Tailored for Android TV & Tablets
* **Responsive Dual-Screen Layout:** Automatic side-by-side display in landscape mode (tablets/TV) featuring a persistent main menu on the left and dynamic sub-menus on the right (`layout-sw600dp-land`).
* **Optimized D-Pad Navigation:** Full support for TV remote controllers backed by intelligent focus routing and Material3 feedback. Touch targets have been fine-tuned to fit the TV experience perfectly.
* **Fluidd & Mainsail Escape-Hatch:** Prevents the cursor from getting "trapped" inside the WebView. Pressing the BACK button on your remote instantly forces focus back to the core application overlay controls.
* **UI Polish for TVs:** Custom CSS overrides hide the heavy 18px stock scrollbars (`.v-navigation-drawer`) in Fluidd. Stutter-free scrolling is achieved by replacing aggressive DOM mutation observers with a streamlined JavaScript injection layer.

### 🖼️ Picture-in-Picture (PiP) & Companion Mode
* **True Native Picture-in-Picture (PiP):** Monitor your print progress via a floating overlay (complete with real-time `tvPipProgress` state tracking) while running other apps on your TV or mobile platform.
* **Automated ADB Restriction Bypass:** Need to unlock advanced permissions like PiP or Overlays on restricted TV platforms? You can use our official desktop automation companion tool, the [KlippShell Helper](https://github.com/Ship-of-Agony/KlippShell-Helper), to configure your TV in seconds.
* **Auto-Discovery Companion Mode (Master/Slave):** Seamlessly use your smartphone as a remote control for your Android TV dashboard. A background subnet scanner (port 9999) instantly discovers the TV Master, establishes a smart TCP handshake, and synchronizes the active printer profile without manual IP entry.

### 🔧 Printer Management & Visualization
* **Model Selection with TV Search Dialog:** Built-in, error-tolerant G-code and model search functionality optimized for comfortable remote control entry.
* **45+ Creality Presets:** Out-of-the-box configurations for modern printers like the Creality K2 Plus and many more.
* **Fine-Tuned 3D Viewport:** Optimized Three.js camera view with perfect auto-centering for ideal alignment of your 3D models (such as the standard Benchy).
* **Local Profile Fallback:** Reliable offline fallback mechanisms utilizing local printer profiles if the connection to the remote Moonraker instance drops.

### 🔔 Notifications & Monitoring
* **Moonraker Milestone System:** A dedicated background worker tracks printing progress live and pushes unique milestone notifications (First Layer, 50%, 75%, 90%, 100%).
* **Android TV Notification Compliance:** Discrete Multi-Channel Notifications (`NotificationChannel`) segregating high-priority heads-up banners for critical events ("Fehler & Statusmeldungen") and standard background milestones ("Informationen & Meilensteine"). Channels register proactively at startup (`onCreate`) for immediate OS control.
* **WebRTC & Live Streams:** Full WebRTC permission handling and hardware layer enforcement (`LAYER_TYPE_HARDWARE`) for fluid, freeze-free MJPEG and WebRTC stream decoding inside the dashboard views.
* **On-Screen Display (OSD):** Real-time overlay reporting temperatures and print times, fully synchronized into your target interface language without hardcoding bugs.

---

## ⚡ Optional Desktop Companion (KlippShell Helper)

To unlock the full potential of KlippShell on hardware-restricted Android TV or Fire TV systems, we provide a multi-language desktop companion tool. 

👉 **Repository:** [KlippShell-Helper](https://github.com/Ship-of-Agony/KlippShell-Helper)

The helper application runs on your Windows PC and uses automated ADB commands to permanently grant key system permissions to KlippShell with a single click:
1. **Natives PiP** (`PICTURE_IN_PICTURE`)
2. **TV Overlay / System Alert Window** (`SYSTEM_ALERT_WINDOW`)
3. **Doze Whitelist** (Prevents Android from killing the background monitoring service)

*Note: It includes a built-in localization engine supporting EN, DE, ES, FR, PL, CS, and RU, alongside an automated reset/undo option.*

---

## 🛠️ Bug Fixes & Known Issues

 ##  Current Work in Progress & Roadmap

### 🐛 Bug Fixes
- [ ] **Android TV Notification Dialogs:** Fix confirmation focus and click handling – dialogs currently cannot be dismissed/confirmed via remote control (D-Pad/OK) after appearing.
- [ ] **Advanced Mode ("Secret Mode"):** Resolve errors and improve stability for advanced configuration features.

### ⚙️ UI & Core Features
- [ ] **Main Menu:** Implement an edit function for previously added printers (IP, name, port, etc.).
- [ ] **Companion Mode:** Improve connection stability, data synchronization, and overall handling.

### 📖 Documentation & FAQ
- [ ] **FAQ / Help Section:** Add direct URL links to the *KlippShell Helper App*.

---

## ⚙️ System & Convenience
* **Integrated GitHub OTA Updater:** Automatic, asynchronous online checking for new application releases upon startup directly via the GitHub API.
* **Built-in FAQ & License Viewers:** A stylized, blue-accented settings panel opening an asset-driven, scrollable FAQ dialog.
* **Calibrated Network Discovery:** Automatic network scanner timeout extended from 350ms to a highly stable 750ms to gracefully handle packet loss on weak Wi-Fi hardware typical of budget Android TV sticks.
* **Complete Internationalization (i18n):** Zero hardcoded text. Native localization of all statuses, toasts, and settings via dynamic `strings.xml` resource IDs supporting English (EN), German (DE), Spanish (ES), French (FR), Polish (PL), Czech (CS), and Russian (RU).

---

## 🛡️ Security & Google Certification
* 🛡️ **Google Play Protect Certification Roadmap:** KlippShell is strictly designed around official Android security policies. The code architecture is prepared for official Google Ecosystem Certification targeted for September 2026, ensuring absolute device compliance, verified sandboxing, and smooth sideloading compatibility across certified Android TV installations without OS ecosystem warnings.

---

## 🛠️ Technical Specifications (For Developers)
* **Architecture:** 100% type-safe layout architecture in Kotlin, completely eliminating risky Java reflection hacks (`params.javaClass.getField`) inside ConstraintLayout parameters to guarantee long-term OS compatibility.
* **UI Framework:** Full Material3 integration with reactive D-Pad focus styling.
* **Design Integrity:** Adaptive color mapping via `ContextCompat.getColor()` from adaptive resource IDs (e.g., `R.color.pill_normal_inactive`) for toast backgrounds and OSD banners, ensuring flawless transitions between Light and Dark modes.
* **Deep-Linking:** Registered `klippshell://` intent-filter schema in the manifest to bypass target SDK registration blockades on custom TV launcher providers (e.g., Projectivity Launcher).
* **State Management:** Strict use of the `savedInstanceState` bundle to eliminate infinite autostart loops when returning from the WebView activity.

---

## 🔒 Permissions
* **INTERNET:** Required for communicating with your Klipper/Moonraker node in the local network.
* **ACCESS_NETWORK_STATE:** Monitors active network connectivity to gracefully prevent crashes and check Wi-Fi states.
* **POST_NOTIFICATIONS:** Runtime notification permission anchored with a native, onboarding privacy disclosure dialog.

---

## 📂 Getting Started & Installation
1. Grab the latest APK release over at the **Releases** section.
2. Upon first launch, an interactive 6-point onboarding guide will seamlessly introduce you to Master/Slave setups, D-Pad mappings, and PiP ADB workflows directly within the printer list view.
3. *(Optional)* If your TV platform restricts Picture-in-Picture or Overlays, download the standalone desktop tool from the [KlippShell Helper Repository](https://github.com/Ship-of-Agony/KlippShell-Helper) to automatically authorize these advanced permissions.
4. For support or feedback, utilize the functional "Kontakt" (Contact) field inside the settings panel to directly dispatch an email intent to the studio.

---

## 📄 License & Open-Source Credits

### License
KlippShell (4 Creality)  
© 2026 Ship of Agony LABs  

This application is licensed under the Apache License, Version 2.0 (the "License"). You may not use these files except in compliance with the License. You may obtain a copy of the License at:  
http://www.apache.org/licenses/LICENSE-2.0  

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

### 📦 Third-Party Open Source Information & Assets
This application proudly utilizes the following open-source libraries, frameworks, and assets:

* **Kotlin Standard Library, Coroutines & Lifecycle**  
  Copyright JetBrains s.r.o. | Licensed under the Apache License, Version 2.0  
  [kotlinlang.org](https://kotlinlang.org)
* **Material Components for Android & Google Material Design Icons**  
  Copyright The Android Open Source Project | Licensed under the Apache License, Version 2.0  
  Used for the general user interface layout, icons, and control elements.
* **AndroidX & Jetpack Frameworks (WorkManager, TVProvider, Core-KTX)**  
  Copyright The Android Open Source Project | Licensed under the Apache License, Version 2.0  
  [developer.android.com/jetpack](https://developer.android.com/jetpack)
* **Coil (Coroutine Image Loader)**  
  Copyright 2023 Coil Contributors | Licensed under the Apache License, Version 2.0  
  [github.com/coil-kt/coil](https://github.com/coil-kt/coil)
* **Printer Images & Media Assets**  
  The product illustrations and device graphics used in this application originate from the official Creality repository.  
  [CrealityOfficial/CrealityPrint](https://github.com/CrealityOfficial/CrealityPrint)
* **3D Model Assets ("Benchy")**  
  The 3D Benchy placeholder model used in this application is based on the "Benchy - The jolly 3D printing torture-test" design from Cults3D / CreativeTools. The original file format was converted into the optimized GLB format using the ImageToStl platform.  
  [Model URL](https://cults3d.com/en/orders/160580329) | [Conversion Tool URL](https://imagetostl.com)

---

## ❤️ Core Inspiration & Acknowledgements
* **KlippHub by DnG Crafts:** This application is heavily inspired by the great project KlippHub. This sparked the vision of being able to conveniently monitor a 3D printer on the TV right from the comfort of the couch. Special thanks go to DnG for the tireless support, as well as for all the other valuable contributions (such as the Cfs RFID application) for the entire 3D printing community!
* **Development Support:** Google Gemini (AI) – for assistance in error analysis, code refactoring, and resolving complex crashes.
