# TVS Ronin (vehicle 25, internal U368)

Byte indexes 0-based. Labels: Observed / Inferred / Unknown.

## Which stock code runs it

- **Only the legacy stack**: `U368HelperService` (extends `BaseConnectHelperService`), sender
  `U368SendDataToCluster`, receiver `U368ClusterDataManager` / `U368BaseClusterDataReceiver`, parser
  `U368DataParser`, navigation `TVSMMapActivity.U368sendNavigationData`. Dispatch: `DashboardBikeListAdapter`,
  `HomeActivity.onResume` and `unified/ui/fragment/DashboardFragment` (vehicle "25"). Observed.
- Vehicle 25 has no `BikeType` entry, so the unified engine never handles it. Observed.
- Account series name `RONIN` (`KEYS.SERIES_U368`), `KEYS.isRonin("25")`, counted as a premium bike
  (`KEYS.isPremiumBikes`). Observed.
- The Ronin shares its GATT secret keys with the Apache RR310 (`SERVICE_UUID_U399C`,
  `SEND_CHARACTERISTIC_UUID_U399C`, `RECEIVE_CHARACTERISTIC_UUID_U399C`). Observed. Inferred: same cluster
  platform as the RR310 / Apache 4V, so the Apache frame layouts (APACHE.md) are the best starting point for its
  RX frames.

## Connection

| Item | Value | Source |
|---|---|---|
| Service / chars | U399C secrets; expected `TVSMVGSA…`, `5352`, `5354` | Observed / Inferred |
| MTU | not requested by `U368HelperService` | Observed |
| Write type | 2, with response | `U368HelperService.getCharacteristicsWriteType`, `U368SendDataToCluster.writeData`. Observed |
| Scan | name starts with `TVS` (U399 / U368 branch of `BluetoothDeviceListActivity`) | Observed |
| Encryption | **every data frame XOR 0xEB** on bytes `2 .. n-2`, no firmware condition (`U368EncryptDecryptDataUtils.encryptData`) | Observed |
| Navigation frames | checksum in byte 18, then XOR 0xEB (`TVSMMapActivity.writeDataForU368`) | Observed |
| RX | XOR 0xEB, checksum check `U399ChecksumUtils.isValidChecksumU368` | Observed |
| Disconnect | `5B 4A 01 00 … 10 11 12 FF`, written plain | `sendDisconnectCommandToCluster`. Observed |

## Connect sequence (Observed: `U368SendDataToCluster.sendInitialData`)

Rider name `5B 52` three times, 400 ms apart; 400 ms; status frame; 400 ms; location `5B 5C`; 400 ms.

## Status frame `5B 4A` (Observed: `U368SendDataToCluster.sendMobileData`)

| Byte | Value |
|---|---|
| 2 | signal (high nibble) / battery (low nibble), battery from `getU368BatteryLevel`: 0–20 % → 0, 21–40 → 1, 41–60 → 2, 61–80 → 3, else 4 |
| 3 | overspeed limit (120 when the alert is off, else the setting) |
| 4 | `00` |
| 5 | SMS-sent flag (1 after the auto-reply SMS was sent) |
| 6 | hour: 24 h value when the phone uses 24 h format, else 12 h (0 → 12) |
| 7, 8 | minute, second |
| 9 | 12 h: AM 0 / PM 1. 24 h: AM `0x10` / PM `0x11` |
| 10 | new missed calls |
| 11 | `00` |
| 12, 13, 14 | day, month, year % 100 |
| 15 | new missed SMS |
| 16 | voice-assist state |
| 17 | crash-alert flag |
| 18 | `00` |
| 19 | `FF` |

## Navigation (Observed: `TVSMMapActivity.U368sendNavigationData`, timer in `sendNavigationData`)

Every **500 ms** while Mappls navigates. Two frames:

`5A 4E dist(2) eta_min(2) left(3) code lines 01 00 00 stage <zero pad> <checksum> FF`, then
`5B 4F <text up to 17> 00… FF`, each with checksum + XOR 0xEB.

- `dist` rounded to 50 m. `eta_min` = seconds / 60. `left` 3 bytes.
- `code` = TVS short-instruction code from `en_tvs_short_instuctions.json` (`getPictogramCode`), roundabouts
  replaced by `getManeuverID(degree)`; straight / unknown = 7. The same raw codes the Ntorq SmartXonnect legacy screen
  sends (`PROTOCOL.md`). Whether the Ronin draws them is Unknown: the Race XP did not, so test early.
- `stage`: 5 straight, 3 turn left close, 4 turn right close, 1 turn ahead, 8 arrived, 0 rerouting. Rerouting also
  sends a custom message through `U368HelperService.sendCustomMessage`.
- Compared with the SmartXonnect frame there is no `flag` byte use and no alert block.

## Other phone → cluster frames (Observed: `U368SendDataToCluster`)

| Frame | Layout |
|---|---|
| Caller | `5B 43 <name or number, +91 removed, up to 17> FF` (`sendCallerName`) |
| SMS sender | `createSendSMSNameArray` (`5B 53 …`) via `onSendSMS` |
| Custom message | `sendMobileData5(text)`, 3 times 400 ms apart (`sendCustomData`); layout in `sendMobileData5` (`5B 4C …`), read before implementing |
| High speed | `5B 4C "HIGH SPEED ALERT" 00… FF` (`sendHighSpeedMessage`) |
| Low fuel | `5A 4E <dist(2)> 00 00 00 00 00 51 01 00… FF` (`sendLowFuelAlert`; dist = km × 10 under 1000, else / 100) and `5B 4C 20 <15 → 0x15 else 0> "Low Fuel Alert" … FF` (`sendLowFuelAlertMessage`) |
| Vehicle data | `5A F1 <illumination> <ride mode> 00 00 00 00 00 <d> <m> <yy> 00 00 01 00 00 00 00 FF` (`onVehicleData`) |
| Clear call | `5A F1 <illumination> <ride mode> 00 02 <TSL_HAZARD> 00 00 <d> <m> <yy> 00 01 01 00 00 00 00 FF` (`onClearCall`) |
| Climate | `createClimate("Cloudy 18⁰C")`, a fixed placeholder in stock |
| Location | `5B 5C <place> FF` |

No music frames, no social counts and no weather screen in the Ronin sender. Observed (no `5B 60/62/63/65`,
`5B 9C` or `5B 5D` in `U368SendDataToCluster`).

## Cluster → phone

`U368DataParser` reads (field getters, positions within the frame being parsed): speed [2], gear [5], fuel % [6],
engine temperature [8], battery voltage [11], average mileage [13], ABS MIL [13], plus clutch, brake, electric-start
switch, ABS, engine-start and sensor statuses, cruising range, lap times, 0-60 and 0-100 times, G-force and crash
detection. Observed (getter names and indexes). Which DID each getter applies to is **Unknown**: read
`U368ClusterDataManager` dispatch and `U368DataParser` constructor. These positions match the Apache
`5A 10` / `5A 11` / `5A 18` classes (APACHE.md), so the Apache layout is the working hypothesis.

## What RideLink needs for the Ronin

1. `RONIN` model in an Apache-like family with: XOR 0xEB on every frame, checksum + XOR on nav, write with response.
2. Status frame above; nav frame above at a 500 ms period (consider RideLink's own resend rule, see the
   SmartXonnect road tests in `PROTOCOL.md`).
3. Telemetry from the Apache layouts, to be confirmed by a Ronin log.
4. No music / social / weather capability flags.
