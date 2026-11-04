# Architecture

## Modules

```mermaid
graph LR
    app[":app<br/>MainActivity, ClusterService,<br/>RideNotificationListener, BootReceiver"]
    ui[":ui<br/>Compose screens, theme, strings,<br/>MainViewModel"]
    core[":core<br/>protocol, ble, ClusterController,<br/>nav + social parsers, weather, phone, settings"]
    app --> ui
    app --> core
    ui --> core
```

## Ride data flow

```mermaid
sequenceDiagram
    participant Maps as Google Maps / Dialer / chat notifications, media sessions
    participant L as RideNotificationListener + MusicWatcher
    participant C as ClusterController (singleton)
    participant W as WeatherRepository (Open-Meteo, coarse location)
    participant B as ClusterLink (GATT)
    participant D as Scooter display

    Note over C,B: auto-connect when paired: ClusterService.start() -> C.start() -> B.connect(saved address)
    B->>D: connect, MTU 512, enable notify 5354
    B-->>C: LinkState.Ready
    C->>B: rider name 5B 52, social counts 5B 9C, song/artist/state 5B 62/63/60
    loop every 2 s
        C->>B: status 5B 4A (time, battery, signal, network, temperature, call state)
    end
    loop every 15 min (TFT models)
        W-->>C: weather, AQI, place name
        C->>B: screen 5B 4C 00 78, weather 5B 5D, location 5B 5C
    end
    Maps->>L: notification posted
    L->>C: onNavigation / onIncomingCall / onSocialCounts / onMusic
    C->>B: 5B 4F text, 400 ms, 5A 4E control
    C->>B: 5B 43 caller every 500 ms while ringing
    D-->>B: notify frames (5A 10 / 11 / 19 ride data, 5A 6B music, ...)
    B-->>C: incoming -> log, Auto model detection, DisplayTelemetry
    C->>C: 5A 10 [12] switch: accept / reject call via CallControl (TelecomManager)
    B->>D: write with response (5352)
```

## Ride analytics

```mermaid
graph LR
    D[Display 5A 10/11/18/19] --> C[ClusterController.telemetry]
    C --> R[RideRecorder<br/>every 5 s while connected or demo]
    R -->|ride ends: link drop, demo off, 10 min still| M[RideMath.summarize]
    M --> S[(RideStore<br/>SQLite rides + samples)]
    S --> V[AnalyticsViewModel<br/>RideMath.stats per period]
    V --> UI[AnalyticsScreen + RideDetailSheet]
```

`RideRecorder` is started from `RideLinkApp.onCreate` and lives for the process. Odometer distance (0.1 km)
is used when it agrees with integrated speed within 2x, otherwise integrated speed.

## State ownership

| State | Owner | Exposed as |
|---|---|---|
| Active device, its model (null = Auto), rider name, toggles | `SettingsRepository` (DataStore) | `Flow<Settings>` |
| Every paired scooter with its own model | `GarageStore` (SQLite `garage.db`) | `StateFlow<List<Bike>>` |
| Connection | `ClusterLink` | `StateFlow<LinkState>` |
| Current nav step, caller, speed, telemetry, weather, place, music, social counts, detected model, frame log, demo | `ClusterController` | `StateFlow`s |
| Ride history (summaries + 5 s samples) | `RideStore` (SQLite `rides.db`) | `StateFlow<List<RideRecord>>` |
| Ride session lifetime | `ClusterService` (foreground, connectedDevice) | notification |

`ClusterLink` and `ClusterController` are Hilt singletons, so the service, the notification
listener and the UI all share one session.
