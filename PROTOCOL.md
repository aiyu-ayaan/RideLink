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
With the screen off Android stops the modem's signal reports and `getSignalStrength()` goes stale (bars only moved
after unlocking); with fine location RideLink asks `requestCellInfoUpdate` every 10 s and sends the best registered
cell's level while that reading is under 30 s old.
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
Short text comes from the Mappls SDK file `translations/mappls-directions-en_short_instructions.json` (v5,
`DirectionsUtils.getShortInstruction`, text before `$`; the number after `$` is the maneuver id): "Turn left",
"Turn right", "sharp left", "slightly left", "Keep left", "U-turn", "Enter roundabout", "Destination",
"Go straight". Direction only, never a street name. RideLink sends the same words for each maneuver
(`ClusterProtocol.directionText`), so `lines` is always 1.

This layout is **not** what the 2026 Ntorq 125 Race XP takes: that display is a SmartXonnect LCD (vehicle code 9,
see "SmartXonnect LCD" below). Sent this frame it shows the distance and the text ("250 m / SHARP LEFT", rider photo
2026-11-06) but never an arrow, for every code tried (0, 1, 2, 7, 10, 11, 12, 17, 19, 22, 48, 57). Most likely the
arrow needs byte 11 = 1 and the stage byte of the SmartXonnect layout (unconfirmed until tested on the scooter).

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
Stock counting (`SocialMediaCountHelper.insertWhatsappRecord` / `insertFBRecord` / `insertInstaRecord`): +1 per new
notification (deduplicated on key + text), the frame written at once, all counts cleared when the service starts.
RideLink does the same (key + `when` + text, group summaries and ongoing notices skipped) and resets an app to 0
once none of its notifications are left in the shade. An earlier RideLink rebuilt the counts from the shade; a
chat's count then stayed at 1 per chat, so a second message never changed the frame and the badge never popped.

### Message pop-up on the custom lines (`N109U347N251DataSenderToCluster.sendCustomData` / `sendEcommerceCustomData`)
Stock shows short notices ("Food / NOTIFICATION" for Swiggy and Zomato, voice-assist feedback) as line 1 `5B 4C` then
line 2 `5B 63`: on SmartXonnect themes other than U445B/U566 the pair 5 times, 300 ms after line 1 and 100 ms after
line 2 (`sendCustomData`). RideLink uses the same pair for any chat message (WhatsApp, Telegram, SMS, any
`CATEGORY_MESSAGE` / MessagingStyle notification): line 1 app + sender, line 2 the message, punctuation turned into
spaces, held 6 s, then the rider's own lines come back. Pop-ups queue (up to 5, oldest dropped) and show one after
another. The same pop-up carries "Missed call / name" (newest call-log entry marked missed, `READ_CALL_LOG`) and
"Food" or "Order" over "Notification" for Swiggy, Zomato, Blinkit, Zepto, Dunzo, Amazon and Flipkart (stock
`N251HelperService.sendSocialmediaNotification`; stock only shows it on U445B/U566).
During a route the turn resends (every 800 ms on SmartXonnect, 2 s otherwise) replaced the pop-up and the caller
name within a second, so RideLink holds the turn frames while a pop-up shows (6 s) or a call rings, then resends the
current step at once. A RideLink choice, not taken from stock.

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

Fuel bars: the SmartXonnect LCD (legacy `N251ClusterDataReceiver`) keeps them in the **low nibble** of `5A 10` [6]
(`String.format("0x%02X ", b[6]).substring(3, 4)`); `JupiterSpeedOMeter1` / `NtorqSpeedOMeter1` store the whole byte.
The rider's Race XP reports `05` (5 bars). Stock low-fuel rule (`N251ClusterDataReceiver`, `N360ClusterDataReceiver`):
bars <= 1 broadcasts `ACTION_LOW_FUEL_ALERT` (petrol pump dialog), bars > 1 clears it. RideLink: `DisplayTelemetry.fuelBars`,
`LowFuelWatch` (range from 5A 19 when present, else that bar rule).

Mileage and range: `N251ClusterDataReceiver` reads `5A 19` [8] average economy, [9] instant, [10] trip mileage,
[11..12] distance to empty, but only feeds them to the U566 theme model. The rider's 2026 Race XP (Racing 2.0 theme)
never sends `5A 19` (log of 2026-11-06: only 5A 10 / 11 / 16 / 18, and 5A 18 injection volume [16..17] is 0), so the
display gives no mileage or range at all; RideLink shows the fuel-log mileage (full-to-full) instead.

Service: `5A 11` [4] service reminder is display -> phone only (raw). Stock has no phone -> display service frame;
its service reminders come from the TVS server. RideLink's reminders are phone-side and use custom line 1 (`5B 4C`).

Find my vehicle: the status-frame flag is set only for the Jupiter U279 theme, for the first 10 status frames after
`JupiterBleService` starts with `EXTRA_IS_FIND_ME` (`JupiterMobileToCluster.sendMobileData`,
`DashboardViewModel`). Not used for Ntorq; RideLink keeps sending 0. Stock "last parked location" is the phone's
location at ride stop (`BaseClusterDataReceiver.callLastParkedLocationAPI`), the same as RideLink.

SOS / location sharing: stock texts a Google Maps link by `SmsManager` (`Utils.sendSendSosMessage`); there is no live
location sharing. Stock has no chat auto-reply (no `RemoteInput` use); RideLink's WhatsApp reply is its own.
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

## SmartXonnect LCD (vehicle code 9, legacy app: `TVSMMapActivity`, `N109U347N251DataSenderToCluster`, `N251ClusterDataReceiver`)
Account code "9" (`nTorq251`) covers the legacy Ntorq trims; the theme id comes from the account
(`N251Theme`: Default 0, Marvel 2/3/4, Racing 2.0 = 5 = Race XP (`u458_ntroq_tile_bg`), U445B 6, Spiderman 7, Thor 8).
Everything below is for themes other than U445B/U566, which add alerts and remap codes.

Display frames: `5A 10`, `5A 11`, `5A 18` like the other families, plus two only this receiver parses:
- `5A 16` race stats: best lap `[6]` min `[7]` s `[8]` cs, top speed `[10]`, lap distance `[11..13]`/10 km,
  best 0-60 `[14]`/10 s, average speed `[15]`. (Rider's display: 7:23.30, 69 km/h, 15.0 s, 35 km/h.)
- `5B 42` the display's Bluetooth MAC in `[8..13]` (stock checks it against the account: the "MAC ID" error).
RideLink detects this family from either frame and saves it as the scooter's model.

Navigation, every **800 ms** (`TVSMMapActivity.sendNavigationData`, `N251N109U347sendNavigationData`):
```
5A 4E dist(2) etaMin(2) left(3) code lines 01 00 flag stage 00 alertType alertDist(2) FF
```
then **100 ms** later `5B 4F <text up to 17> FF`. Raw bytes, no checksum.
- dist rounded to 5 m under 25 m, else 50 m; `[0, d]` up to 255, else 2 bytes.
- `code`: stock's legacy screen sends TVS's own `res/raw/en_tvs_short_instuctions.json` (v5) `$code` unmapped
  (left 0, sharp left 1, keep left 2, right 3, sharp right 4, keep right 5, straight 7 / 21, destination 8,
  slightly left 15, slightly right 16, U-turn 41, roundabout 72). The 2026 Race XP does **not** draw those: on the
  rider's display (2026-11-06) 0, 1 and 2 drew the left arrows, 7 drew a keep-left arrow and 3, 4, 5, 15, 16, 41,
  72 drew nothing. Its pictogram (2026 owner's manual item 12, "Navigation window (pictogram)": arrowheads in eight
  directions round a ring, left of the text lines) takes the 2025+ ids of `getPictogramMappingForNtorqU812`, which
  RideLink sends: left 0, sharp left 1, slight left 2, keep left 7, right 10, sharp right 11, slight right 12,
  keep right 17, U-turn 19, destination 22, roundabout 48, straight 57, roundabout exits 27 / 28 / 29 / 31 / 32 / 33
  (Mappls 65..71, the angle travelled round in 45 degree steps; 68 = straight across = 48).
- `lines` = 2 when the text is over 17 characters, else 1. Byte 11 = 1 while navigating.
- `flag` = 1 with the "Mobile Data" (stage 7) and "GPS" (stage 6) messages, else 0.
- `stage`: 1 = turn 65..1000 m ahead; 3 / 4 = text contains "left" / "right" and under 65 m (stock: else the
  default, 0 for a turn; RideLink sends 1 so the U-turn / roundabout / destination arrow stays up). Under stage 4 the
  rider saw `->->->` in the text lines: the display's own "turn now" chevrons; 5 = straight, or any turn over 1 km (straight arrow, text "STAY SAME RD"); 8 = arrived (dist 0,
  "You Arrived"); 0 with code 72 = "Rerouting"; 0 = "Take U-Turn" hint.
- Alert block zero (traffic camera / road works only on U445B).
- Stock sends nothing when a route is cancelled (timer cancelled); RideLink does the same, and sends the
  "You Arrived" frame when the route ended at the destination.

Status, DID `4A` (`sendMobileData`): `netBatt overspeed(120 off) 00 02 hour12 min sec ampm(0 AM) missed 00
day month yy 00 voiceAssist 00 00` (temperature 0, byte 3 = 2, network type 0 on these themes).
No music frames (`5B 63` is this display's second text line, used for "NAVIGATION / ASSIST ON"); location `5B 5C`,
caller `5B 43` (`BluetoothUtil.createCallerNameArray`) and rider name `5B 52` as elsewhere.

Chat counts (`sendMobileDataNotificationAndAlert(getWhatsappByte(), getFBByte(), getInstaByte(), 0, 0, 0)`):
`5B 9C <WA> <FB> <IG> 00 <alert> 00 ... FF`, **no checksum**; WA = messages (5 bits) << 3 with chats 0,
FB = messages (4 bits) << 4. Call ended (`onDisconnectCall`): the status frame three times, 100 ms apart.
Extras RideLink sends to this display:
- `5A F1 <brightness 2..5> <ride mode 0> 00 00 00 00 00 <day> <month> <yy> 00 00 01 00 00 00 00 FF`
  (`onVehicleDataForU458`; Race XP stock leaves ride mode 0) on connect and when the brightness changes.
- `5A 7E <whose: 1 purchase, 2 birthday> <day> <month> <yy> 00 ... FF` (`sendBirthDate`) on connect.
- `5B 53 <SMS sender up to 17> FF` (`createSendSMSNameArray`), 8 times 100 ms apart, for a new SMS.
- Auto-reply SMS to a missed caller while connected (stock DND / auto reply; phone side only).
Experimental, off by default (stock has the code but never calls it, or feeds it TVS cloud data):
- `onClearCall` `5A F1 05 00 00 00 00 00 00 <d> <m> <yy> 01 00 01 00 00 00 00 FF` after a call ends,
  `onClearSMS` `5A F1 <brightness> 00 00 02 AA 00 00 <d> <m> <yy> 00 01 01 00 00 00 00 FF` after an SMS is read.
- `sendDeliveryAlert(code)`: `5B 9C` byte 5 (code meaning unknown; Console sweeps 1..20) on a delivery-app update.
- `carbonEmission(value)`: `5B 9C` byte 6; RideLink sends the last ride's CO2 in kg (distance / mileage x 2.31). Not used by RideLink: `5A F1` vehicle data (illumination, ride mode, date, "clear call / SMS" flags for the
notification LED), the "NAVIGATION / ASSIST ON" text before a route, and `5B 5D` climate text (a fixed
placeholder string in stock).

## Model detection
The display never reports its model; the stock app takes `VehicleId` from the TVS account.
RideLink's Auto mode uses the 2025+ format until a frame proves otherwise:
- `5A 16` or `5B 42` -> SmartXonnect LCD (saved as the scooter's model; wins over a manual pick).
- `5A 6B` or `5A 29` -> older Ntorq (U577 premium format).

## Bike families (VehicleId from TVS account, stored in prefs `VehicleId`)

| Variant | BikeTypes (VehicleId) | Service class | Receiver |
|---|---|---|---|
| Ntorq (old) | U_716, U_577_BASE (37), U_577_PREMIUM (38) | `NtorqBleHelper` | `NtorqClusterDataReceiver` |
| Jupiter-style | NTORQ_U812 (39), NTORQ_N251_STANDARD_NEW (43), NTORQ_U458_Race_NEW (44), NTORQ_U377_SUPER_SQUAD_NEW (45), Jupiter U279/U566/U745, Zest U714 | `N360HelperService` / `JupiterBleService` | `JupiterClusterDataReceiver` |

Account-side vehicle codes (`com/tvsm/unified/utils/VehicleCode`): legacy Ntorq trims
(Standard, Race Edition, Super Squad, XP, XT, Spider, Thor) are all code `"9"` with theme 0..8.
That is the SmartXonnect LCD generation (section above), handled by the legacy code in
`com.tvsm.connect.bluetooth.bluetoothlegatt` and `TVSMMapActivity`; the 2026 Race XP is one of them. Newer codes:
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
