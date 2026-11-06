# Architecture

## Modules

```mermaid
graph LR
    app[":app<br/>MainActivity, ClusterService,<br/>RideNotificationListener, BootReceiver"]
    ui[":ui<br/>Compose screens, theme, strings,<br/>MainViewModel"]
    core[":core<br/>protocol, ble, ClusterController,<br/>nav + social parsers, weather, phone, settings,<br/>care (fuel log, service), check-in"]
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
    Note over C,B: ClusterService holds a partial wake lock while Ready, so loops run with the screen off
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
    L->>L: Maps turn = large icon matched against Maps' own maneuver drawables (MapsTurnIcons)
    L->>C: onNavigation / onIncomingCall / onSocialCounts / onMusic
    C->>B: 5B 4F text, 400 ms, 5A 4E control
    loop every 2 s while a route is active (stock NavigationRepoImpl timer)
        C->>B: repeat last 5B 4F + 5A 4E
    end
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
    N[Google Maps notification] --> CN[ClusterController.nav]
    C --> R[RideRecorder<br/>every 5 s while connected or demo]
    CN -->|distance left + new instructions| R
    R -->|ride ends: link drop, demo off, 10 min still| M[RideMath.summarize]
    M --> S[(RideStore v3<br/>SQLite rides + samples + turns,<br/>each ride tagged with its scooter)]
    S --> V[AnalyticsViewModel<br/>one scooter at a time,<br/>RideMath.stats per period]
    V --> UI[AnalyticsScreen]
    UI -->|ride:id page| RD[RideDetailScreen<br/>source, stats, charts, turns]
```

`RideRecorder` is started from `RideLinkApp.onCreate` and lives for the process. Distance per interval:
Google Maps route progress (drop in distance-left) while navigating; the rest from the odometer (0.1 km) when it
agrees with integrated speed within 2x, otherwise integrated speed. The split is stored per ride (`maps_m`,
`odo_m`, `speed_m`).

## Fuel, service and safety

```mermaid
graph LR
    T[ClusterController.telemetry<br/>5A 10 odometer + fuel bars, 5A 19 range] --> M[CareMonitor<br/>while Ready, not demo]
    M -->|RefuelDetector: +2 bars held 10 s, standing| F[(CareStore care.db<br/>fills, items, readings per bike)]
    M -->|LowFuelWatch: range under limit or 1 bar, 30 s| E[events]
    M -->|once per connect: CareMath.status SOON/OVERDUE| X[ClusterController.flashText<br/>custom line 1 for 10 s]
    M --> E
    E --> CN[app CareNotifier<br/>notifications, Maps petrol pump search]
    F --> VM[CareViewModel<br/>CareMath.spans full-to-full mileage] --> CS[CareScreen + Ride CareCard]
    RR[RideRecorder.recording] --> CI[RideCheckIn<br/>SMS start + end with Maps link]
    WA[WhatsApp notification<br/>RemoteInput reply action] --> L[RideNotificationListener] -->|onChatMessage: riding, once per chat| C[ClusterController]
    LD[link drop at speed 0] --> P[parked spot, precise when granted]
```

`ClusterService` runs as `connectedDevice|location` when location is granted (falls back to `connectedDevice`
when started from boot), so the parked spot and check-in link get a fix with the screen off.
Console -> Feature tests runs each of these paths without a scooter (Pretend riding enables WhatsApp replies).

## App shell and updates

```mermaid
graph TD
    Root[RideLinkRoot] --> Tabs[Ride / Analytics / Settings<br/>floating pill, hides on scroll]
    Root --> Stack[page stack: settings:SECTION, ride:ID, Console, Updates, About, Acknowledgements, Care<br/>SeekableTransitionState + PredictiveBackHandler]
    Root --> Host[UpdateHost sheet]
    Host --> U[AppUpdater: GitHub releases API -> RideLink-version.apk -> PackageInstaller session]
    U --> IR[UpdateInstallReceiver: confirm / failure reason]
    Settings[Settings -> Developer options] -->|developer mode| Console[Console page]
    Console --> LF[LogFile: files/logs, 7 days, export via SAF]
```

## State ownership

| State | Owner | Exposed as |
|---|---|---|
| Active device, its model (null = Auto), rider name, toggles | `SettingsRepository` (DataStore) | `Flow<Settings>` |
| Every paired scooter with its own model | `GarageStore` (SQLite `garage.db`) | `StateFlow<List<Bike>>` |
| Connection | `ClusterLink` | `StateFlow<LinkState>` |
| Current nav step, caller, speed, telemetry, weather, place, music, social counts, detected model, frame log, demo | `ClusterController` | `StateFlow`s |
| Ride history (summaries + 5 s samples + Maps turns, per scooter) | `RideStore` (SQLite `rides.db`, v3) | `StateFlow<List<RideRecord>>` |
| Update check / download / install | `AppUpdater` | `StateFlow<UpdateState>` |
| Saved Console log | `LogFile` (`files/logs`) | export to a file the rider picks |
| Ride session lifetime | `ClusterService` (foreground, connectedDevice) | notification |

`ClusterLink` and `ClusterController` are Hilt singletons, so the service, the notification
listener and the UI all share one session.
