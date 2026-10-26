# Architektur

TG-Finance ist eine Offline-first-Finanz-App. Die lokale Room-Datenbank ist die einzige Quelle
der Wahrheit; der optionale, selbst gehostete Server dient nur dem Abgleich zwischen Geräten.

## Module

| Modul    | Inhalt | Abhängigkeiten |
|----------|--------|----------------|
| `:core`  | Reines Kotlin/JVM: Domänenmodelle, Geldbeträge (Cents als `Long`), Zeitraum- und Wiederholungsberechnung, CSV-Export, Sync-Protokoll, Sync-Engine, Ende-zu-Ende-Verschlüsselung | nur kotlinx (Coroutines, Serialization) |
| `:app`   | Android-App (Compose, Material 3, MVVM, Room, Hilt, WorkManager, DataStore) | `:core` |
| `server` | Eigenständiger Gradle-Build: Ktor-Backend (REST, JWT, Exposed, PostgreSQL/SQLite) | `:core` (gemeinsames Protokoll und gleiche Last-write-wins-Regel) |

`:core` hat keine Android-Abhängigkeiten. Dadurch ist die Logik schnell auf der JVM testbar, und
Client und Server benutzen exakt dieselben Protokollklassen und dieselbe Konfliktregel.

## Schichten in `:app` (MVVM)

```
UI (Compose Screens)  ──>  ViewModel (StateFlow<UiState>)  ──>  Repository (Interface)
                                                                  │
                                                     Room DAO / DataStore / Sync
```

* **Screens** sind zustandslos (`XxxScreen(state, onAction)`), damit jede Ansicht eine `@Preview` hat.
  Eine dünne `XxxRoute` holt das ViewModel per `hiltViewModel()` und sammelt den State.
* **ViewModels** kombinieren Repository-Flows zu einem unveränderlichen `UiState`.
* **Repositories** sind Interfaces (für Fakes in ViewModel-Tests) mit Room-Implementierung.
  Sie arbeiten mit den Domänenmodellen aus `:core`; Entities bleiben in der Datenschicht.

## Ordnerstruktur

```
TG-Finance/
├── core/src/main/kotlin/com/tgfinance/core/
│   ├── model/        Domänenmodelle (Account, Transaction, Budget, …)
│   ├── money/        MoneyFormatter, AmountParser
│   ├── time/         PeriodCalculator (Monatsbeginn, Wochenstart), Recurrence
│   ├── csv/          CSV-Writer und Buchungs-Export
│   └── sync/         Protokoll, SyncEngine, LastWriteWins, PayloadCipher (E2E)
├── app/src/main/java/com/tgfinance/app/
│   ├── data/
│   │   ├── local/        Room: Database, Entities, DAOs, Converter, Standarddaten
│   │   ├── repository/   Repository-Interfaces + Implementierungen
│   │   ├── settings/     DataStore-Einstellungen
│   │   ├── backup/       JSON-Backup, CSV-Export
│   │   └── sync/         Room-LocalSyncStore, HTTP-Client, Token-Speicher (Keystore), SyncManager
│   ├── di/               Hilt-Module
│   ├── work/             WorkManager: wiederkehrende Buchungen, Hintergrund-Sync
│   ├── ui/
│   │   ├── theme/        FinanceColors (zentrale Farben), Theme, Typografie, Formen
│   │   ├── components/   Karten, Betragsanzeige, Icons, Diagramme (Canvas)
│   │   ├── navigation/   Ziele, NavHost, adaptives Gerüst (Bottom Bar / Navigation Rail)
│   │   └── <feature>/    dashboard, transactions, accounts, categories, budgets,
│   │                     statistics, recurring, goals, settings, backup, sync, lock
│   └── MainActivity.kt, TgFinanceApp.kt
├── server/               Ktor-Backend, Dockerfile, docker-compose.yml, Caddyfile
└── docs/                 Dieses Dokument
```

## Daten & Sync-Felder

Jede synchronisierte Tabelle (Konten, Kategorien, Buchungen, Budgets, wiederkehrende Buchungen,
Sparziele) hat:

| Feld        | Zweck |
|-------------|-------|
| `id`        | UUID (String), auf dem Gerät erzeugt |
| `updatedAt` | Zeitpunkt der letzten Änderung (ms), Basis für Last-write-wins |
| `deleted`   | Soft Delete; gelöschte Zeilen bleiben als „Grabstein“ für den Sync erhalten |
| `deviceId`  | Gerät, das die letzte Änderung gemacht hat (Tie-Breaker bei gleichem Zeitstempel) |
| `dirty`     | Nur lokal: Änderung ist noch nicht zum Server übertragen |

Standardkategorien und -konten haben feste IDs (`default-…`), damit zwei frisch installierte
Geräte beim ersten Sync keine Duplikate erzeugen.

Beträge werden als ganzzahlige Cents (`Long`) gespeichert – keine Rundungsfehler durch `Double`.

## Sync-Ablauf

1. **Pull:** `GET /api/v1/sync/pull?since=<cursor>` liefert alle Datensätze, die sich seit dem
   Server-Cursor geändert haben (seitenweise). Der Cursor ist eine Server-Sequenznummer, damit
   Uhrabweichungen zwischen Geräten keine Änderungen „verstecken“.
2. Pro Datensatz: unbekannt → übernehmen; lokal unverändert → neuere Version übernehmen;
   lokal ebenfalls geändert → **Konflikt**, Last-write-wins entscheidet, Konflikt wird geloggt.
3. **Push:** `POST /api/v1/sync/push` mit allen `dirty`-Datensätzen. Der Server wendet dieselbe
   Regel an; abgelehnte Datensätze kommen mit der Server-Version zurück und werden lokal übernommen.
4. Mit Ende-zu-Ende-Verschlüsselung wird nur `payload` (AES-256-GCM, Schlüssel per PBKDF2 aus
   einem Passwort abgeleitet) verschlüsselt; Metadaten für die Konfliktlösung bleiben lesbar.
