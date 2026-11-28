# TVS Raider 125 (vehicle 29, internal N360)

Requested in issue #19. Byte indexes 0-based. Labels: Observed / Inferred / Unknown.

## Which stock code runs it

- **Live path: legacy `N360HelperService`** (extends `BaseConnectHelperService`), sender
  `N360DataSenderToCluster`, receiver `N360ClusterDataReceiver`, navigation in
  `TVSMMapActivity.N251N109U347sendNavigationData` (the same method as the Ntorq SmartXonnect, with `isN360Theme()`
  branches). Dispatch: `DashboardBikeListAdapter` (vehicle "29" → `N360HelperService`), `HomeActivity.onResume`,
  `unified/ui/fragment/DashboardFragment`. Observed.
- A unified path also exists: `BikeVariants.Raider` uses `NtorqBleHelper` + `NtorqMobileToCluster` (the U577
  sender) + `RaiderClusterDataReceiver`, and `BleEngine` has many `BikeType.RAIDER` branches next to U577. But
  `NotificationListenerService.startBleService` explicitly returns for U577 base, U577 premium and RAIDER, and the
  dashboards start the legacy service. Inferred: the unified Raider path is a migration in progress; implement the
  legacy behaviour first and borrow the unified tables where they are cleaner.
- `isN360Theme()` = vehicle "29" and theme 0 (`N251ThemeUtils.isN360Theme`). There is only one Raider theme.

## Two cluster types: TFT and LCD

- The Raider ships with a TFT SmartXonnect cluster and a cheaper LCD cluster. The stock app tells them apart from
  `5A 11` [9] (vehicle model): **0xE3 = LCD**, anything else = TFT (`N360ClusterDataReceiver`, Observed;
  `RaiderClusterDataReceiver.handleSpeedOMeter` checks `-29 || 227`, Observed).
- On TFT the stock app also starts the profile picture transfer (`startSendingProfilePicture`); out of scope.
- The LCD uses a different pictogram table (U725, below) and gets the custom-text pair 4 times.

## Connection

| Item | Value | Source |
|---|---|---|
| Service / chars | server secret `SERVICE_UUID_N360C`, `SEND_CHARACTERISTIC_UUID_N360`, `RECEIVE_CHARACTERISTIC_UUID_N360` | `N360HelperService.getServiceParcelUUID` etc. Observed |
| Expected UUIDs | `TVSMVGSA…` service, `5352` write, `5354` notify | Inferred (the `Raider` unified variant uses `NtorqBLEConnectionConfig`, which has exactly these) |
| MTU | 137 | `N360HelperService` `requestMtu(137)`. Observed |
| Write type | 1 (no response) for data frames (`N360DataSenderToCluster.writeData`) | Observed |
| Scan | name starts with `TVS` | Observed |
| Encryption | see below | |

### Encryption rule (Observed: `Utils.updateCheckSumEncryption`, `N360DataSenderToCluster.writeData`, `writeMusicData`, `N360HelperService` RX decrypt)

The cluster's BLE name carries its firmware date: character 5 is the year letter (`B` = 2023, `C` = 2024 …
`S` = 2040; anything else, including `A`, maps to year 1) and character 6 the month letter (`B` = 2 … `L` = 12,
default 1). `updateCheckSumEncryption()` is true when year ≥ 2022 and month ≥ 1, which in practice means **year
letter B or later (2023+ firmware)**.

- Firmware 2023 or later: data frames go **plain**; incoming frames are plain.
- Older firmware (year letter `A` or unknown): every data frame is XORed with **0xED** on bytes `2 .. n-2`, and
  incoming frames are decrypted the same way.
- Music frames (`5B 62`, `5B 65`, `5B 60`, volume) are **always** XORed with 0xED (`writeMusicData`), whatever the
  firmware.
- Navigation frames go plain, without checksum, through `BaseConnectHelperService.writeNavigationData`
  (`TVSMMapActivity.writeData` has no Raider branch; `writeDataForN360`, which would add checksum + 0xED, is never
  called). Observed.

Incoming frames carry the checksum in byte 18 (`U399ChecksumUtils.isValidChecksumN360`). Observed.

## Connect sequence (Observed: `N360DataSenderToCluster.sendInitialData`)

1. Rider name `5B 52 <name> FF` (same text frame as Ntorq).
2. 50 ms, status frame (below).
3. 50 ms, current location `5B 5C <place> FF`.
4. Status frame every 2 s from `BaseConnectHelperService.startSendingCyclicData`. Inferred from the shared base
   class; RideLink already uses 2 s.
5. `onVehicleData()`: `5A F1` vehicle data, then `5B 7E` birthday (below).

## Status frame `5B 4A` (Observed: `N360DataSenderToCluster.sendMobileData`)

| Byte | Value |
|---|---|
| 0, 1 | `5B 4A` |
| 2 | signal (high nibble) and battery (low nibble), built as decimal digits then `hex2decimal`, like Ntorq. Signal 0 without a SIM |
| 3 | overspeed limit (`KEYS.N360_OVER_SPEED_LIMIT`) |
| 4 | temperature, raw (`KEYS.N360_WEATHER_TEMPERATURE`, 0 when unknown) |
| 5 | `02` |
| 6 | hour, 12 h (0 → 12) |
| 7 | minute |
| 8 | second |
| 9 | AM = 0, PM = 1 |
| 10 | new missed calls (call log count minus the count at connect) |
| 11 | network type (`getMobileSignalType`) on the Raider theme, else 0 |
| 12, 13, 14 | day, month, year % 100 |
| 15 | unread message count (`BluetoothUtil.getUnreadMsgCount`) |
| 16 | voice-assist state (`pref_voice_assists_state`) |
| 17 | `00` |
| 18 | call status: 1, 2 or 5 (`onCallsStatus`; 2 and 5 also start `changeCallStatusTimer`), else 0. Meaning of each value Unknown; read `N360DataSenderToCluster.changeCallStatusTimer` and where `onCallsStatus` is set |
| 19 | `FF` |

Battery level (`BluetoothUtil.getN360BatteryLevel`): 0–10 % → 0, 11–24 → 1, 25–49 → 2, 50–74 → 3, 75–100 → 4.
Note the Raider uses 0..4, the Ntorq 0..5.

Not sent while `isImportantInstructionComing()` (a voice-assist or alert pop-up is up). After a call ends the status
frame is sent 3 times, 50 ms apart (`onDisconnectCall`).

## Navigation (Observed: `TVSMMapActivity.N251N109U347sendNavigationData`, `sendNavigationData`)

Same two frames as the Ntorq SmartXonnect (`PROTOCOL.md`, "SmartXonnect LCD"):
`5A 4E dist(2) eta(2) left(3) code lines 01 00 flag stage 00 alertType alertDist(2) FF`, then 100 ms later
`5B 4F <text up to 17> FF`. Every **800 ms** (the default timer branch). Distances, `lines`, byte 11 = 1, `flag` and
`stage` follow the SmartXonnect rules. Raider differences:

- **Rerouting code is 0xFF** on the Raider theme, not 72. Observed.
- **Pictogram table**: on the Raider theme the code goes through `getPictogramForU725`. Observed. The input is the
  Mappls maneuver id (`AdviseInfo.getManeuverID`, `getPictogramCodeForU725`) on the LCD and the TVS short-instruction
  code (`getPictogramCode`, `en_tvs_short_instuctions.json`) on the TFT, with roundabouts replaced by
  `getManeuverID(degree)`. The decompiler garbled this method; confirm the TFT branch on a real TFT Raider.
- U725 table (Mappls id → cluster id), default 0: `-1>-1 1>1 2>10 3>10 4>11 5>12 6>9 7>57 8>22 15>10 16>12
  19>10 20>12 21>57 23>62 25>63 35>14 36>23 41>19 50>57 65>27 66>28 67>29 68>48 69>31 70>32 71>33`. Observed.
  (`2>10`, slight left to the right-turn arrow, looks wrong but is what the code says.)
- The unified Raider path uses `BleNavigationDataSourceImp.getClusterPictogramMappingForRaider` (default 64):
  `0>0 1>1 2>2 3>10 4>11 5>12 6>9 7>57 8>22 15>2 16>12 19>2 20>12 21>57 23>62 25>63 35>14 36>23 41>19 50>57
  65>27 66>28 67>29 68>48 69>31 70>32 71>0 255>255`, rest 0. Observed. RideLink's `Maneuver.mappls` ids feed this
  table directly. **Recommended table for RideLink: the unified Raider table**, with 71 → 33 if a rider confirms the
  sharp-right roundabout arrow.
- In the unified `BleNavigationSendData.byteArrayForNavigation`, vehicle "29" sends `navOn` 1 while running and 0
  when stopped (existing `PROTOCOL.md` line on `navOn`). Observed.

## Calls, messages, text

| Frame | Layout | Source |
|---|---|---|
| Caller | `5B 43 <name or number, +91 removed, up to 17> FF` (`createCallerNameArray`) | `sendCallerName`. Observed |
| Call ended | status frame ×3, 50 ms apart | `onDisconnectCall`. Observed |
| SMS sender | `5B 53 <number, +91 removed, up to 16> FF`, written 8 times 50 ms apart | `onSendSMS`, `sendSMS`. Observed |
| Chat counts | `5B 9C <WA> <FB> <IG> <alert> 00 <b5> 00… FF`; FB = (count & 0x0F) << 4 | `sendMobileDataNotificationAndAlert`, `sendFBData`. Observed |
| Custom line 1 | `5B 4C <feedbackId> <tftState> <text> 00… FF` (**two parameter bytes before the text**, unlike Ntorq) | `sendMobileData51`. Observed |
| Custom line 2 | `5B 63 <text up to 16> 00… FF` | `sendMobileData6`. Observed |
| Pop-up pair | line 1 then line 2, 50 ms apart; on the LCD 4 rounds, 250 ms apart | `sendCustomData`. Observed |
| Low fuel prompt | `sendCustomData("", "", 0, 67)` every 1 s, up to 10 times, until dismissed | `sendAutoNavigationCommand`. Observed |

## Music (TFT; Observed: `sendTrackTitle`, `sendArtistName`, `sendMusicPlayback`, `sendVolumeInfo`)

Song `5B 62`, artist **`5B 65`** (not `63`), play state `5B 60`, volume frame from `sendVolumeInfo`. All through
`writeMusicData`, always XOR 0xED. Whether the LCD shows music is Unknown.

## Other phone → cluster frames

| Frame | Layout | Source |
|---|---|---|
| Vehicle data | `5A F1 <illumination × 2, max 10> <ride mode> 00 00 00 00 00 <d> <m> <yy> 00 00 01 00 00 00 00 FF` | `lambda$onVehicleData$0`. Observed |
| Birthday | `5B 7E <whose> <day> <month 1..12> <yy> 00… FF` | `sendBirthDate`. Observed |
| Location | `5B 5C <place> FF` | `sendCurrentLocation`. Observed |
| Weather | `5B 5D …` | `sendWeatherData`. Layout Unknown, read the method |
| Cricket / football / news | `5B 81`, `5B 91`, `5B 96` (news frames are 75 bytes, need the 137 MTU) | server data; out of scope |
| Disconnect | `5B 4A 01 00 00 00 00 00 00 00 00 00 00 00 00 00 10 11 12 FF` | `sendDisconnectCommandToCluster`. Observed |

## Cluster → phone (Observed: `com.tvs.bike.core.protocol.raider.*`, `RaiderFrameIdentifier`)

| DID | Fields (0-based) |
|---|---|
| `5A 10` | speed [2], odometer [3..5], fuel [6], average speed [7], trip status [8], top speed [9], throttle [10], switch / location tag [11], 0-60 time [12], average mileage [13], trip F meter [14..15], RPM [16..17], checksum [18] |
| `5A 11` | fuel sensor fail [2], vehicle state 1 [3], service reminder [4], **gear [5]**, ISS / state 2 [6], software version [7], MIL code [8], vehicle model [9] (0xE3 = LCD), diagnostics [10], ISG code [11], turn indicators [12], connector [13], backlight / BT pair [17] (high nibble / 2 = backlight) |
| `5A 19` | ISS duration [2..4], OTA [5], average economy [8], instant economy [9], distance to empty [11..12], ISS count [15..17] |
| `5A 18` | same as Ntorq `5A 18` (engine load, MAP, temps, battery voltage [11], run time, distance, injection volume) |
| `5A 6B` | music command [2] |
| `5A 54` | ack, music command [2] |
| `5A 9D` | primary software version [3], image version [8..10], BLE version [13..15] |

Notes:
- Speed-meter 1 differs from the Ntorq layout: switch status is at [11] (Ntorq [12]), average mileage at [13],
  trip at [14..15] (2 bytes), RPM at [16..17]. Observed.
- The legacy receiver drops `5A 10` frames whose [11] has bit 7 set (`N360ClusterDataReceiver`, `(bArr[11] & 128)
  != 128`). Observed. Inferred: bit 7 is a flag, the low bits are the switch action (accept 1, reject 2, as on
  Ntorq). Confirm with a rider log.
- Trip A / Trip B: the legacy receiver's `readFrame19` reads trip A distance [5..6], trip A mileage [7], trip B
  distance [8..9], trip B mileage [10], trip A average speed [13], trip B average speed [14]. Observed. Which DID
  carries it is Unknown (the dispatch around `N360ClusterDataReceiver` line 1545 was garbled); it conflicts with the
  unified `5A 19` layout above. A rider log with RX lines settles it.
- Fuel bars ≤ 1 broadcasts the low-fuel alert (existing `PROTOCOL.md`). Observed.

## What RideLink needs for the Raider

1. A `RAIDER` model in a new `ClusterFamily.N360` (or a SmartXonnect sub-variant): SmartXonnect nav and timing,
   Raider pictogram table, reroute 0xFF, status frame above, battery 0..4.
2. The firmware-date XOR rule from the BLE name, applied in one place before `ClusterLink.send`, plus the
   always-XOR rule for music.
3. Telemetry parser for the Raider `5A 10` / `5A 11` layout (gear, switch at [11]).
4. Custom line 1 with the two parameter bytes.
5. TFT / LCD switch from `5A 11` [9].
6. Manual model pick (auto-detection cannot tell a Raider from a U577; see INVENTORY.md section 6).

Open questions for a Raider owner (issue #19): Console log with RX lines; BLE name (for the firmware letter); does
the arrow show for left, right, U-turn and roundabout; do the switch buttons accept and reject calls.
