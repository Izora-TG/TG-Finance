# TG Finance

Finanzen einfach im Blick – eine Android-App (Kotlin, Jetpack Compose, Material 3), die komplett
offline auf dem Gerät funktioniert. Kein Konto, keine Cloud. Optional lassen sich mehrere Geräte
über einen **selbst gehosteten** Server abgleichen, auf Wunsch Ende-zu-Ende-verschlüsselt.

## Features

| Bereich | Was die App kann |
|---|---|
| **Dashboard** | Gesamtsaldo, Einnahmen/Ausgaben/Saldo des aktuellen Monats, Budget-Fortschritt, Top-Kategorien (ausklappbar), letzte Buchungen, Sparziele, Warnbanner bei überschrittenen Budgets, Pull-to-Refresh |
| **Buchungen** | Einnahme, Ausgabe oder Umbuchung in ≤ 3 Taps (Plus → Betrag → Kategorie → Speichern; Konto und Datum sind vorbelegt, meistgenutzte Kategorien zuerst). Bearbeiten, Löschen (Wischen + Rückgängig), Volltextsuche (Notiz, Kategorie, Konto), Filter nach Zeitraum, Typ, Konto, Kategorie |
| **Konten** | Mehrere Konten (Girokonto, Bargeld, Sparkonto, Kreditkarte, Depot …) mit Einzelsalden, Anfangssaldo, Archivieren, Umbuchungen zwischen Konten |
| **Kategorien** | Standardkategorien vorhanden, eigene mit Symbol und Farbe |
| **Budgets** | Monatliches Limit pro Kategorie, Fortschrittsbalken (violett → gelb ab 80 % → rot bei Überschreitung), vergangene Monate ansehen |
| **Wiederkehrend** | Miete, Abos, Gehalt … täglich/wöchentlich/monatlich/jährlich mit Intervall und Enddatum; WorkManager bucht automatisch und holt verpasste Termine nach |
| **Sparziele** | Zielbetrag, optionales Zieldatum (inkl. „x € pro Monat nötig“), Ein-/Auszahlungen |
| **Statistiken** | Kreisdiagramm nach Kategorie, Balkendiagramm Einnahmen/Ausgaben, Verlauf des Gesamtsaldos, Sparquote; Zeitraum Woche/Monat/Quartal/Jahr, blätterbar |
| **Export/Backup** | CSV-Export (Excel-kompatibel, Semikolon, UTF-8), lokales JSON-Backup und Wiederherstellung über den Android-Dateidialog |
| **Einstellungen** | Währung, Wochenbeginn, Monatsbeginn (z. B. Zahltag am 25.), Design (dunkel/hell/System), App-Sperre per Biometrie oder Geräte-PIN |
| **Sync (optional)** | Eigener Server, nur HTTPS, Push/Pull im Hintergrund und per Pull-to-Refresh, Last-write-wins mit Konfliktprotokoll, optionale Ende-zu-Ende-Verschlüsselung, dezente Statusanzeige |

**Design:** dunkles Theme als Standard (Hintergrund `#0F0F14`, Karten `#1A1A23`), violette Akzente
(`#8B5CF6`), Einnahmen dezent grün, Ausgaben dezent rot. Alle Farben zentral in
`app/src/main/java/com/tgfinance/app/ui/theme/FinanceColors.kt`.

**Geräte:** Handy (Bottom Navigation), Tablet/Faltbare/Querformat (Navigation Rail, ab „Expanded“
Zwei-Spalten-Layout: Liste links, Details rechts) über `WindowSizeClass`.

**Sprache:** Deutsch ist die Standardsprache (`res/values/strings.xml`); weitere Sprachen über
`values-xx/strings.xml`.

## Architektur

MVVM mit Hilt, Room, Navigation Compose (typsicher), WorkManager und DataStore. Die reine Logik
(Geld, Zeiträume, Wiederholungen, CSV, Sync-Engine, Verschlüsselung) liegt im
plattformunabhängigen Modul `:core`, das sich App und Server teilen. Details und Ordnerstruktur:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

```
core/     Reines Kotlin: Domänenmodelle, Logik, Sync-Protokoll und -Engine
app/      Android-App
server/   Ktor-Sync-Server (eigener Gradle-Build) + Docker-Deployment
```

## App bauen

Voraussetzungen: JDK 17+ und Android SDK (Platform 35). Am einfachsten Android Studio
(Ladybug oder neuer) öffnen und das Projekt importieren.

```bash
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # auf verbundenes Gerät/Emulator installieren
./gradlew test                   # alle Unit-Tests (core + app)
./gradlew :core:test             # nur die plattformunabhängige Logik
```

- Min SDK 26, Target/Compile SDK 35, Kotlin 2.1, AGP 8.7, Gradle 8.14 (Wrapper)
- Versionen zentral im Version Catalog `gradle/libs.versions.toml`
- Room-Schemas werden nach `app/schemas/` exportiert (für spätere Migrationen)
- Für jeden Screen gibt es `@Preview`-Funktionen (Handy, Tablet, Leerzustände)

### Tests

| Modul | Was getestet wird |
|---|---|
| `core` | Zeiträume/Monatsbeginn, Wiederholungen (31., Schaltjahr), Betragsparser, CSV, Sync-Engine inkl. Konfliktfällen, Paging, Soft Delete, E2E-Verschlüsselung, Server-URL-Prüfung |
| `app` | Repositories gegen In-Memory-Room (Robolectric), Backup/Restore, Standarddaten, Room-Sync-Store inkl. Sync zweier Datenbanken mit Konflikt, HTTP-Client (MockWebServer), ViewModels mit Fakes |
| `server` | Registrierung/Login/JWT, Rate-Limiting, Benutzertrennung, Push/Pull/Paging, Last-write-wins, Argon2id, End-to-End-Sync zweier Geräte über den echten Server |

## Sync-Server selbst hosten

Der Server ist optional. Er speichert pro Benutzer nur Datensätze mit Metadaten
(`type`, `id`, `updatedAt`, `deleted`, `deviceId`) und einem Payload – mit aktivierter
Ende-zu-Ende-Verschlüsselung ist der Payload für den Server nicht lesbar.

### Schnellstart mit Docker Compose (PostgreSQL + Caddy mit automatischem HTTPS)

Voraussetzungen: ein Server mit Docker, eine Domain, deren DNS-Eintrag auf den Server zeigt,
und offene Ports 80/443.

```bash
git clone <dieses Repo> tg-finance && cd tg-finance/server
cp .env.example .env
# .env bearbeiten:
#   DOMAIN=finance.deine-domain.de
#   JWT_SECRET=$(openssl rand -base64 48)
#   POSTGRES_PASSWORD=$(openssl rand -base64 24)
docker compose up -d --build
curl https://finance.deine-domain.de/health    # {"status":"ok"}
```

Caddy holt und erneuert das TLS-Zertifikat automatisch. Der Ktor-Server selbst ist nicht nach
außen veröffentlicht, sondern nur über Caddy erreichbar.

Danach in der App: **Mehr → Synchronisation**, `https://finance.deine-domain.de` eintragen,
Benutzername und Passwort wählen, **Registrieren**. Auf weiteren Geräten **Anmelden**.
Wenn alle Konten angelegt sind, in `.env` `ALLOW_REGISTRATION=false` setzen und
`docker compose up -d` ausführen.

### Ohne Docker / mit SQLite

```bash
export JWT_SECRET=$(openssl rand -base64 48)
export DATABASE_URL=jdbc:sqlite:/var/lib/tgfinance/tgfinance.db
./gradlew -p server installDist
server/build/install/tg-finance-server/bin/tg-finance-server
```

Der Server lauscht dann auf Port 8080 (HTTP) und muss hinter einen Reverse Proxy mit HTTPS
(Caddy, Nginx – Beispiel in `server/nginx.example.conf`) gestellt werden; die App akzeptiert
ausschließlich `https://`-Adressen.

### Konfiguration (Umgebungsvariablen)

| Variable | Standard | Bedeutung |
|---|---|---|
| `JWT_SECRET` | – (Pflicht) | Zufälliger Schlüssel ≥ 32 Zeichen zum Signieren der Tokens |
| `DATABASE_URL` | `jdbc:sqlite:./data/tgfinance.db` | JDBC-URL für PostgreSQL oder SQLite |
| `DATABASE_USER` / `DATABASE_PASSWORD` | – | Zugangsdaten (PostgreSQL) |
| `PORT` | `8080` | HTTP-Port des Servers |
| `ALLOW_REGISTRATION` | `true` | Neue Benutzer erlauben |
| `TOKEN_TTL_HOURS` | `720` | Gültigkeit eines Login-Tokens |
| `TRUST_PROXY` | `false` | `X-Forwarded-For` des Proxys auswerten (nur hinter Proxy aktivieren) |
| `MAX_LOGIN_ATTEMPTS` / `LOGIN_LOCK_MINUTES` | `5` / `15` | Sperre nach Fehlversuchen (pro IP und Benutzername) |
| `JWT_ISSUER` | `tg-finance` | Aussteller im Token |

### REST-API

| Methode & Pfad | Beschreibung |
|---|---|
| `GET /health` | Health-Check |
| `POST /api/v1/auth/register` | `{username, password}` → `{token, expiresAt, username}` |
| `POST /api/v1/auth/login` | wie oben; `429` mit `Retry-After` bei zu vielen Fehlversuchen |
| `GET /api/v1/me` | Benutzername und Salt für die E2E-Schlüsselableitung |
| `GET /api/v1/sync/pull?since=<cursor>&limit=<n>` | Änderungen seit dem Cursor (seitenweise) |
| `POST /api/v1/sync/push` | `{deviceId, records[]}` → `{accepted[], rejected[]}` |

### Wie der Sync funktioniert

- **Offline-first:** Room ist die einzige Quelle der Wahrheit. Jede Änderung setzt `updatedAt`,
  die Geräte-ID und ein lokales `dirty`-Flag; Löschen ist ein Soft Delete (`deleted = true`).
  Alle IDs sind UUIDs; Standardkategorien und wiederkehrende Buchungen haben deterministische IDs,
  damit zwei Geräte keine Duplikate erzeugen.
- **Pull, dann Push:** Der Cursor ist eine Sequenznummer des Servers (nicht die Geräteuhr), damit
  keine Änderungen durch abweichende Uhren verloren gehen.
- **Konflikte:** Last-write-wins pro Datensatz nach `updatedAt`, bei Gleichstand entscheidet die
  Geräte-ID – identisch auf Server und allen Geräten. Konflikte werden in der App
  (Synchronisation → Konflikte) und auf dem Server (Tabelle `conflicts` + Log) protokolliert.
- **Wann:** alle 30 Minuten im Hintergrund (WorkManager, nur mit Netz), beim Öffnen der App und
  per Pull-to-Refresh auf dem Dashboard.
- **Ende-zu-Ende-Verschlüsselung (optional):** AES-256-GCM; der Schlüssel wird per
  PBKDF2-HMAC-SHA256 (310 000 Iterationen) aus einem Verschlüsselungspasswort und einem Salt pro
  Benutzer abgeleitet. Nur der abgeleitete Schlüssel wird gespeichert – verschlüsselt mit einem
  Schlüssel aus dem Android Keystore. Das Passwort selbst wird nirgends gespeichert.
  Ohne dieses Passwort sind die Serverdaten nicht wiederherstellbar.

### Sicherheit

- Passwörter: Argon2id (m = 19 MiB, t = 2, p = 1), PHC-Format, konstante Vergleichszeit
- Login- und Registrierungs-Rate-Limiting pro IP und Benutzername
- JWT (HS256) mit Ablaufzeit; Token in der App mit Android-Keystore-Schlüssel verschlüsselt
- App: kein Klartext-HTTP (Network Security Config), keine Android-Cloud-Backups der Finanzdaten,
  optionale App-Sperre mit `FLAG_SECURE`
- Server loggt keine Request-Inhalte

## Entscheidungen & Standardwerte

- Beträge als ganze Cents (`Long`), keine Gleitkommazahlen
- Eigene Canvas-Diagramme statt Chart-Bibliothek (weniger Abhängigkeiten, passend zum Theme)
- Ein Budget pro Kategorie und Monat; Monatsbeginn konfigurierbar (1.–28.)
- Löschen eines Kontos löscht dessen Buchungen mit (Archivieren als Alternative)
- PBKDF2 statt Argon2 für die E2E-Schlüsselableitung auf dem Gerät, weil es ab API 26 ohne
  native Bibliothek verfügbar ist
