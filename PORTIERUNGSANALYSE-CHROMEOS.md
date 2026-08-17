# Portierungsanalyse: FT2 Clone als Android-App für ChromeOS

## Ergebnis

Eine ChromeOS-Portierung über die Android-Laufzeit ist technisch gut
vertretbar, aber kein reiner Neu-Build. Die Audio- und Tracker-Kernlogik ist
portabel; rund um Dateisystem, Zeichensatzkonvertierung, Fensterverwaltung und
Android-Lebenszyklus waren gezielte Anpassungen erforderlich. Dieses Projekt
setzt diese Anpassungen um und erzeugt eine installierbare Universal-APK für
x86_64- und ARM64-Chromebooks.

Ausgangsbasis:

- Upstream: `8bitbubsy/ft2-clone`
- FT2-Commit: `e725a93b21cd5455e748dbac7b3173213367a8bb`
- SDL2: `release-2.32.10`, Commit
  `5d249570393f7a37e037abf22cd6012a4cc56a71`
- Android: minSdk 28, targetSdk/compileSdk 35

## Quellcodebefund

| Bereich | Upstream-Situation | ChromeOS/Android-Lösung | Risiko |
|---|---|---|---|
| Tracker/Replay | Plattformneutrales C, eigene Mixer | unverändert nativ kompiliert | niedrig |
| Audioausgabe | SDL-Audio plus Desktop-Geräteauswahl | bewährter SDL-Android-Pfad mit OpenSL ES vor AAudio/AudioTrack | mittel; Hardware-Smoke-Test nötig |
| Grafik | SDL-Renderer, internes Bild 632×400 | OpenGL ES 2, zentriertes Aspect-Fit | niedrig |
| Eingabe | SDL Maus/Tastatur | Maus über SDLActivity; Tastaturpositionen über Android-Hardware-Scancode und SDLs Linux-Tabelle | niedrig bis mittel; ChromeOS-F-Tasten beachten |
| Dateisystem | Desktop-Pfade, Home-Verzeichnis, `fts` | Play-Dateien mit Sonderfreigabe, Workspace-Fallback, DocumentsProvider, portable Rekursion | mittel |
| Zeichensätze | `iconv` für UTF-8/CP850 | integrierter CP850/UTF-8-Konverter | niedrig |
| MIDI | RtMidi/ALSA/CoreMIDI/WinMM | für v1 deaktiviert | bewusst fehlend |
| Sampling | SDL-Audioaufnahme | kompiliert, aber ohne Mikrofonfreigabe | bewusst nicht freigegeben |
| CPU/Codecs | C-Decoder, ARM-Optimierungen vorhanden | ARM64 und x86_64 | niedrig |

## Warum der Port relativ kompakt bleibt

Der FT2-Clone trennt seine eigentliche Anwendung bereits weitgehend von der
Plattform. SDL2 deckt auf Android die entscheidenden Systemdienste ab:
Fenster/Surface, Ereignisse, Maus, Tastatur, Timer, Audioausgabe und OpenGL ES.
Die Modul- und Sampleformate werden durch den bestehenden C-Code verarbeitet;
dafür ist keine Android-Neuimplementierung nötig.

Der originale Desktop-CMake-Build kann auf Android dagegen nicht direkt
verwendet werden. Er erwartet je nach Plattform Bibliotheken wie ALSA, RtMidi,
`iconv` und BSD `fts`. Das Android-App-Modul besitzt deshalb eine eigene
CMake-Einstiegsdatei und bindet SDL2 als Quellabhängigkeit ein.

## Umgesetzte Änderungen

### Android-Projekt und Build

- Gradle 8.10.2 und Android Gradle Plugin 8.8.2
- NDK 27.2.12479018 und CMake 3.22.1 festgelegt
- ABI-Filter `arm64-v8a` und `x86_64`
- `libmain.so` für FT2 plus `libSDL2.so`
- Release-C-Code mit `-O3` und den bestehenden FT2-Optimierungen
- ELF- und APK-Alignment für 16-KiB-Speicherseiten

### Android-Lebenszyklus und Eingabe

`Ft2Activity` erweitert SDLs `SDLActivity`. FT2 bleibt dadurch ein natives
SDL-Programm, erhält aber eine Android-konforme Activity. Pausieren blockiert
den UI-Thread nicht; Touch-zu-Maus-Synthese ist abgeschaltet, damit echte
ChromeOS-Mausereignisse nicht doppelt erscheinen.

SDL 2.32.10 reicht unter Android standardmäßig nur den Android-Keycode weiter.
Auf ChromeOS kann dieser bereits der aktiven QWERTZ-Belegung entsprechen,
während FT2 seine Klaviatur bewusst nach physischen USB-Tastenpositionen
auswertet. Dadurch wurde die deutsche untere `Y`-Taste fälschlich als
`SDL_SCANCODE_Y` und somit als Note 22 statt als Grundnote 1 interpretiert.
Die Portierung übergibt zusätzlich `KeyEvent.getScanCode()` und übersetzt
gültige Werte mit SDLs vorhandener Linux-Evdev-Tabelle. Fehlt ein nutzbarer
Hardware-Scancode, bleibt der bisherige Android-Keycode als Fallback erhalten.
FT2s originale `scancodeKey2Note`-Tabelle bleibt unverändert.

FT2 und SDL laufen in einem privaten Android-Prozess, getrennt von Launcher und
DocumentsProvider. Nach dem vollständigen `SDLActivity`-Shutdown wird nur
dieser Tracker-Prozess beendet. Das ist für FT2 wichtig, weil zahlreiche
globale C-Strukturen auf einen einmaligen Prozessstart ausgelegt sind; jeder
neue Lauf erhält dadurch einen frischen nativen Prozess. Der für Android
ungeeignete Desktop-Crash-Signalhandler ist deaktiviert.

Der Prozess wird unmittelbar nach der zurückgekehrten SDL-`onDestroy()`-Routine
beendet. FT2 sendet eine bestätigte Beendigung bereits aus der nativen
Ereignisschleife an Java. Die Activity deckt die getrennt gerenderte SDL-
`SurfaceView` dadurch noch vor deren Abbau mit `#101010` ab. Beim anschließenden
`finish()` wird der vollständige Android-Task zuerst in den Hintergrund
verschoben und erst dort entfernt; alle Fensteranimationen sind deaktiviert.
Window, normales View-Layout, Decor-View, Task-Preview und Android-12+-Splash
verwenden ebenfalls denselben dunklen Hintergrund.

### Audio-Latenz

SDL 2.32.10 priorisiert in seinem Android-Standardpfad OpenSL ES und verwendet
AAudio sowie AudioTrack als nachgeordnete Alternativen. Die in Version 1.0.8
erprobte Umstellung auf AAudio mit Low-Latency-, Exclusive- und Game-Vorgaben
führte auf dem Ziel-Chromebook zu vollständigem Tonausfall und wurde deshalb
komplett entfernt. Version 1.0.9 entspricht im Audio-Backend wieder dem
funktionierenden Stand 1.0.7.

Auch der stabile FT2-Standardpuffer von 1.024 Samples ist wiederhergestellt.
Nur Installationen, die durch 1.0.8 bereits einmalig auf 512 Samples migriert
wurden, werden genau einmal auf 1.024 Samples zurückgesetzt. Danach bleiben
manuelle Änderungen unter `Config > Audio` unangetastet. Die tatsächliche
Taste-zu-Schall-Latenz enthält weiterhin Scheduling, Android/ChromeOS-Mixer,
DSP und das physische Ausgabegerät; insbesondere Bluetooth kann zusätzliche
Verzögerung verursachen. Eine weitere Latenzoptimierung sollte erst anhand
konkreter Audio-Logs des Zielgeräts erfolgen.

Das Manifest und das SDL-Fenster erlauben Größenänderung, freie Orientierung
und Desktop-Fenstermaße. FT2 verarbeitet `RESIZED`, `SIZE_CHANGED`, `MAXIMIZED`
und `RESTORED`, berechnet Render- und Mauskoordinaten neu und reagiert dadurch
unmittelbar auf den ChromeOS-Maximieren-Button.

Im Android-Zweig werden Mauskoordinaten immer lokal von SDLs Surface gelesen.
Das vermeidet die unter ChromeOS mögliche Mischung aus globalen Desktop- und
lokalen Fensterkoordinaten. Der Versatz des zentrierten Render-Rechtecks wird
sowohl im FT2-Vollbild als auch bei einem nur über ChromeOS maximierten Fenster
abgezogen.

### Fenster und Darstellung

Die FT2-Oberfläche arbeitet intern mit 632×400 Pixeln. Die Portierung berechnet
bei jeder Größenänderung ein zentriertes, größtmögliches Render-Rechteck.
ChromeOS-Vollbild verwendet anders als der Desktop-Zweig eine proportionale
fraktionale Skalierung statt ausschließlich ganzzahliger Faktoren. Dadurch
werden maximierte Fenster tatsächlich ausgenutzt; nur die optionale Einstellung
`Stretched` darf das Bild absichtlich auf das gesamte Fenster verzerren.

Frühere Teststände kombinierten eine feste Launcher-Größe mit nachträglichen
leeren `ActivityOptions`-Launch-Bounds und getrennten Task-Affinitäten. Auf dem
Ziel-Chromebook blieb jedoch der zuerst für den Launcher erzeugte kleine
Fenstercontainer maßgeblich. Das entspricht der ChromeOS-Regel, dass die
Root-Activity die Fensterattribute des gesamten Activity-Stacks bestimmt.

Die finale ChromeOS-Fassung setzt deshalb bereits auf beiden möglichen
Root-Activities die Metadaten-Vorgabe
`WindowManagerPreference:FreeformWindowSize=maximize`, entfernt sämtliche
festen Startabmessungen und startet den Tracker im selben Task wie den
Launcher. Damit ist das Fenster vor dem Aufbau der SDL-Surface maximiert. Die
Fassung startet bewusst immer maximiert; eine fehleranfällige Rekonstruktion
des letzten ChromeOS-Fensterzustands ist nicht mehr nötig.

FT2 übermittelt seinen `Fullscreen`-Schalter zusätzlich über einen
SDL-Android-Befehl direkt an die Java-Seite; das verschlüsselte Config-Byte
bleibt ein zweiter Start-Fallback. Bei aktivem Schalter hält die Activity den
Immersive-Mode während der gesamten Sitzung aktiv und setzt ihn beim Erzeugen,
Resume und Fokusgewinn erneut. Titelleiste und Shelf können deshalb erst nach
dem bereits garantierten Maximieren ausgeblendet werden.

### Sicheres Dateimodell

Android stellt keine frei durchsuchbare Desktop-Platte bereit. Für die
Sideload-ChromeOS-Fassung gibt es deshalb zwei Betriebsarten:

1. Mit bestätigtem `MANAGE_EXTERNAL_STORAGE` wird `/storage/emulated/0` als
   ChromeOS-Play-Dateien-Wurzel verwendet. `Disk Op.` startet in deren Ordner
   `FT II`, kann aber per `..` etwa `Download` oder `Music` erreichen.
2. Ohne Sonderfreigabe bleibt `files/workspace` als isolierter Fallback aktiv.
3. Ein `DocumentsProvider` zeigt den jeweils aktiven FT-II-Ordner im ChromeOS-
   Dateisystem an und erlaubt Anlegen, Lesen, Schreiben, Umbenennen und Löschen.
4. `ACTION_VIEW`- und `ACTION_SEND`-URIs werden über den ContentResolver in den
   Workspace kopiert und anschließend als SDL-Dropfile an FT2 übergeben.

Eine vorgeschaltete Android-Activity erklärt die Freigabe, öffnet die dafür
vorgesehene Systemeinstellung und migriert vorhandene private Workspace-Dateien
verlustfrei in `Play-Dateien/FT II`. Die App funktioniert bei Ablehnung weiter.
Der Sonderzugriff ist für Sideloading gedacht und müsste vor einer Play-Store-
Veröffentlichung durch einen SAF-basierten Import-/Export-Workflow ersetzt oder
als zulässiger Kernzweck von Google genehmigt werden.

Die rekursive Löschfunktion in `ft2_diskop.c` verwendete BSD `fts`, das im
Android-NDK fehlt. Sie wurde durch eine auf `dirent`, `lstat`, `unlink` und
`rmdir` basierende Implementierung ersetzt. Symbolische Links werden dabei
nicht verfolgt.

### Dateinamen und CP850

FT2 arbeitet intern teilweise mit Codepage 850, während Android-Dateipfade
UTF-8 sind. Androids Bionic stellt kein POSIX-`iconv` bereit. Der Android-Zweig
enthält daher eine kleine, feste CP850-Tabelle sowie validierte UTF-8-
Dekodierung und -Kodierung. Nicht in CP850 darstellbare Zeichen werden wie im
historischen Modell auf `?` abgebildet.

### Konfiguration

Die Konfigurationsdatei wird über `SDL_GetPrefPath("AST", "FT2Clone")` im
privaten App-Bereich gespeichert. Nutzdaten liegen getrennt im sichtbaren
Workspace. Dadurch sind Konfiguration und Dokumente eindeutig getrennt.

## Nicht enthaltene Desktop-Komponenten

### MIDI

Der Desktop-Build verwendet RtMidi und je nach Betriebssystem ALSA,
CoreMIDI oder WinMM. Eine belastbare Android-Lösung müsste Android MIDI API,
USB-Berechtigungen, Hotplug und den Activity-Lebenszyklus abbilden. Das wäre ein
eigenes Teilprojekt und wurde für die erste, stabile ChromeOS-Fassung nicht
halbfertig eingebaut. FT2 kompiliert ohne `HAS_MIDI`; das Tracker- und
Wiedergabeverhalten bleibt erhalten.

### Audioaufnahme

SDLs Aufnahmebackend ist vorhanden, aber das Manifest fordert absichtlich kein
`RECORD_AUDIO` an und es gibt noch keinen Laufzeit-Berechtigungsdialog. Die
Sample-Aufnahme ist damit kein freigegebenes Feature dieser Fassung. Laden,
Bearbeiten und Speichern vorhandener Samples bleibt davon unberührt.

## Erwartetes ChromeOS-Nutzererlebnis

Das Bedienmodell passt grundsätzlich gut zu ChromeOS: FT2 ist von Haus aus eine
dichte Desktop-Anwendung für Maus und Tastatur. Fenstergrößenänderung,
Multitasking und der Dateien-Workflow sind abgedeckt. Die größten praktischen
Risiken liegen nicht mehr in der Portierbarkeit des Quellcodes, sondern in
gerätespezifischer Audio-Latenz, Funktionstastenbelegung und den jeweiligen
Sideloading-Richtlinien eines Chromebooks.

## Empfohlene Abnahme vor Veröffentlichung

Auf mindestens je einem Intel/AMD- und ARM-Chromebook:

1. Start, Pause/Resume, Fenster maximieren/verkleinern und Vollbild testen.
2. XM/MOD/S3M/STM laden, abspielen, editieren und erneut speichern.
3. WAV/AIFF/FLAC/OGG/MP3 laden und WAV exportieren.
4. Dateien-App, „Öffnen mit“ und „Teilen“ in beide Richtungen prüfen.
5. 44,1/48/96 kHz, Kopfhörerwechsel und Bluetooth-Audio testen.
6. Tracker-Tastatur, Ziffernblock und F-Tasten prüfen.
7. 30 Minuten Wiedergabe auf Dropouts, Suspend-Fehler und Temperatur prüfen.

## Aufwand für die nächsten Ausbaustufen

- Hardware-Smoke-Test und kleine Korrekturen: ungefähr 0,5–1 Tag
- Produktionssignierung, Store-Metadaten, automatisierte CI: ungefähr 1 Tag
- Android-MIDI mit USB/Hotplug: grob 3–7 Tage plus Gerätetests
- freigegebene Sample-Aufnahme mit Berechtigungs-UX: grob 1–3 Tage plus Tests
- echte Touch-optimierte Tracker-Oberfläche: deutlich größerer UI-Umbau
