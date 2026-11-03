# Architecture

## Modules

```mermaid
graph LR
    app[":app<br/>MainActivity, ClusterService,<br/>RideNotificationListener, BootReceiver"]
    ui[":ui<br/>Compose screens, theme, strings,<br/>MainViewModel"]
    core[":core<br/>protocol, ble, ClusterController,<br/>nav parser, settings"]
    app --> ui
    app --> core
    ui --> core
```

## Ride data flow

```mermaid
sequenceDiagram
    participant Maps as Google Maps / Dialer notifications
    participant L as RideNotificationListener
    participant C as ClusterController (singleton)
    participant B as ClusterLink (GATT)
    participant D as Scooter display

    Note over C,B: ClusterService.start() -> C.start() -> B.connect(saved address)
    B->>D: connect, MTU 512, enable notify 5354
    B-->>C: LinkState.Ready
    C->>B: rider name 5B 52
    loop every 2 s
        C->>B: status 5B 4A (time, battery, signal, call state)
    end
    Maps->>L: notification posted
    L->>C: onNavigation(NavStep) / onIncomingCall(name)
    C->>B: 5B 4F text, 400 ms, 5A 4E control
    C->>B: 5B 43 caller every 500 ms while ringing
    D-->>B: notify frames (5A 10 speed, 5A 6B music, ...)
    B-->>C: incoming -> log, Auto model detection, speed
    B->>D: write with response (5352)
```

## State ownership

| State | Owner | Exposed as |
|---|---|---|
| Saved device, model (null = Auto), rider name, toggles | `SettingsRepository` (DataStore) | `Flow<Settings>` |
| Connection | `ClusterLink` | `StateFlow<LinkState>` |
| Current nav step, caller, speed, detected model, frame log, demo | `ClusterController` | `StateFlow`s |
| Ride session lifetime | `ClusterService` (foreground, connectedDevice) | notification |

`ClusterLink` and `ClusterController` are Hilt singletons, so the service, the notification
listener and the UI all share one session.
