# Apache family

Byte indexes 0-based. Labels: Observed / Inferred / Unknown.

## Models and stacks

| Id / theme | Code | Product | Live stack | Encryption (TX / RX) |
|---|---|---|---|---|
| 3 | N112 | older Apache SmartXonnect (Inferred) | Legacy `U324HelperService` → `N112SendDataToCluster` | plain / plain, write type 1 |
| 27 | U507 | Apache RTR 160 4V (`APACHE160`) | Legacy `U324HelperService` → `U507SendDataToCluster` (new UUID) or N112 classes (old UUID) | new UUID: 0xEB / 0xEB; old: plain |
| 23 / 0 | U399 (U399C) | Apache RR310 | Legacy `U399HelperService` | 0xEA / 0xEA |
| 23 / 1, 2 | U490, U713 | RR310 variants (Inferred) | Legacy `U399HelperService`; unified `ApacheBleService` lists them | 0xEA / 0xEA |
| 31, 32 | U449, U469 | Apache (Inferred: newer RTR 160 / 200 4V) | Legacy `U449HelperService`; unified `ApacheBleService` | 0xEB / 0xEB |
| 33 / 0 | U400 | Apache RTR 310 | Legacy `U400HelperService` | 0xEA / 0xEA |
| 33 / 1 | U712 | RTR 310 variant (Inferred) | Legacy `U400HelperService` for id 33; unified `ApacheBleService` for `BikeType.APACHE_U712` | 0xEA / 0xEA |
| 35, 36 | U796, U797 | basic Apache (`SERIES_APACHE_U796_797 = "U711"`, `isBasicApacheSeries`) | Unified `ApacheBleService` | 0xEA / 0xEA |
| 42 | N597 | Apache, product Unknown | Unified `ApacheBleService` | 0xEA / plain |

Sources: `BikeVariantsComponentImplement.getBikeVariants`, `BikeTypeKt.isApacheSeries`, `HomeActivity.onResume`,
`DashboardFragment`, `BleEngine.writeCharacteristicToCentral`, sender `writeData` methods. Observed.

For vehicle 33 both stacks have code; the dashboards start `U400HelperService` for "33", the unified
`BleConnectViewModel` starts `ApacheBleService` for the Apache variant. Which wins for theme 1 is Unknown.
Implement one Apache family with per-model differences; the frame shapes below are close across both stacks.

## Connection

| Item | Value | Source |
|---|---|---|
| Unified | `TVSMVGSA…`, `5352` write with response, `5354` notify (`ApacheBLEConnectionConfig`); writes go through `writeCharacteristicApache` with response | Observed |
| Legacy U399 / U400 | server secrets `SERVICE_UUID_U399C`, `SERVICE_UUID_RTR_U400`; MTU 141; write type 2 | Observed |
| Legacy U449 | U399C secrets; write type 2 | Observed |
| Checksum | data frames built with `addChecksum` when `isChecksumNeeded`; nav frames always checksum (`ApacheMobileToCluster.addChecksumWriteEncryptedData`, `writeDataForU399/U400/U449`) | Observed |

## Status frame `5B 4A`

### Unified (Observed: `BlePacketsProvider.sendApacheMobileData`)

| Byte | Value |
|---|---|
| 2 | signal / battery nibbles (`getIceBatteryLevel`, 0..5) |
| 3 | overspeed limit (setting when the alert is on, else 120) |
| 4, 5 | `00 00` |
| 6, 7, 8 | hour (`getCurrentHour`), minute, second |
| 9 | AM = 1, PM = 0 (`calendar.get(AM_PM) != 0 ? 0 : 1`) |
| 10 | missed calls |
| 11 | network type |
| 12, 13, 14 | day, month, year % 100 |
| then | per model, see below |
| 19 | `FF` |

Tail by model (the condition in the decompile is garbled; this is the reading that makes the three branches
distinct, Inferred):
- N597, U713, U712, U490, U400, U399: [15] call status, [16] voice-assist byte (`hex2decimal("<va>0")`).
- U449, U469: [15] `00`, [16] voice-assist byte, [17] `00`.
- Others (U796, U797): [15] `00`, [16] voice-assist byte, [17] `00`, [18] call status.

When there are no missed calls the unified sender also writes the vehicle-control frame (below).

### Legacy (Observed: `U399DataSenderToCluster.sendMobileData`, `U400SenderDataToCluster.sendMobileData`, `U449SendDataToCluster.sendMobileData`)

- U399: `5B 4A netBatt overspeed 00 02 hour min sec ampm missed 00 d m yy <b15> <b16> crash <b18> FF`.
  [15], [16], [18] are set from variables the decompile did not name (candidates: missed SMS, voice assist, call
  status). Unknown, read the method.
- U400: `5B 4A netBatt overspeed 00 02 hour min sec ampm missed 00 d m yy <b15> voiceAssist 00 00 FF`.
- U449 / U507: `5B 4A netBatt overspeed 00 smsSent hour min sec ampm missed <b11> d m yy …` (same shape as the Ronin
  frame in RONIN.md). Read the full concat before implementing.

## Navigation

### Unified (Observed: `BleNavigationSendData.sendApacheNavigationDataToCluster`, `ApacheClusterNavigationData.toByteArray`)

| Byte | Field |
|---|---|
| 0, 1 | `5A 4E` |
| 2, 3 | distance to manoeuvre: 0 → `00 01`, ≤ 255 → `00 d`, ≤ 65535 → big-endian, else `FF FF` |
| 4, 5 | remaining minutes |
| 6, 7, 8 | remaining distance, metres, 3 bytes |
| 9 | pictogram |
| 10 | number of text lines (2 when text > 17 chars) |
| 11 | navigation status (0xFF while rerouting, 0 on arrival under 20 m) |
| 12 | unit |
| 13 | alert (1 for "no GPS" / "no mobile data") |
| 14 | prefix / stage: 1 turn ahead (65..1000 m), 3 left close (< 65 m), 4 right close, 5 straight (> 1 km), 6 no GPS, 0 U-turn suggestion / reroute / no data |
| 15..18 | reserved (byte 18 becomes the checksum) |
| 19 | `FF` |

Then checksum and XOR (0xEA or 0xEB by model). Arrival (≤ 75 m left, or ≤ 70 m to the next destination) sends
pictogram 34 (`DATA_ID_USER_ID`). An optional road-name frame `5A 49 <road> … <arrived> <travelled m (2)>
<minutes (2)> 00 00 FF` follows (`sendNavigationControl2`, 33 bytes, needs a large MTU). Observed.

Pictogram (Mappls maneuver id → cluster id), `ApacheUtils.mapPictoIdForApache` with Mappls →
`BleNavigationDataSourceImp.getIceClusterPictogramId`: 0..2 unchanged, 3..5 → +7 (10, 11, 12), 65..71 → −38
(27..33), and `6>9 7>57 50>57 8>22 11>8 73>8 74>8 12>18 75>18 13>0 56>0 14>10 52>10 15>7 19>7 22>7 16>17 20>17
24>17 17>1 55>1 23>62 25>63 18>11 53>11 36>23 41>19 54>19 51>12 57>2 68>48 21>49 72>47`, unknown → 0.
Observed. (A separate Google-Maps table exists in `mapPictoIdForApache`; RideLink produces Mappls ids, so use this
one.)

### Legacy (Observed: `TVSMMapActivity.U399SendNavigationData`, `U400SendNavigationData`, `U449sendNavigationData`, `N112sendNavigationData`)

U399, U400 and U449 send the same `5A 4E` + `5B 4F` pair as the Ronin, every **500 ms**, with checksum + XOR
(0xEA for U399 / U400, 0xEB for U449). Field order follows the Ronin frame; confirm per model from the
`concatByteArrays` argument order in each method. N112 / old U507 send a text-led format
(`prefixNavigationN … <code> <lines> <text>`) every 400 ms; read `N112sendNavigationData` if anyone asks for N112.

## Text, calls, music

| Frame | Layout | Source |
|---|---|---|
| Rider name | `5B 52 <name> FF` | `ApacheMobileToCluster.sendRiderName`. Observed |
| Custom line | `5B 4C <feedbackId> <text> …` via `apacheCustomTextLineOneArray(text, feedbackId, 0x4C)`; U449/U469 use `apache2vCustomLineText` | `sendMobileData5`. Observed; read `BluetoothUtil.apacheCustomTextLineOneArray` for the exact bytes |
| Destination reached | `apacheCustomTextLineOneArray(text, 64, 0x4C)` | `sendDestReachedCustomText`. Observed |
| Birthday | `5B 7E <whose> <day> <month 1..12> 00 00 01 00… FF` | `ApacheMobileToCluster.sendBirthDate`. Observed |
| Caller | `5B 43` text frame (shared) | Inferred |
| Music | U400 legacy sender writes `5B 60`, `5B 61` (album), `5B 62`, `5B 65`; unified play state `returnPlayPauseStateApache` (with track durations) | Observed |

## Vehicle control `5A F1` (Observed: `BlePacketsProvider.sendApacheVehicleControl`)

`5A F1 <illumination 1..9> <speedo / mode> 00 02 <TSL on/off> 00 00 <day> <month 0-based> <year low byte> <clear call
info> 01 00 00 00 00 00 FF`. Ride-mode reset and last ride mode through `updateVehicleSettings`.

Calibration frames `5B 73 <command> <id> <len> <data(4)> <checksum> 00… FF` (`sendCalibrationUpdate`): id 18
follow-me headlamp timer, id 9 idle start-stop timer, `5B 73 08` read request (`calibrationWriteRequest`). Observed.

## Cluster → phone (Observed: `com.tvs.bike.core.protocol.apache.*`, `ApacheIncomingFrameIdentifier`)

| DID | Fields |
|---|---|
| `5A 10` | speed [2], odometer [3..5], fuel [6], average speed [7], mileage [8], top speed [9], throttle [10], location tag / switch [11], 0-60 [12], average mileage [13], trip F [14..15], RPM [16..17], checksum [18] |
| `5A 11` | direction / state [2], vehicle state 1 [3], service reminder [4], **gear [5]**, battery voltage / state 2 [6], software version [7], MIL blink [8], vehicle model [9], diagnostics [10], turn indicators [12], engine temp / tell-tales [13], screen matrix [14], state 3 [15], ABS MIL [16], backlight / **vehicle mode** [17] |
| `5A 12` | lean angle [2], **cruising range [3..4]**, wheel angle offset [5], acceleration [6], torque [7], trip distance [8..9], trip time h [10] m [11], trip mileage [12], trip fuel [13..14], overspeed threshold [15], overspeed setting [16] |
| `5A 16` | lap time min [2] s [3] ms [4], lap number [5], best lap min [6] s [7] ms [8], best lap number [9], lap trigger [10] |
| `5A 18` | engine load [2], accumulated injection time [3..4], MAP [5], barometric [6], intake temp [7], engine temp [8], injection time [9..10], battery voltage [11], run time [12..13], distance [14..15], injection volume [16..17] |
| `5A 29` | basic data (16 raw bytes) |

The legacy U399 / U400 receivers parse frames manually (`U399DataParser`, `U400DataParser`, `U449DataParser`);
the unified `U399SpeedMeter*` classes show the same positions (speed [2], odometer [3..5], fuel [6], gear 5A 11 [5],
cruising range 5A 12 [3..4], laps). Observed.

Detection hazard: `5A 16` and `5A 29` collide with RideLink's SmartXonnect / U577 detection (INVENTORY.md 6).

## What RideLink needs for Apache

1. `ClusterFamily.APACHE` with per-model XOR key (0xEA or 0xEB), checksum on nav, write with response.
2. Unified status frame with the three tails; legacy U399 / U400 frames as variants once their unnamed bytes are
   read.
3. The 20-byte nav frame above with the ICE pictogram table.
4. Telemetry parser for `5A 10/11/12/16/18`: gear, ride mode, cruising range, lean angle, lap timer.
5. Capability flags: gear, ride mode, range, laps, lean angle; music on U400 (legacy) and the unified Apache path
   (`BleMusicDataSourceImpl` has Apache branches); social counts Unknown: the legacy Apache senders have no `5B 9C`
   method, but the unified `BleWriteSocialMediaNotificationDataSrcImpl` has no family check, so unified Apache models
   probably receive it.
