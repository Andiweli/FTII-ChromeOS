# Build- und Prüfprotokoll

Stand: 15. August 2026

## Verwendete Toolchain

| Komponente | Version |
|---|---|
| FT2 Clone | `e725a93b21cd5455e748dbac7b3173213367a8bb` |
| SDL2 | `release-2.32.10` |
| Android Platform | API 35 |
| Android Build-Tools | 35.0.0 |
| Android NDK | 27.2.12479018 (Clang 18.0.3) |
| CMake | 3.22.1 |
| Gradle Wrapper | 8.10.2 |
| Java | Temurin 17.0.16 |

## Erfolgreiche statische Builds

- `libmain.so` und `libSDL2.so` für `arm64-v8a`
- `libmain.so` und `libSDL2.so` für `x86_64`
- vollständige Java-/SDLActivity-Kompilierung gegen Android API 35
- DEX-Erzeugung mit D8, minSdk 28
- Ressourcen- und Manifest-Link mit AAPT2

Die nativen Bibliotheken wurden im Release-Modus gebaut. Bei SDL2 trat nur eine
bekannte Deprecation-Warnung für `ASensorManager_getInstance` auf; FT2 selbst
baute ohne Fehler.

## APK-Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Paketname | `com.ast.ft2clone` |
| Version | `1.0.10-chromeos` / Code 10010 |
| minSdk / targetSdk | 28 / 35 |
| ABIs | ARM64 und x86_64 vorhanden |
| Signatur | APK Signature Scheme v3, gültig |
| Zertifikat | lokales 3072-Bit-RSA-Testzertifikat |
| ZIP-Alignment | vollständig bestanden |
| Native Page-Alignment | 16 KiB bestanden |
| ELF-LOAD-Alignment | 0x4000 bei beiden ABIs |
| Native Abhängigkeiten | nur Android-Systembibliotheken plus mitgeliefertes SDL2 |
| ChromeOS-Merkmale | Touch optional, PC optional, resizable, DocumentsProvider |
| Fensterskalierung | SDL resizable; Resize-/Maximize-/Restore-Ereignisse verarbeitet |
| Mauskoordinaten | Android Surface-lokal; Render-Versatz in Fenster und Vollbild berücksichtigt |
| Tastatur-Scancodes | Java übergibt Android-Keycode plus Hardware-Scancode; physische Tasten werden über SDLs Linux-Evdev-Tabelle übersetzt, Android-Keycode bleibt Fallback |
| FT2-Notentabelle | unveränderter Upstream; statisch verifiziert: physische Z-Position = Note 1, S = 2, X = 3 und Q = 13 |
| Maximierter Start | keine statischen `<layout>`-Bounds; Launcher und Tracker besitzen ChromeOS-`FreeformWindowSize=maximize` und verwenden denselben Task |
| FT2-Vollbild | Config-Bit plus nativer Android-Marker; Immersive-Mode bei Start, Resume und Fokusgewinn mit fünf zeitversetzten Anwendungen |
| Neustartpfad | Tracker in privatem Prozess; Prozessende erst nach vollständigem `SDLActivity.onDestroy()` |
| Schließübergang | nativer Prepare-Exit-Befehl; deckende `#101010`-Ebene vor Surface-Abbau; Task vor dem Entfernen in den Hintergrund; Fensteranimation aus |
| Audio-Backend | vollständiger 1.0.8-Rollback; OpenSL ES vor unverändertem SDL-AAudio, danach AudioTrack-Fallback |
| Audio-Puffer | 1.024-Sample-Standard wiederhergestellt; einmaliger 512 → 1.024 Rollback nur für von 1.0.8 migrierte Installationen |
| Dateizugriff | Play-Dateien mit Sonderfreigabe; privater Fallback ohne Freigabe |
| Launcher-Icon | adaptiv/maskierbar plus runder Legacy-Fallback; Motiv auf 84 % verkleinert und blauer Sicherheitsrand vergrößert |

## Was ohne Zielgerät nicht geprüft werden konnte

- tatsächlicher Start auf ARC/ARCVM
- wiederholtes sofortiges Beenden und Starten über X sowie Esc-Bestätigung
- optische Bestätigung, dass beim Schließen kein weißer Zwischenframe mehr erscheint
- Zeitpunkt des Ausblendens von ChromeOS-Titelleiste und Shelf
- Maus-Hit-Test nach Maximieren und Wiederherstellen
- messbare Audio-Latenz sowie Dropout-/XRun-Verhalten eines Chromebooks
- Suspend/Resume über Deckel und ChromeOS-Energiesparen
- konkrete Hardware-Scancodes verschiedener Chromebook- und externer Tastaturen
- Dateidialogdarstellung der jeweiligen ChromeOS-Version

Die APK ist deshalb als **Test-Build** gekennzeichnet. Vor produktiver
Verteilung ist der Hardware-Smoke-Test aus der Portierungsanalyse erforderlich.
