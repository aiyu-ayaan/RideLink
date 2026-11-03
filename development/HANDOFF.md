# Handoff — 2026-11-03 (day 2, cloud session)

Read this, then `CLAUDE.md`, `PROTOCOL.md`, `development/TODO.md`. Day-1 notes follow below.

## Done today (one commit per stage, `git log --oneline`)

1. Speed byte fixed: `frame[2]` (ParsingMeta is 1-based), test updated.
2. Auto-connect: `RideLinkRoot.AutoConnect` starts `ClusterService` when a device is saved and Bluetooth is
   granted (app start, after pairing, after granting). Ride button is Disconnect / Connect; Disconnect holds
   until the app process restarts (`MainViewModel.userStopped`).
3. Optional permission card: READ_PHONE_STATE (network type), ACCESS_COARSE_LOCATION (signal level, weather,
   place), ANSWER_PHONE_CALLS (scooter buttons). Status frame sends 0 for what is not granted.
4. TFT features, each with a Settings toggle: social counts `5B 9C` (sender-name hack removed), weather + AQI
   `5B 5D` after screen switch `5B 4C 00 78` and temperature in the status frame (Open-Meteo, 15 min, TFT
   models only, skipped while navigating), place name `5B 5C`, music `5B 62/63/60`, call accept/reject from
   `5A 10` byte 12, ride data card from `DisplayTelemetry` (5A 10/11/19).
5. Demo ride now fills every card (weather, music, social counts, ride data) through the real encoder.
6. `fake_cluster.py` decodes `9C`, screen switch, `5D`, `5C`, `60` (generic + U577).
7. Version 0.2.0 (versionCode 2).
8. Follow-up pass: overspeed alert limit (Settings slider) and missed-call badge (older Ntorq, READ_CALL_LOG)
   in the status frame; Vehicle card from 5A 18 + 5A 11 diagnostics (RPM, battery, engine temp, service,
   engine light); Settings shows the model code and software version the display reports (use it to settle
   U577 premium 38 vs U812 39); default screen `5B 4C 00 00` before nav after a weather switch; Console chips
   send each TFT frame on its own.
9. User decisions applied: Settings -> Display text (rider name + custom lines 1/2, sent on connect and with
   "Send to display"); reject-with-SMS texts an editable message (default "Riding now, will call you back.");
   the voice button opens the phone assistant (needs "display over other apps").

10. Analytics tab (`core/analytics`, `ui/analytics`): rides recorded automatically (5 s samples, SQLite), week/month/
    year/all stats with change vs the previous period, distance / hour-of-day / weekday / speed-band charts on
    Canvas, records, ride list and detail sheet (speed, RPM, range), CSV export, labelled sample rides.
    `RideMath` + `SampleRides` are pure and unit-tested (59 JVM tests total).

## Waiting on the user
- Share the decompile folder (`ntorq-app`, or the jadx output) with this session through Remote Control in the
  Claude desktop app (Settings -> Claude Code -> Remote Control). Then port: cricket `5B 81`, football `5B 91`,
  news `5B 96` (needs a data source: free score/news feeds), vehicle settings `5A F1` / calibration `5B 73`
  (only with care, they change scooter settings), image/doc transfer, older-Ntorq music controls `5A 6B`.
- Non-English Maps: needs sample notifications in the rider's language.

## Not verified (cloud session had no Android SDK)

The cloud environment's network policy blocked `dl.google.com` (Google Maven + SDK), so the Android modules
were **never compiled** today, the emulator/screenshots were not run, and there is no
`dist/RideLink-0.2.0-debug.apk`. The container also has no Bluetooth, so `fake_cluster.py` was only checked
by feeding it the exact frames the unit tests pin.
What was verified: all pure-Kotlin code (`protocol/`, `nav/`, `notify/`) compiled and its 34 unit tests passed
in a plain Kotlin/JVM Gradle project (Kotlin 2.4.10, JUnit 4.13.2) that points at `core/src` (37 tests after the
follow-up pass).

First thing next session, on a machine with the SDK:
1. `./gradlew :core:testDebugUnitTest :app:assembleDebug` and fix any compile errors in the Android code
   (watch: Compose icon names in `RideCards.kt`, `MaterialShapes.Sunny`/`Cookie6Sided`, emphasized type styles,
   `Lifecycle.currentStateFlow`, `LocationManagerCompat.getCurrentLocation`).
2. Console -> Demo ride on the emulator, screenshots of Ride (all cards), Settings (new toggles), Console.
3. Copy the APK to `dist/RideLink-0.2.0-debug.apk`.
4. On the scooter: check weather screen switch does not stick over navigation, raw units of odometer/trip/fuel/
   range/mileage, and that byte 12 really changes when the switch is pressed while ringing.

---

# Handoff — day 1

## Where things stand

- The user confirmed the app **works on their 2026 Ntorq XT (TFT)**: it pairs, connects and the
  display shows our data.
- Last test APK: `dist/RideLink-0.1.0-debug.apk` (debug-signed; `dist/` is git-ignored).
- All work is committed on `main` (`git log --oneline`).

## User requests not done yet (do these next, in this order)

1. **Bug: speed reads the wrong byte.** `ClusterProtocol.speedKmh` uses `frame[3]`.
   Stock `@ParsingMeta(start = N)` is **1-based** (`DataParser.parseData` does `start - 1`), so
   speed is `frame[2]`. Fix the function and `speedFromSpeedometerFrame` test.
2. **Speed and display data without pressing "Start ride".** Once paired, connect automatically:
   start `ClusterService` on app launch when a device is saved and Bluetooth permission is granted,
   right after pairing, and on boot (already done). The ride button becomes "Disconnect".
3. **Signal / network on the display ("notification and signal values properly").**
   - Network type needs `READ_PHONE_STATE` (`TelephonyManager.dataNetworkType`); without it we send 0.
   - Stock reads `getSignalStrength()` only when location permission is granted; ask for
     `ACCESS_COARSE_LOCATION` (also needed for weather).
   - Status frame layout is already correct (signal in the high nibble, battery level in the low one).
4. **Port the TFT features** (all recovered, layouts below):
   - Social notification counts (WhatsApp, Facebook, Instagram), replacing the "message sender" hack.
   - Weather + AQI screen and current location name.
   - Music: song, artist, play state.
   - Call accept/reject from the scooter buttons.
   - Ride data from the display: odometer, trip, fuel, range, average speed, top speed,
     fuel economy, turn indicators, service reminder.
   - Settings in the app to switch each one on or off.

## TFT feature map (recovered from TVS Connect 8.13.2)

Which code path: 2025+ Ntorq (U812/N251/U458/U377) uses `JupiterMobileToCluster` +
`JupiterClusterDataReceiver`; older TFT XT (U577 premium) uses `NtorqMobileToCluster`.
All frames are 20 bytes, end with `FF`, written on `5352` with response.

### Phone -> display
| Feature | Frame | Layout | Source |
|---|---|---|---|
| Social counts | `5B 9C` | WA byte, FB byte, IG count, zero pad, FF. WA = (msgs & 0x1F) << 3 or (chats & 0x07). FB = (msgs & 0x0F) << 4 or (chats & 0x0F) (stock passes msgs for both). Telegram is counted but not sent | `BleWriteSocialMediaNotificationDataSrcImpl`, `BluetoothUtil.getSocialMediaDataToWriteToCluster`, DID `SampleGattAttributes.DATA_ID_MESSAGE5 = -100` |
| WhatsApp count parsing | — | Summary notification title "WhatsApp": text "N messages from M chats". Else text "^(\d+) new messages$" -> N msgs, 1 chat. Else 1/1. Only when `sbn.tag == null` | `NotificationListenerService` (package switch, ~line 740) |
| Screen switch | `5B 4C 00 <id>` + 15 zero + FF | id: default 0x00, weather 0x78, cricket 0x79, football 0x80 | `BluetoothUtil.createTrafficSequenceCustomFrame`, `TrafficSequence` |
| Weather | `5B 5D` | icon, AQI (2 bytes BE, 0..65535), rain % (1), short description UTF-8 up to 13 bytes, pad with spaces (0x20), FF. Send screen switch 0x78 first | `BluetoothUtil.createClimateData` |
| Weather icon (2025+) | — | clouds/cloud/cloudy 1, clear/sunny 2, thunderstorm 3, snow 4, rain/rainy 5, sunrise 6, sunset 7, rain date 8, fog/foggy 9, haze 10, night 11, rainbow 15, hailstorm 16, high/high tide 17, low/low tide 18, weather alert 20, else 12 | `getWeatherIcon` |
| Weather icon (U577 premium) | — | clouds 1, clear sky 2, thunderstorm 3, snow 4, rain 5, fog 9, else 0 | `getWeatherIconForPremium` |
| Temperature | status frame byte | Jupiter family: temp + 40 | `sendJupiterMobileData` |
| Current location | `5B 5C` | place name UTF-8 up to 17 bytes, zero pad, FF | `BluetoothUtil.createCurrentLocationArray`, used by `BleEngine.sendAddressDataToCluster` |
| Song | `5B 62` | text frame (17 bytes) | `BlePacketsProvider.returnMusicPacket(name, 98)` |
| Artist | `5B 63` (Raider `5B 65`) | text frame | `BleMusicDataSourceImpl` |
| Play state (generic) | `5B 60 00 <state> 00 00 00 <volume%>` pad FF | state: Pause 1, Stop 4, else 0 | `returnPlayPauseState` |
| Play state (U577) | `5B 60 00 <state> 01 <curMin> <curSec> <vol> 00 00 00 00 <totMin> <totSec>` pad FF | Stop 254 | `returnPlayPauseStateU577` |
| Cricket / football / news | `5B 81` / `5B 91` / `5B 96` | needs TVS's own score/news API; **not portable** without a data source | `getCricketData` etc. |

Do not port (unclear or risky): vehicle control `5A F1` (backlight, TSL), calibration `5B 73`
(follow-me headlamp timer, idle start-stop timer). They change scooter settings and the decompiled
branches are garbled. Image/doc transfer (profile picture, DigiDoc, Wi-Fi mirroring) is large and TFT-model specific; later.

### Display -> phone (notify on `5354`)
`@ParsingMeta` is 1-based. Listed here as **0-based index** into the notified frame.

| Frame | Field (0-based index) |
|---|---|
| `5A 10` speedometer 1 | speed [2], odometer [3..5] BE, fuel level [6], average speed [7], trip status [8], top speed [9], throttle [10], backlight [11], **switch/call status [12]**, 0-60 time [13], trip meter [14..16], RPM [17..18] |
| `5A 11` speedometer 2 | fuel sensor fail [2], vehicle state [3], service reminder [4], vehicle state 1 [6], software version [7], MIL code [8], **vehicle model [9]** (try for Auto detection), diagnostics [10], ISG code [11], turn indicators [12], connector [13], screenshot [17] |
| `5A 19` speedometer 3 | ISS duration [2..4], OTA status [5], avg fuel economy [8], instant fuel economy [9], distance to empty [11..12], ISS count [15..17] |
| `5A 18` speedometer 4 | engine load [2], MAP [5], intake temp [7], engine temp [8], battery voltage [11], engine run time [12..13], distance [14..15], injection volume [16..17] |

Switch/call status byte [12] (`TelephonyUtils.handleCallAcceptRejectAction`): 1 accept, 2 reject,
3 reject with SMS, 4 voice assistant. 64 = ignition change, 128 = vehicle off.
Accepting/rejecting needs `ANSWER_PHONE_CALLS` + `TelecomManager.acceptRingingCall()/endCall()`.
Scaling of odometer/trip/fuel is not applied in the stock parser; show raw values and confirm on the scooter.

## How testing was done without the scooter

1. **Unit tests** (`./gradlew :core:testDebugUnitTest`): the encoder is checked byte-for-byte
   against layouts taken from the decompiled stock app.
2. **Fake display on this PC** (`tools/fake_cluster.py`): BlueZ GATT server on `hci0` advertising
   as `TVSBT01` with the real service/characteristic UUIDs. It logs and decodes every write to
   `captures/` and can notify frames typed as hex on stdin (e.g. a `5A 10` speed frame).
3. **Two-adapter end-to-end test** (`tools/central_test.py` on `hci1`): plays the phone, finds
   the fake by service UUID, connects, subscribes and writes the pinned frames. Result: PASS.
   Note: `bluetoothctl show` piped through stdin reports the wrong adapter; check
   `ActiveInstances` over D-Bus instead.
4. **Emulator UI + Demo ride**: the Android emulator has no host Bluetooth, so the UI is tested with
   Console -> Demo ride (fake route, speed, call through the real encoder) and adb screenshots
   (`adb exec-out screencap -p -d <display id>`; the emulator has two displays).
   Permissions for screenshots: `pm grant` BLUETOOTH_SCAN/CONNECT/POST_NOTIFICATIONS and
   `cmd notification allow_listener com.aiyu.ridelink.debug/com.aiyu.ridelink.service.RideNotificationListener`.
5. **Real scooter**: install `dist/*.apk`, pair, use the Console tab to see TX/RX frames.

## Regenerating the decompiled source

The decompile lives in a temporary scratchpad and may be gone. To recreate:
```
mkdir -p /tmp/rl && cd /tmp/rl && unzip -o -q /media/aiyu/550E85595197DBEC/development/ntorq-app/TVS-Connect_8.13.2_apkcube.apks base.apk
JADX_OPTS="-Xmx6g" /media/aiyu/550E85595197DBEC/development/ntorq-app/jadx/bin/jadx --no-res -j 8 --show-bad-code -d /tmp/rl/src base.apk
```
Key files: `com/tvs/ble/feature/{handler/BleEngine,datasender/JupiterMobileToCluster,datareceiver/JupiterClusterDataReceiver,services/NotificationListenerService,framework/BleWriteSocialMediaNotificationDataSrcImpl}`,
`com/tvs/bluetooth/core/utils/{BlePacketsProvider,BluetoothUtil}`, `com/tvs/bike/core/protocol/jupiter/*`.
