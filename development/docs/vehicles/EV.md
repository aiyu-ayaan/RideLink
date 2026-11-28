# Electric vehicles: iQube family, TVS X, U388, others

Byte indexes 0-based. Labels: Observed / Inferred / Unknown. EV ids are the **EV account** numbering
(INVENTORY.md section 1), not the ICE ids RideLink stores today.

## Models and services (Observed: `BleConnectViewModel.startBleService`, `NotificationListenerService.startBleService`, `BikeVariantsComponentImplement.getBikeVariants`)

| Group | BikeTypes | Service | Receiver |
|---|---|---|---|
| U467 group | U_467, U_467_UG, U_467_PLUS, U467S_GEN_3, U546, U467_PLUS_UG, U_710S, U546_V1 | `U467BleHelper` | `IQubeClusterDataReceiver` |
| U347 group | U_347, U_347_UG, U_347_UG_PLUS, U347_GEN_3, U702, U_759, U829_A, U829_B, U829 | `U347BleHelper` | `IQubeClusterDataReceiver` |
| U388 group | U388, IKARUS (TVS X) | `U388BleHelper` | `U388ClusterDataReceiver` |

Service UUID: `TVSMVGSA…` for the iQube groups (`IQubeBLEConnectionConfig`, `IQUBE_SERVICE_UUID`,
`U546_SERVICE_UUID`); `TVSMVGUU…` for U388 and TVS X (`U388_SERVICE_UUID`, `IKARUS_SERVICE_UUID`). MTU 141 for
the U467 family (existing `PROTOCOL.md`). Scan: name contains `TVSiQUBE` or `TVS`. The iQube config is the only one
with `shouldAutoConnect() = true`. Observed.

## Access control: U388 and TVS X (not in scope)

The U388 group runs a challenge-response before the cluster accepts the phone: the cluster sends a challenge frame
(`5A F2`, `U388ChallengeFrameData`), the app answers with an AES-CTR response built from a key embedded in the app
(`BluetoothUtil.cipher`, `KeylessEntryUtils`), and `validateUserID` (`5B 22 <userId> … FF`) binds the TVS account
user id. The same channel carries keyless lock / unlock and trunk commands (`byteArrayForUnLock`,
`U388SpeedometerFrameData` lock and trunk states). Observed.

RideLink does **not** reproduce this: it is vehicle access control, and the response depends on the user's TVS
account. U388 and TVS X are therefore blocked. Record this on issue #20 rather than working around it.

## Status frame `5B 4A` (Observed: `BlePacketsProvider.sendIQubeMobileData`)

| Byte | Value |
|---|---|
| 2 | signal / battery nibbles; battery `getBatteryLevel`: under 10 % → 0, else `ceil(pct / 25)` integer division (0..4) |
| 3 | `0x78` (120) |
| 4 | temperature + 127 (out of range → a default) |
| 5 | `02` |
| 6, 7, 8 | hour, minute, second |
| 9 | AM 0 / PM 1 |
| 10 | missed calls |
| 11 | network type |
| tail | by group, below |
| 19 | `FF` |

Tail (ordinals of `BlePacketsProvider`'s own switch map):
- U347 group without U759 / U829 (U_347, U_347_UG, U_347_UG_PLUS, U347_GEN_3, U702): zero pad.
- U759, U829_A, U829_B, U829: zero pad, call status at [18].
- U_467, U546, U546_V1: call status at [12], zero pad.
- U_467_PLUS, U_467_UG, U467S_GEN_3, U467_PLUS_UG, U_710S: call status [12], `00 00 00` [13..15], voice-assist
  animation [16], `00 00` [17..18].

Call status 1 is replaced by 0 when no call is active (`isCallActive`). Frame XOR: 0xEB for the U467 sub-group and
U_710S, plain for the U347 group, U546, U546_V1, U759, U829 (INVENTORY.md section 5). Observed.

## Battery and range (the EV-only features)

| Data | Frame | Source |
|---|---|---|
| Battery % | `5A 13` and `5A 23` [2] | `U467BatteryPercentage.batteryPercentage`, mapped in `u388/IncomingFrameIdentifier`. Observed |
| Distance to empty | `5A 13` / `5A 23` [3..4] (`d2E`) | same class. Observed |
| Trip A / B | `5A 19`: selected trip [2], trip A distance [3..4], mileage [5], speed [6], average speed [7], trip B distance [8..9], mileage [10], speed [11], average speed [12] | `U546TripData`. Observed (U546 iQubes) |
| Mode distances | eco, power, sport mode distances per ride | `IQubeClusterDataReceiver` (`getEcoModeTotalDistance`, `getPowerModeTotalDistance`). Observed; frame positions Unknown |
| Dialer | `5A 06` | `U388DialerFrameData`. Observed |
| Speedometer | `5A 10/11/19/9D/6D` mapped to `U388SpeedometerFrameData` for the U388 group: DND [2], call mute [6], lock [11], trunk [12], incoming call state [13] | Observed |

Inferred: the iQube groups share the ICE `5A 10` speed / odometer layout for speed and odometer; confirm with an
iQube log, since `IQubeClusterDataReceiver` parses manually.

Battery charging state, charger and SoC history come from the TVS cloud (`GetBatteriesLatestDataQuery`,
`GetHomeChargerDetailsQuery` GraphQL), not from BLE. Observed. RideLink can only show what the cluster sends.

## Navigation

`BleNavigationDataSourceImp.sendNavigationDataToCluster` → `IQubeClusterDataReceiver` branch →
`BleNavigationSendData.byteArrayForNavigation` (the unified two-frame nav). Pictogram mapping through
`getNewPictogramMappingBasedOnClusterVersion` with `isPictogramMappingV1` ("7", the U347) and
`isPictogramMappingV3` (new protocol clusters or ids 10, 15, 17, 20, 29, 46, 47, 48 …). Observed; per-model tables
Unknown (same open item as JUPITER_ZEST.md).

## What RideLink needs for EVs

1. An EV id space (`ClusterModel` entries with an EV flag or separate ids).
2. `ClusterFamily.IQUBE` with the status frame above and per-group XOR.
3. Telemetry: battery %, distance to empty, trip A / B; Ride screen battery tile and range instead of fuel; fuel
   log, refuel detection and low-fuel features hidden for EVs (capability flag `hasFuel = false`).
4. U388 / TVS X: blocked (access control). Say so in the README table.
