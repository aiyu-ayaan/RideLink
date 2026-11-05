# TVS Ntorq SmartXonnect BLE protocol (recovered)

Source: static decompile of TVS Connect 8.13.2 (`com.tvsm.connect`), APK sha256
`0fd932601293660216b46d786325dc99a66e830e530f1d14f5d0020b24b3fec9`.
Everything below is **static evidence only**. Nothing has been confirmed against a
real scooter yet. Confirm with an HCI snoop log before relying on it.

## GATT

| Item | UUID | Notes |
|---|---|---|
| Service | `5456534D-5647-5341-5342-454E544F5251` | ASCII `TVSMVGSASBENTORQ`. Shared by Ntorq, Jupiter, Raider, Apache configs |
| Write | `00005352-0000-1000-8000-00805f9b34fb` | write type 2 = `WRITE_TYPE_DEFAULT` (with response) |
| Notify/read | `00005354-0000-1000-8000-00805f9b34fb` | cluster -> phone frames |
| CCCD | `00002902-...` | enable notifications |

- Scan filter: advertised name contains `TVS` (case-insensitive) and not `HUD`.
- MTU requested: 512 (141 for iQube U467 family, 515 for U577 base / Jupiter U566).
- Source: `com/tvs/bike/core/bikes/NtorqBLEConnectionConfig`, `JupiterBLEConnectionConfig`,
  `com/tvs/ble/feature/handler/BleEngine` (`onPeripheralScan`, `onConnectedPeripheral`).

## Auth

- **Ntorq/Jupiter family: no auth step found.** `validateUserID` (frame `5B 22 <userId> ... FF`)
  runs only for `BikeType.U388`. `writeChallenge` (AES cipher + `9A F1` frame) is only reached via
  `processVehicleDataFrameType`, which is not the path for ICE Ntorq/Jupiter bikes.
- Bonding: `peripheral.createBond()` exists in `BleEngine`; whether the cluster demands it is unknown.

## Connect sequence (BaseBleConnectHelperService / NtorqBleHelper)

1. Connect GATT, request MTU, enable notify on `5354`.
2. `initializeDeviceServices()`:
   - `sendInitialDataPacket()` -> rider name frame (DID `0x52`), ongoing-ride observer,
     weather/cricket loop.
   - `startSendingCyclicData()` -> `writeCyclicDataPacket()` every **2000 ms**.
3. Incoming frames are parsed only after the first cyclic frame was sent (`isFirstCyclicDataSent`).

## Frame format

Mostly fixed 20-byte frames: `start, DID, payload..., 0xFF`.
Start byte `0x5B` ('[') for most phone->cluster frames, `0x5A` ('Z') for nav-control and
some cluster frames, `0x9A` for the challenge frame (other bikes).

### Text frame (`BluetoothUtil.createMobileToClusterArray(text, did)`)
`5B DID <text up to 17 bytes, UTF-8> <zero pad> FF` (20 bytes).
Characters outside `A-Z a-z 0-9 space` become `X`; emoji become `@`.
RideLink's Settings -> Display text writes the rider name (`0x52`) and custom lines 1 (`0x4C`) and 2 (`0x63`).
The lines are templates (`DisplayTemplate`): `{caller|rider}`, `{time}`, `{battery}`, `{temp}`, `{song}`, `{odo}` and more,
re-rendered every status tick and on call start/end, and written only when the text changes. `0x63` is also the music
artist on Ntorq, so a playing song replaces line 2.

| DID | Hex | Use |
|---|---|---|
| 82 | `0x52` | Rider name (sent on connect) |
| 76 | `0x4C` | Custom text line 1 / nav text / nav assist |
| 99 | `0x63` | Custom text line 2; music artist (old) |
| 97 | `0x61` | Album name |
| 98 | `0x62` | Song name |
| 101 | `0x65` | Artist name (new) |
| 67 | `0x43` | Caller info: `5B 43 <category> <text> ... FF`; category 1=number, 2=name, 3=country |
| 83 | `0x53` | SMS sender |
| 80 | `0x50` | Pictogram description text |

### Cyclic mobile status frame, DID `0x4A` ('J') (`BlePacketsProvider.sendNtorqMobileData`)
Old-Ntorq default branch (U716 etc.), in order:
```
5B 4A
[netStrength nibble | battery level nibble]   hex2decimal("<net><batt>")
overspeed limit (0xFF = off)
temperature (from weather API)
00
hour (12h)  minute  second  AM=1/PM=0
missed-call count
network type
day  month  year%100
00
voice-assist/LCD flag byte
find-my-vehicle flag (0/1)
FF
```
U577 base/premium and Raider use a slightly different tail (call-state byte, padded to 20).

### Cyclic status frame, Jupiter family (`BlePacketsProvider.sendJupiterMobileData`)
Used for U812/N251/U458/U377 (and Jupiter/Zest). Padded to 20 bytes:
```
5B 4A
[netStrength nibble | battery level nibble]
overspeed limit (120 when alert off; Zest 70)
temperature + 40
00
hour (12h)  minute  second  AM=0/PM=1      <- inverted vs the Ntorq family
missed calls (always 0 for new Ntorq)
network type
day  month  year%100
00
voice-assist flag (0 for new Ntorq)
find-my-vehicle flag
00 FF
```
Battery level (`getIceBatteryLevel`): 0-9% 0, 10-19 1, 20-39 2, 40-59 3, 60-79 4, 80-100 5.
Network type (`getNetworkType`, input `TelephonyManager.NETWORK_TYPE_*`): 3G types (2,3,8,10,15,16) -> 3,
LTE (13) -> 4, NR (20) -> 5, else 0. Signal strength is `SignalStrength.getLevel()` (0..4).
RideLink sends the network type only with `READ_PHONE_STATE`. The signal level is read without any permission
(the stock app gates `getSignalStrength()` on location; RideLink dropped that gate after the rider saw no bars), from
the default *data* subscription, taking the best `CellSignalStrength.level` when `getLevel()` reports 0.
Deviation from stock: RideLink also sends the missed-call count in the Jupiter-family frame (stock always sends 0).
Unconfirmed on the rider's display: whether signal bars, the call icon and the notification badge come from this
frame at all; check with Console -> Copy log (decoded `STATUS` lines).
Overspeed limit: Settings -> Overspeed alert (30..120 km/h); off = 120 on the Jupiter family, 0xFF on older Ntorq.
Missed calls (older Ntorq only): new missed calls from `CallLog` (`READ_CALL_LOG`), cached 30 s.

### Incoming call (`TelephonyUtils.writeNtorqCallerInformationToCluster` / `writeJupiterCallerInformationToCluster`)
Text frame DID `0x43` with the contact name (or number with `+91` removed), no category byte.
Repeated every 500 ms while ringing (`BleEngine.processingNtorqIncomingCallDataToCluster`).
SMS senders are only sent to iQube/U388 displays, never to Ntorq.

### Frames from the display (`NtorqFrameIdentifier`, `JupiterFrameIdentifier`)
Keyed on byte 1 after `5A`: `10` speedometer 1, `11` speedometer 2 (software version), `19`, `18`,
`37` calibration response, `54` doc-transfer ack. Older Ntorq only: `6B` music controls, `29` Wi-Fi password.
Speedometer 1 (`@ParsingMeta`, **1-based**: `DataParser.parseData` reads `start - 1`): speed `frame[2]`,
odometer 3 bytes from `frame[3]`, fuel `frame[6]`, RPM 2 bytes from `frame[17]` (0-based indexes).

### Navigation (new Ntorq/Jupiter path: `BleNavigationSendData.byteArrayForIceBikeNavigation`)
Two frames. Sent **second first**, then sleep 400 ms, then first:

Frame A (instruction text): `5B 4F <instruction up to 17 bytes> <pad> FF`

Frame B (nav control, 20 bytes):
```
5A 4E
distToNextManeuver_m   2 bytes BE (FF FF if >= 65535)
etaMinutes             2 bytes BE
totalDistLeft_m        3 bytes BE
clusterPictogramId     1 byte
lines                  1 byte (2 if instruction > 17 chars, else 1)
navOn                  1 byte (0 = running, 0xFF = stopped; Raider "29": 1/0)
00 00 00
00 00 00 00
FF
```
Text source in stock (`NavigationRepoImpl.sendAdviseInfoToCluster`): Mappls `AdviseInfo.getShortText()`
("Turn right"), maneuver `AdviseInfo.getManeuverID()`, distance rounded by `NavigationUtils.getFormattedDistance`
(down to 10 m under 1 km, else 100 m). Short text means stock nearly always sends `lines = 1`.
Deviation: RideLink always sends `lines = 1`. Google Maps text ("Turn right onto MG Road") is over 17 chars,
and with `lines = 2` the 2025+ TFT showed the text but no arrow (rider report, 2026-11-05; Console test turn,
15 chars, showed the arrow). Only 17 bytes of text are sent either way.

Repeat: stock sends the current pair every **2 s** for the whole route (`NavigationRepoImpl.startTimer`,
`scheduleAtFixedRate(..., 0, 2000)`; `onRouteProgress` only stores the latest `AdviseInfo`). Stock sends no
"navigation on" frame for this family (`JupiterMobileToCluster.sendNavigationAssistOn` is empty) and no custom
lines during a route. RideLink sends on every Maps update and repeats the last pair when 2 s pass without one
(`ClusterProtocol.NAV_RESEND_MS`); live custom lines are held back while a route is active.

Turn source in RideLink: Google Maps 26.14 has no direction in the notification text (title "40 m",
text = street, sub text "17 min · 7.8 km · 11:27 PM ETA"); the turn is only in the 138 px large icon, which is
one of Maps' `maneuver_*` vector drawables. `MapsTurnIcons` draws those from the installed Maps app and
`ManeuverIcon` picks the closest shape (exact match, score 1.00, on a real route on 2026-11-05).

Pictogram mapping for NTORQ_U812/N251/U458/U377 (`getPictogramMappingForNtorqU812`),
Mappls maneuver id -> cluster id:
`0>0 1>1 2>2 3>10 4>11 5>12 6>19 7>57 8>22 11>0 12>10 13>0 14>10 15>7 16>17 17>1 18>11 19>7 20>17 21>57 22>0 23>7 24>10 25>17 36>23 41>19 50>24 51>38 52>10 53>36 54>24 55>35 56>0 57>40 65>27 66>28 67>29 68>48 69>31 70>32 71>33 72>61 73>0 74>8 75>18`
Unmapped -> 0.

The keys are Mappls `TurnTypeConstants`: 0 left, 1 sharp left, 2 slight left, 3 right, 4 sharp right,
5 slight right, 6 U-turn, 7 continue, 8 destination, 15 fork left, 16 fork right, 41 right U-turn,
68 roundabout, 72 rotary. So on 2025+ Ntorq: left 0, right 10, slight right 12, U-turn 19,
straight 57, destination 22, roundabout 48.

U577 premium (vehicle 38) uses its own table and the stock app parses the value as **hex**
(`Integer.parseInt(value, 16)`): right "10" -> 0x10. U577 base (37) has a third table, default 5.

## TFT features (2025+ Ntorq `JupiterMobileToCluster`, U577 `NtorqMobileToCluster`)

All 20 bytes, end with `FF`, written on `5352` with response.

### Checksum (`BluetoothUtil.addChecksum`, writes with `isChecksumNeeded = true`)
Some frames are written through `BleEngine.writeCharacteristicToCentral(frame, true)`, which replaces byte 18 with
a checksum: `255 - (unsigned sum of bytes 0..17) mod 256` (`byteArrayToIntArray` drops the last two bytes, `& 0xFF`).
Byte 19 stays `FF`. On the Ntorq paths this covers the **social counts** (`BleWriteSocialMediaNotificationDataSrcImpl`)
and **every music frame** (song, artist, play state; `BleMusicDataSourceImpl`, all models except Raider and Apache).
A text frame loses its 17th character to the checksum, so music text is cut to 16 characters.
Caller (`5B 43`), navigation, status, weather and location frames are written without it.
Before RideLink sent the checksum, the display ignored the counts and music frames (no notification icons).

### Social unread counts, DID `0x9C` (`BleWriteSocialMediaNotificationDataSrcImpl`, `BluetoothUtil.getSocialMediaDataToWriteToCluster`)
```
5B 9C
WA   (msgs & 0x1F) << 3 | (chats & 0x07)
FB   (msgs & 0x0F) << 4 | (chats & 0x0F)      stock passes msgs for both halves
IG   count (0..255)
00 ... 00 FF
```
DID is `SampleGattAttributes.DATA_ID_MESSAGE5 = -100`. Telegram is counted by the stock app but never sent.
WhatsApp counts (`NotificationListenerService`, only when `sbn.tag == null`): summary title `WhatsApp` with
text `N messages from M chats`; else `^(\d+) new messages$` = N messages in 1 chat; else 1/1.
RideLink rebuilds the counts from the active notifications on every post/remove (reading a chat clears it);
Facebook (`com.facebook.katana`, `com.facebook.orca`) and Instagram count visible non-summary notifications.

### Screen switch, DID `0x4C` with a leading `00` (`BluetoothUtil.createTrafficSequenceCustomFrame`, `TrafficSequence`)
`5B 4C 00 <id> <15 x 00> FF`. id: default `0x00`, weather `0x78`, cricket `0x79`, football `0x80`.
Same DID as text line 1; the leading `00` byte tells them apart.

### Weather + AQI, DID `0x5D` (`BluetoothUtil.createClimateData`)
```
5B 5D
icon                 see tables below
AQI                  2 bytes BE (0..65535)
rain chance %        1 byte
description          13 bytes UTF-8, padded with spaces (0x20)
FF
```
Send the screen switch `0x78` first. Icons, 2025+ family (`getWeatherIcon`): clouds/cloud/cloudy 1, clear/sunny 2,
thunderstorm 3, snow 4, rain/rainy 5, sunrise 6, sunset 7, rain date 8, fog/foggy 9, haze 10, night 11, rainbow 15,
hailstorm 16, high tide 17, low tide 18, weather alert 20, else 12. U577 premium (`getWeatherIconForPremium`):
clouds 1, clear sky 2, thunderstorm 3, snow 4, rain 5, fog 9, else 0.
Temperature goes in the status frame (Jupiter family: `temp + 40`).
RideLink source: Open-Meteo `current=temperature_2m,weather_code,is_day`, hourly `precipitation_probability`,
air-quality `us_aqi`; WMO code -> `WeatherKind` -> icon. Refreshed every 15 min while connected; the screen
switch + weather frame are skipped while a route is active, and the next nav step after a weather switch is preceded
by `5B 4C 00 00` (default screen). Only sent to TFT models (2025+ family, U577 premium).

### Current location, DID `0x5C` (`BluetoothUtil.createCurrentLocationArray`, `BleEngine.sendAddressDataToCluster`)
`5B 5C <place name, up to 17 bytes UTF-8> <zero pad> FF`. RideLink cuts on character boundaries.
Name from `Geocoder` (sub-locality, else locality) on the coarse network fix.

### Music (`BlePacketsProvider`, `BleMusicDataSourceImpl`)
- Song: text frame DID `0x62` (`returnMusicPacket(name, 98)`).
- Artist: text frame DID `0x63` (Raider uses `0x65`).
- Play state, generic (`returnPlayPauseState`): `5B 60 00 <state> 00 00 00 <volume %> <pad> FF`;
  state pause 1, stop 4, else (playing) 0.
- Play state, U577 base/premium (`returnPlayPauseStateU577`):
  `5B 60 00 <state> 01 <cur min> <cur sec> <volume %> 00 00 00 00 <total min> <total sec> <pad> FF`; stop 254.

RideLink reads the active `MediaSession` (needs the notification listener grant) and sends song/artist/state
only when they change, plus once on every connect.

### Display -> phone ride data (notify on `5354`, `JupiterClusterDataReceiver`)
`@ParsingMeta` is 1-based; 0-based indexes here. Parsed by `DisplayTelemetry.merge`.

| Frame | Field (0-based index) |
|---|---|
| `5A 10` speedometer 1 | speed [2], odometer [3..5] BE, fuel level [6], average speed [7], trip status [8], top speed [9], throttle [10], backlight [11], switch/call status [12], 0-60 time [13], trip meter [14..16] BE, RPM [17..18] BE |
| `5A 11` speedometer 2 | fuel sensor fail [2], vehicle state [3], service reminder [4], vehicle state 1 [6], software version [7], MIL code [8], vehicle model [9], diagnostics [10], ISG code [11], turn indicators [12], connector [13], screenshot [17] |
| `5A 19` speedometer 3 | ISS duration [2..4], OTA status [5], avg fuel economy [8], instant fuel economy [9], distance to empty [11..12] BE, ISS count [15..17] |
| `5A 18` speedometer 4 | engine load [2], MAP [5], intake temp [7], engine temp [8], battery voltage [11], engine run time [12..13], distance [14..15], injection volume [16..17] |

Odometer and trip are in **0.1 km** steps (confirmed: raw 1718 shown as 171.8 km on the rider's Ntorq XT).
Fuel and the other fields are raw until confirmed. Trip [14..16] read 0/empty on the rider's scooter; needs a capture.
RideLink also keeps 5A 11 software version [7], MIL code [8], vehicle model [9] (shown in Settings to identify the
model) and 5A 18 engine load, intake/engine temp, battery voltage and run time for the Vehicle card.

### Call accept/reject from the scooter switch (`TelephonyUtils.handleCallAcceptRejectAction`)
`5A 10` [12]: 1 accept, 2 reject, 3 reject with SMS, 4 voice assistant; 64 = ignition change and 128 = vehicle off
are flags in the same byte (RideLink reads the action from the low 6 bits). The byte is a level repeated in every
frame, so RideLink acts on a change only, and only while it is showing a ringing call. Accept uses
`TelecomManager.acceptRingingCall()`, reject `endCall()` (Android 9+); both need `ANSWER_PHONE_CALLS`.
Reject with SMS (3) rejects, then (if enabled) texts the newest call-log number of the last 2 min with the
Settings message (`SEND_SMS` + `READ_CALL_LOG`). Voice assistant (4) opens `ACTION_VOICE_COMMAND` / `ACTION_ASSIST`
at any time; needs the "display over other apps" grant to start from the background.

## Model detection
The display never reports its model; the stock app takes `VehicleId` from the TVS account.
RideLink's Auto mode uses the 2025+ format and switches to the older Ntorq format when the display
sends `5A 6B` or `5A 29`, which only older Ntorq displays send.

## Bike families (VehicleId from TVS account, stored in prefs `VehicleId`)

| Variant | BikeTypes (VehicleId) | Service class | Receiver |
|---|---|---|---|
| Ntorq (old) | U_716, U_577_BASE (37), U_577_PREMIUM (38) | `NtorqBleHelper` | `NtorqClusterDataReceiver` |
| Jupiter-style | NTORQ_U812 (39), NTORQ_N251_STANDARD_NEW (43), NTORQ_U458_Race_NEW (44), NTORQ_U377_SUPER_SQUAD_NEW (45), Jupiter U279/U566/U745, Zest U714 | `N360HelperService` / `JupiterBleService` | `JupiterClusterDataReceiver` |

Account-side vehicle codes (`com/tvsm/unified/utils/VehicleCode`): legacy Ntorq trims
(Standard, Race Edition, Super Squad, XP, XT, Spider, Thor) are all code `"9"` with theme 0..8.
That is an older SmartXonnect generation handled by separate legacy code
(`com.tvsm.connect.bluetooth.bluetoothlegatt`), not yet analysed. Newer codes:
U716 `34`, U577 base `37`, U577 premium `38`, U812 `39`, N251 `43`, U458 `44`, U377 `45`.
U577 code has TFT-style features (image transfer, Wi-Fi mirroring frame `0x29`, Mappls pictogram
map), so a TFT Ntorq XT is most likely **U577 premium (38)** or **U812 (39)**. This is
unconfirmed until a real capture.

## Official onboarding flow
1. **Register vehicle (server, once):** `AddVehicleByVinActivity` -> `postAddVehicle` /
   `postAddVehicleWithoutVIN` with frame no / VIN. TVS backend returns model data; app stores
   `VehicleId`, theme, frame no, `userVehicleId` in prefs. `VehicleId` picks the BLE code family.
2. **Pair cluster (phone only):** `UnifiedBluetoothScanFragment` -> `BleEngine.onPeripheralScan()`
   scans by **name only** (Jupiter family: name contains `TVSBT` or `TVSiQUBE`, not `HUD`;
   Ntorq/Raider: contains `TVS`, not `HUD`). User taps a device -> connect -> on CONNECTED the app
   saves `(address, name)` to local Room table `PairedDeviceEntity` and prefs (`BLE_ADDRESS`,
   `BLE_NAME`). No server call and no check that the device belongs to the registered VIN.
3. **Later connects:** `connectBle()` reconnects to the saved address directly.

Implication: a fake cluster named e.g. `TVSBT01` can be paired from the official app's
Bluetooth scan screen. Afterwards the official app must be re-paired to the real scooter.

## Open questions
- User's scooter: Ntorq XT with TFT, 2026. Exact BikeType (38 vs 39) to confirm from capture.
- Jupiter-family cyclic frame (`sendJupiterMobileData`), call/SMS/notification frames, and
  cluster->phone frames (call accept/reject, music controls) still to extract.
- Whether the cluster needs bonding or a specific frame before it shows nav.
