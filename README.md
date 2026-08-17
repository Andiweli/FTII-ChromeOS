# FT II for Android and ChromeOS

![Platform](https://img.shields.io/badge/platform-Android%20%7C%20ChromeOS-4285F4)
![Architecture](https://img.shields.io/badge/ABI-ARM64%20%7C%20x86__64-34A853)
![License](https://img.shields.io/badge/license-BSD%203--Clause-lightgrey)

**FT II for Android and ChromeOS** is a native port of the modern FastTracker II clone, optimized especially for Chromebook desktop use. It brings the classic tracker workflow to Android while preserving the appearance, behavior, keyboard layout and XM playback of the original clone as closely as possible.

## Android and ChromeOS adaptations

This fork adds the platform integration required to make the desktop-oriented FT2 clone behave like a native Android and ChromeOS application:

- Android Studio project and SDL2-based Android integration
- Native **ARM64** and **x86_64** builds with 16 KiB page-size compatibility
- Proper ChromeOS freeform-window scaling and proportional rendering
- Persistent maximized and fullscreen modes, including immersive fullscreen
- Correct mouse coordinates in resized, maximized and fullscreen windows
- Physical keyboard scan-code handling, preserving the original position-based FT2 piano layout on QWERTY, QWERTZ and other hardware keyboards
- Clean Android lifecycle handling for reliable startup, shutdown and restart
- Access to ChromeOS/Android shared files, with a private workspace fallback
- Adaptive launcher icon and ChromeOS-friendly application/window presentation

The tracker core and its original note mapping remain unchanged wherever possible. Platform-specific fixes are kept in the Android, SDL and window-management layers.

## About the original FT2 clone

This project is forked from [8bitbubsy/ft2-clone](https://github.com/8bitbubsy/ft2-clone), created and maintained by **Olav Sørensen (8bitbubsy)**.

The upstream project is a highly accurate recreation of **FastTracker II**, the classic music tracker released by Triton for MS-DOS in the 1990s. Its XM player was directly ported from the original FastTracker II source code for maximum playback accuracy, while other parts combine original FT2 code with new implementations and modern extensions.

The original clone targets Windows, macOS and Linux and adds useful features such as modern sample-format support, improved interpolation, waveform tools, drag and drop, text editing and extended module import. More information and official desktop builds are available at [16-bits.org/ft2.php](https://16-bits.org/ft2.php).

To learn more about the historic tracker, see [FastTracker II on Wikipedia](https://en.wikipedia.org/wiki/FastTracker_2).

## Legal

The source code is distributed under the [BSD 3-Clause License](LICENSE).

Copyright for the upstream FT2 clone remains with **Olav Sørensen and its contributors**. Portions of the project are based on the original FastTracker II source code and are used under the terms described by the upstream project.

FastTracker II and all related names, logos and third-party assets remain the property of their respective owners. This Android/ChromeOS port is an independent, community-developed project and is not affiliated with or endorsed by Triton, Google or the original rights holders.

The software is provided **as is**, without warranty of any kind. See the license file for the complete terms.
