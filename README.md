![Downloads](https://img.shields.io/github/downloads/Ship-of-Agony/KlippShell4Creality/total?style=flat-square&logo=github&color=blue&cache=bust1)

   KlippShell 4 Creality is a native, highly optimized Android application specifically designed to control and monitor Creality OS and any other Klipper-based 3D printers (via Moonraker, Fluidd, or Mainsail). It allows you      to conveniently manage your 3D printer directly from your Android TV box, smart TV, smartphone, or tablet—completely standalone, with no PC required.

🛋️ The Couch Concept: Keep an eye on your 3D printer relaxed from your sofa while watching your favorite show on TV—without having to constantly run to the printer or open up your laptop.

🛠️ Current Feature Set

    KlippShell has been optimized simultaneously for two entirely different environments: intuitive touch control on mobile devices and native D-Pad navigation via remote controls on Android TV.

📱 Responsive Dual-Screen Layout
    Smartphone Mode: UI elements and input masks stack vertically, optimized for comfortable one-handed mobile operation.
    Tablet & TV Mode (layout-sw600dp-land): An automatic side-by-side view keeps the main menu static on the left, while sub-menus and dashboards open instantly in the right column with zero delay.
    
📺 Seamless Klipper Integration & Performance Enhancements
    D-Pad Focus Escape Hatch: A hardware key event filter prevents heavy web layouts like Fluidd from swallowing remote control inputs. Pressing the BACK button on your remote instantly forces focus back to the native "Close"     button.
    Fluid Video Streaming: Full hardware acceleration (LAYER_TYPE_HARDWARE) and native WebRTC permission handling prevent camera feeds from freezing, enabling continuous decoding of MJPEG and WebRTC streams.
    Lag-Free TV Navigation: By removing aggressive DOM mutation observers from the JavaScript injection layer, scrolling and navigating run completely smooth, even on low-resource Android TV sticks.
    UI Polish: A tailored CSS override hides the heavy, distracting 18px blue TV system scrollbars inside Fluidd's left navigation drawer, saving valuable screen space on your television.
    
🔔 Live Popups & Acoustic Signals
    Real-Time Telemetry Tracking: The app informs you visually and acoustically about critical printing landmarks:
    Printer goes unexpectedly Offline (connection loss)
    First Layer successfully completed
    Pogress milestones reached at 50%, 75%, and 90%
    Print job successfully finished (100%)
    Fully Customizable: Both the clear live popup tiles and the acoustic notification loops can be toggled on or off individually for each status in the settings menu.

🖼️ Native Picture-in-Picture (PiP) Mode
    Close the app on your TV to watch your favorite show or browse other applications—a floating, resizable overlay window keeps you updated on the webcam stream and live print progress via a dedicated status lab.

📡 Android TV Launcher Channels
    An integrated Jetpack WorkManager background service (KlipperTvWorker) periodically fetches your printer's current status via the Moonraker API and embeds a dynamic preview program, complete with live poster art, right as     a channel onto your Android TV Homescreen.

🔒 Local Privacy & Update Security
    No Cloud Dependency: Communication happens exclusively within your local network between the app and your printer. No control or telemetry data ever leaves your home.
    Asynchronous GitHub OTA Updater: KlippShell silently checks the GitHub API on startup. If a new release is available, a localized dialog lets you download the latest update directly.

⚙️ System Requirements & Compatibility
    Minimum Android Version: Android 5.0 (API Level 21) or higher.
    Optimized For: Android 11 up to Android 14+ (Target SDK 36 for maximum future-proofing).
    Hardware Compatibility: Due to its lightweight architecture, KlippShell runs smoothly on:
    Android smartphones & tablets (e.g., Google Pixel, Samsung Galaxy)
    Android TV boxes & Smart TVs (e.g., Xiaomi Mi Box, Nvidia Shield TV)
    Amazon Fire TV Sticks (virtually any generation released over the last decade)
    
👨‍💻 Source Code & Contributing
    This project is maintained with passion as open-source software. The code is written modularly in Kotlin, utilizes responsive XML-based Material3 DayNight themes, and is ready to be imported into Android Studio.
    Internationalization (i18n): Full localization support (DE, EN, CS, ES, FR, PL, RU) is completely integrated via clean resource strings.

🙏 Special Thanks
    A huge thank you goes out to DnG Crafts for providing the fundamental inspiration with their incredible KlippHub project. Your tireless contributions significantly enrich the entire 3D printing community!
