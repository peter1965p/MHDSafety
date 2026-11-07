# MHD Safety 🐧

Android-App, die Lebensmittel und ihre Haltbarkeitsdaten im Blick behält – damit der Zettel am Kühlschrank in Rente gehen kann.

## Was die App kann

- **Erfassen von Hand:** Name und Datum einfach eintippen, das Datum auch kurz als `0911` oder `9.11.` (ohne Jahr nimmt die App das nächste passende)
- **Kamera nur bei Bedarf:** Über die kleinen Symbole im Feld den Barcode scannen (Name und Kategorie kommen von [Open Food Facts](https://world.openfoodfacts.org)) oder das Datum von der Packung ablesen
- **Liste nach Supermarkt-Kategorien** oder nach Datum, mit Ampelfarben
- **Alarm mit Puffer:** Bei „Mindestens haltbar bis“ kommt der Alarm erst ein paar Tage später (einstellbar, Standard 3). Bei „Zu verbrauchen bis“ (Fleisch, Fisch …) gibt es **keinen** Puffer
- **Eigenes Verbrauchsdatum:** Zusätzlich zum Packungsdatum lässt sich ein eigenes „selbst verbrauchen bis“ eintragen, z. B. wenn die Packung offen ist. Beispiel: MHD 09.11., selbst verbrauchen bis 08.11. → Alarm am 08.11. Das eigene Datum darf beliebig früher liegen und ersetzt dann Packungsdatum und Puffer. Später als ein aufgedrucktes „Zu verbrauchen bis“ geht nicht, dann zählt das Packungsdatum
- **Tägliche Erinnerung** mit Vorwarnung einen Tag vorher
- **Räumt selbst auf:** Abgelaufene Einträge werden automatisch gelöscht, standardmäßig 7 Tage nach dem Alarm (einstellbar von 0 bis 30 Tage). Bis dahin stehen sie rot als „abgelaufen“ in der Liste
- **Updates automatisch** über die Releases dieses Repos
- Alle Daten bleiben auf dem Handy, kein Konto nötig

## Installieren

1. Unter [Releases](../../releases/latest) die Datei `MHDSafety-x.y.z.apk` am Handy herunterladen
2. Antippen und installieren (einmalig „Installation aus unbekannten Quellen“ erlauben)

Neue Versionen meldet die App von selbst.

## Entwickeln

- Android Studio, Kotlin, Jetpack Compose, Room, WorkManager, CameraX, ML Kit
- Tests: `./gradlew testDebugUnitTest`
- Die Debug-Version (`com.paeffgen.mhdsafety.debug`) lässt sich neben der echten App installieren

### Neue Version veröffentlichen

1. In `CHANGELOG.md` einen Abschnitt `## [1.1.0]` mit den Änderungen anlegen und committen
2. `scripts/release.sh 1.1.0`

Das Skript setzt die Version, testet, baut die signierte APK (auch nach `releases/`), taggt, pusht und legt das GitHub-Release an.

Die Release-APK wird mit einem eigenen Schlüssel signiert, der **nicht** im Repo liegt. Gradle erwartet in der `gradle.properties` im Gradle-Home:

```properties
MHD_KEYSTORE_FILE=/pfad/zu/mhdsafety-release.jks
MHD_KEYSTORE_PASSWORD=...
MHD_KEY_ALIAS=mhdsafety
```

Geht der Schlüssel verloren, können installierte Apps keine Updates mehr bekommen – also gut sichern.

## Danke

- Produktdaten: [Open Food Facts](https://world.openfoodfacts.org), Lizenz ODbL
- Tux: Larry Ewing, erstellt mit The GIMP; Hintergrundbild mit Gemini erstellt
