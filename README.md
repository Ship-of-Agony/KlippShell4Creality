![Downloads](https://img.shields.io/github/downloads/Ship-of-Agony/KlippShell4Creality/total?style=flat-square&logo=github&color=blue&cache=bust1)

KlippShell 4 Creality

KlippShell 4 Creality brings your Klipper-powered 3D printers natively to Android devices – uncompromisingly optimized for smartphones, tablets, and Android TV boxes. Comfortably monitor and control your printing processes directly from your couch, without having to open a separate web interface on your PC.
🚀 Key Features & Highlights
📱 Home Screen Widget (New)

    🧩 Native Android Home Screen Widget Integration: Engineered a responsive application widget utilizing remote view bindings to display real-time Klipper status, current nozzle/bed temperatures, and print job progress percentages instantly without opening the main application window.

📺 Tailored for Android TV & Tablets

    Responsive Dual-Screen Layout: Automatic side-by-side display in landscape mode (tablets/TV) featuring a persistent main menu on the left and dynamic sub-menus on the right (layout-sw600dp-land).

    Optimized D-Pad Navigation: Full support for TV remote controllers backed by intelligent focus routing and Material3 feedback. Touch targets have been fine-tuned to fit the TV experience perfectly.

    Fluidd & Mainsail Escape-Hatch: Prevents the cursor from getting "trapped" inside the WebView. Pressing the BACK button on your remote instantly forces focus back to the core application overlay controls.

    UI Polish for TVs: Custom CSS overrides hide the heavy 18px stock scrollbars (.v-navigation-drawer) in Fluidd. Stutter-free scrolling is achieved by replacing aggressive DOM mutation observers with a streamlined JavaScript injection layer.

🖼️ Picture-in-Picture (PiP) & Companion Mode

    True Native Picture-in-Picture (PiP): Monitor your print progress via a floating overlay (complete with real-time tvPipProgress state tracking) while running other apps on your TV or mobile platform.

    PiP ADB Restriction Bypass: Integrated, code-formatted (<tt>) step-by-step terminal guide to permanently force PiP overlay permissions via AppOps on hardware-restricted TV platforms.

    Auto-Discovery Companion Mode (Master/Slave): Seamlessly use your smartphone as a remote control for your Android TV dashboard. A background subnet scanner (port 9999) instantly discovers the TV Master, establishes a smart TCP handshake, and synchronizes the active printer profile without manual IP entry.

🔧 Printer Management & Visualization

    Model Selection with TV Search Dialog: Built-in, error-tolerant G-code and model search functionality optimized for comfortable remote control entry.

    45+ Creality Presets: Out-of-the-box configurations for modern printers like the Creality K2 Plus and many more.

    Fine-Tuned 3D Viewport: Optimized Three.js camera view with perfect auto-centering for ideal alignment of your 3D models (such as the standard Benchy).

    Local Profile Fallback: Reliable offline fallback mechanisms utilizing local printer profiles if the connection to the remote Moonraker instance drops.

🔔 Notifications & Monitoring

    Moonraker Milestone System: A dedicated background worker tracks printing progress live and pushes unique milestone notifications (First Layer, 50%, 75%, 90%, 100%).

    Android TV Notification Compliance: Discrete Multi-Channel Notifications (NotificationChannel) segregating high-priority heads-up banners for critical events ("Fehler & Statusmeldungen") and standard background milestones ("Informationen & Meilensteine"). Channels register proactively at startup (onCreate) for immediate OS control.

    WebRTC & Live Streams: Full WebRTC permission handling and hardware layer enforcement (LAYER_TYPE_HARDWARE) for fluid, freeze-free MJPEG and WebRTC stream decoding inside the dashboard views.

    On-Screen Display (OSD): Real-time overlay reporting temperatures and print times, fully synchronized into your target interface language without hardcoding bugs.

⚙️ System & Convenience

    Integrated GitHub OTA Updater: Automatic, asynchronous online checking for new application releases upon startup directly via the GitHub API.

    Built-in FAQ & License Viewers: A stylized, blue-accented settings panel opening an asset-driven, scrollable FAQ dialog.

    Calibrated Network Discovery: Automatic network scanner timeout extended from 350ms to a highly stable 750ms to gracefully handle packet loss on weak Wi-Fi hardware typical of budget Android TV sticks.

    Complete Internationalization (i18n): Zero hardcoded text. Native localization of all statuses, toasts, and settings via dynamic strings.xml resource IDs supporting English (EN), German (DE), Spanish (ES), French (FR), Polish (PL), Czech (CS), and Russian (RU).

🛠️ Technical Specifications (For Developers)

    Architecture: 100% type-safe layout architecture in Kotlin, completely eliminating risky Java reflection hacks (params.javaClass.getField) inside ConstraintLayout parameters to guarantee long-term OS compatibility.

    UI Framework: Full Material3 integration with reactive D-Pad focus styling.

    Design Integrity: Adaptive color mapping via ContextCompat.getColor() from adaptive resource IDs (e.g., R.color.pill_normal_inactive) for toast backgrounds and OSD banners, ensuring flawless transitions between Light and Dark modes.

    Deep-Linking: Registered klippshell:// intent-filter schema in the manifest to bypass target SDK registration blockades on custom TV launcher providers (e.g., Projectivity Launcher).

    State Management: Strict use of the savedInstanceState bundle to eliminate infinite autostart loops when returning from the WebView activity.

🔒 Permissions

    INTERNET: Required for communicating with your Klipper/Moonraker node in the local network.

    ACCESS_NETWORK_STATE: Monitors active network connectivity to gracefully prevent crashes and check Wi-Fi states.

    POST_NOTIFICATIONS: Runtime notification permission anchored with a native, onboarding privacy disclosure dialog.

📂 Getting Started & Installation

    Grab the latest APK release over at the Releases section.

    Upon first launch, an interactive 6-point onboarding guide will seamlessly introduce you to Master/Slave setups, D-Pad mappings, and PiP ADB workflows directly within the printer list view.

    For support or feedback, utilize the functional "Kontakt" (Contact) field inside the settings panel to directly dispatch an email intent to the studio.

📄 License & Open-Source Credits
License

KlippShell (4 Creality)

© 2026 Ship of Agony LABs

This application is licensed under the Apache License, Version 2.0 (the "License"). You may not use these files except in compliance with the License. You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
📦 Third-Party Open Source Information & Assets

This application proudly utilizes the following open-source libraries, frameworks, and assets:

    Kotlin Standard Library, Coroutines & Lifecycle

    Copyright JetBrains s.r.o. | Licensed under the Apache License, Version 2.0

    kotlinlang.org

    Material Components for Android & Google Material Design Icons

    Copyright The Android Open Source Project | Licensed under the Apache License, Version 2.0

    Used for the general user interface layout, icons, and control elements.

    material-components-android

    AndroidX & Jetpack Frameworks (WorkManager, TVProvider, Core-KTX)

    Copyright The Android Open Source Project | Licensed under the Apache License, Version 2.0

    developer.android.com/jetpack

    Coil (Coroutine Image Loader)

    Copyright 2023 Coil Contributors | Licensed under the Apache License, Version 2.0

    github.com/coil-kt/coil

    Printer Images & Media Assets

    The product illustrations and device graphics used in this application originate from the official Creality repository.

    CrealityOfficial/CrealityPrint

    3D Model Assets ("Benchy")

    The 3D Benchy placeholder model used in this application is based on the "Benchy - The jolly 3D printing torture-test" design from Cults3D / CreativeTools. The original file format was converted into the optimized GLB format using the ImageToStl platform.

        Model URL

        Conversion Tool URL

❤️ Core Inspiration & Acknowledgements

    KlippHub by DnG Crafts: This application is heavily inspired by the great project KlippHub. This sparked the vision of being able to conveniently monitor a 3D printer on the TV right from the comfort of the couch. Special thanks go to DnG for the tireless support, as well as for all the other valuable contributions (such as the Cfs RFID application) for the entire 3D printing community!

    Development Support: Google Gemini (AI) – for assistance in error analysis, code refactoring, and resolving complex crashes.
