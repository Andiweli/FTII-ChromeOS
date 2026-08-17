# FT II für ChromeOS (Android)

Dies ist ein vollständiges Android-Studio-Projekt für eine auf ChromeOS
ausgerichtete Portierung von 8bitbubsys FastTracker-II-Clone. Die originale
Tracker-Oberfläche und Audio-Engine bleiben nativ in C; SDL2 stellt Fenster,
Eingabe, Audio und OpenGL ES auf Android bereit.

## Inhalt dieses Pakets

- FT2-Quellstand `e725a93b21cd5455e748dbac7b3173213367a8bb`
- SDL2 `release-2.32.10` als reproduzierbare lokale Abhängigkeit
- Android-App-Modul mit Java-Brücke und nativer CMake-Konfiguration
- ARM64- und x86_64-Unterstützung für aktuelle Chromebooks
- ChromeOS-Dateiintegration über den Android DocumentsProvider
- ausführliche [Portierungsanalyse](PORTIERUNGSANALYSE-CHROMEOS.md)
- dokumentiertes [Build- und Prüfprotokoll](TESTPROTOKOLL-CHROMEOS.md)

## Voraussetzungen

In Android Studio 2025 werden benötigt:

- JDK 17 (die eingebettete JetBrains Runtime genügt)
- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0
- Android NDK `27.2.12479018`
- CMake 3.22.1

Android Studio kann fehlende SDK-Komponenten beim ersten Sync installieren.

## Projekt bauen

1. Diesen Ordner in Android Studio über **Open** öffnen.
2. Den Gradle-Sync abwarten.
3. Für einen lokalen Test **Build > Build APK(s)** wählen.
4. Das Ergebnis liegt unter `app/build/outputs/apk/debug/`.
5. Für eine eigene verteilbare APK **Build > Generate Signed App Bundle or APK**
   verwenden und einen eigenen, dauerhaft gesicherten Keystore wählen.

Kommandozeile:

```bash
./gradlew assembleDebug
```

Die mitgelieferte Test-APK ist installierbar, aber absichtlich nur mit einem
lokalen Testzertifikat signiert. Sie ist nicht als Play-Store-Release gedacht.

## Installation auf ChromeOS

Bei aktiviertem Android-Debugging kann die APK beispielsweise installiert
werden mit:

```bash
adb install -r FT-II-ChromeOS-v1.0.10-final-test.apk
```

Alternativ kann sie je nach ChromeOS-Konfiguration über die Dateien-App
geöffnet werden. Unternehmensrichtlinien oder die ChromeOS-Sicherheitsstufe
können Sideloading blockieren.

## Dateien öffnen und sichern

Beim ersten Start fragt FT II nach dem Android-Sonderzugriff **Zugriff auf alle
Dateien**. Wird er erlaubt, startet `Disk Op.` im sichtbaren Ordner
`Play-Dateien/FT II`. Mit `..` kann direkt zu `Download`, `Music` und den
anderen Ordnern unter Play-Dateien navigiert werden.

- Module und Samples nach **Play-Dateien/FT II** kopieren und über `Disk Op.`
  öffnen.
- Dateien können auch mit **Öffnen mit FT II** oder **Teilen**
  importiert werden; sie werden dabei sicher in den Workspace kopiert.
- Von FT2 erzeugte XM- und WAV-Dateien erscheinen ebenfalls dort.

Mit **Nur Workspace** läuft die App weiterhin ohne den Sonderzugriff. Dann wird
der private Workspace verwendet, der zusätzlich über den ChromeOS-Dateidialog
angeboten wird. Vor einer Deinstallation sollten wichtige Dateien aus diesem
privaten Workspace kopiert werden.

## ChromeOS-spezifisches Verhalten

- Diese für ChromeOS optimierte Fassung startet absichtlich immer maximiert.
  Launcher und Tracker sind beide im Manifest direkt als `maximize`
  deklariert und bleiben im selben Android-Task. Dadurch gibt es keine kleine
  feste Startgröße und keine vom jeweiligen Chromebook abhängige
  Fensterwiederherstellung mehr.
- Das Fenster bleibt frei skalierbar. Maximieren und Größenänderungen skalieren
  FT2 sofort proportional; `Config > Miscellaneous > Stretched` füllt auf
  Wunsch auch Fenster mit abweichendem Seitenverhältnis vollständig aus.
- `Config > Miscellaneous > Fullscreen` wird unter Android beim Anklicken
  sofort gespeichert und zusätzlich direkt an die Android-Seite gemeldet. Beim
  nächsten Öffnen werden beim bereits maximierten Root-Fenster zusätzlich
  Titelleiste und Shelf dauerhaft im Immersive-Mode verborgen. Ein zusätzlicher
  Klick auf **Save config** ist nicht nötig.
- Beim Maximieren ohne FT2-`Fullscreen` verwendet die Maus ausschließlich
  lokale Surface-Koordinaten einschließlich des zentrierten Render-Versatzes.
- FT2/SDL laufen getrennt von Launcher und DocumentsProvider in einem privaten
  Tracker-Prozess. Nach dem vollständigen SDL-Shutdown wird nur dieser Prozess
  beendet; jeder folgende Start beginnt garantiert mit frischem nativen
  Programmzustand.
- Eine bestätigte Beendigung blendet vor dem SDL-Surface-Abbau eine deckende
  dunkelgraue Android-Ebene ein. Anschließend wird der komplette Task zuerst
  hinter ChromeOS verschoben und erst dort ohne Fensteranimation entfernt. So
  bleibt weder eine leere SDL-Surface noch das Android-Übergangsfenster sichtbar.
- Die Audioausgabe verwendet wieder den in 1.0.7 funktionierenden SDL-Standard:
  OpenSL ES vor AAudio und 1.024 Samples als stabilen Puffer. Die experimentelle
  AAudio-Low-Latency-Konfiguration aus 1.0.8 ist vollständig entfernt. Bei
  Installationen, die 1.0.8 bereits auf 512 Samples umgestellt hatte, wird der
  Puffer einmalig auf 1.024 zurückgesetzt; spätere manuelle Änderungen unter
  `Config > Audio` bleiben erhalten.
- Maus, Trackpad und Hardware-Tastatur sind der primäre Bedienweg; Touch ist
  optional und nicht als eigene Tablet-Oberfläche gestaltet.
- Physische Tastaturen werden anhand ihres Hardware-Scancodes ausgewertet.
  Dadurch entspricht FT2s Klaviatur wieder dem Original: auf deutscher QWERTZ-
  Belegung spielt `Y S X D C V G B H N J M` die untere und
  `Q 2 W 3 E R 5 T 6 Z 7 U` die nächsthöhere Oktave. QWERTY- und andere
  Tastaturen folgen denselben physischen Tastenpositionen.
- ARM64 und x86_64 sind in derselben APK enthalten.
- Native Bibliotheken sind für 16-KiB-Speicherseiten vorbereitet und im APK
  entsprechend ausgerichtet.
- Das adaptive und das runde Legacy-Icon besitzen einen vergrößerten blauen
  Sicherheitsrand für kreisrunde Android-/ChromeOS-Masken.
- Der direkte Zugriff auf Play-Dateien benötigt den ausdrücklich bestätigten
  Android-Sonderzugriff. Ohne ihn bleibt der isolierte Workspace verfügbar.

Für FT2-Tastenkürzel mit F-Tasten kann unter ChromeOS die Option sinngemäß
**Tasten der oberen Reihe als Funktionstasten verwenden** nötig sein.

## Bewusste Grenzen dieser ersten Portierung

- MIDI-Ein- und -Ausgabe ist deaktiviert. RtMidi aus dem Desktop-Build wurde
  nicht in den Android-Lebenszyklus integriert.
- Aufnahme über Mikrofon oder Audioeingang ist nicht freigegeben und wurde
  nicht auf Chromebook-Hardware validiert.
- Es gab in der Build-Umgebung keinen physischen Chromebook-Laufzeittest.
  Native Builds, Java-Kompilierung, APK-Struktur, Signatur und Alignment wurden
  geprüft; ein kurzer Hardware-Smoke-Test bleibt vor produktiver Verteilung
  erforderlich.

Der Sonderzugriff `MANAGE_EXTERNAL_STORAGE` ist für diese per Sideloading
verteilte ChromeOS-Fassung gedacht. Eine Veröffentlichung im Google Play Store
würde dafür eine separate Richtlinienprüfung oder ein SAF-basiertes Dateimodell
benötigen.

## Lizenzen

FT2 Clone bleibt unter der im Stammordner enthaltenen `LICENSE` lizenziert.
SDL2 ist unter seiner zlib-Lizenz lizenziert; der vollständige SDL2-Quelltext
und `third_party/SDL2/LICENSE.txt` sind enthalten. Die Portierung ändert diese
Lizenzen nicht.
