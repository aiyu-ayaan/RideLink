# Vehicle inventory

Every vehicle id the stock app knows, which stack runs it, and how frames go on the wire. Byte indexes 0-based.
Labels: Observed / Inferred / Unknown (see README.md).

## 1. Two id spaces

The stock app uses **two different vehicle-id numberings**. They collide (9 is Ntorq SmartXonnect in one and
iQube U347 UG in the other), so never mix them.

- **ICE account ids** (`com.tvsm.connect.utils.KEYS.VehicleTypeIdList`, `com.tvsm.unified.utils.VehicleCode`,
  `com.tvsm.connect.common.BikeType`). The account returns `vehicleTypeId` plus a `theme`; the pair picks the
  model (`BikeVariantsComponentImplement.getBikeType()` matches value **and** theme).
- **EV account ids** (`com.tvs.bluetooth.core.utils.BluetoothConstants.VEHICLE_TYPE_*`), used by the iQube
  side of the app (`com.tvsm.ev`).

RideLink's `ClusterModel.vehicleId` stores the ICE id. EV models need a separate field or a namespaced id.

## 2. ICE vehicles (Observed: `VehicleCode`, `BikeType`, `VehicleTypeIdList`, `DashboardBikeListAdapter`)

| Id | Theme | Code | Product (source) | Stack and service (dispatch site) |
|---|---|---|---|---|
| 3 | 0 | APACHE_N112 | Apache, `apache112` (Inferred: older Apache RTR 200 4V SmartXonnect) | Legacy `U324HelperService` → `N112SendDataToCluster` / `N112ClusterDataReceiver` (`HomeActivity`, `DashboardFragment`) |
| 6 | 0 | N109 | `n109` (Unknown product) | Legacy, `N109ClusterDataReceiver` |
| 7 | 0 | U347 | iQube on the ICE account (`pref_bike_type_is347`) | Legacy N109 path |
| 8 | 0 | NTORQ_STANDARD_SPP | Ntorq 125 first generation, `N251Spp` | Data over **classic Bluetooth SPP** (`BluetoothSppService`, RFCOMM) after a BLE scan finds it. See NTORQ.md |
| 9 | 0..8 | NTORQ_* (Standard, Racing Edition, Super Squad x3, XP, XT, Spider, Thor) | Ntorq 125 SmartXonnect | Legacy `N251HelperService` (RideLink `NTORQ_SMARTXONNECT`) |
| 10 | - | NonIOT | Apache (non-connected) | none |
| 11-17, 21 | - | JUPITER, XL_SUPER, Sport, EFI_200, ZEST, Wego, XL100 | Non-connected models | none (listed in `VehicleTypeIdList` only) |
| 22 | 0 | JUPITER_U408 | Jupiter 125 SmartXonnect (`u408`) | Legacy `U408HelperService` |
| 22 | 1 | JUPITER_U532 | Jupiter (theme U532) | Legacy `U408HelperService`, write type 1 |
| 22 | 2 | JUPITER_U566 | Jupiter (`jupiter_566`) | Legacy `N251HelperService` (theme U566), and unified `JupiterBleService` (`BikeType.JUPITER_U566`) |
| 22 | 3 | JUPITER_U279 | Jupiter (find-my-vehicle theme) | Unified `JupiterBleService` |
| 22 | 4 | JUPITER_U745 | Jupiter | Unified `JupiterBleService` |
| 23 | 0 | APACHE_U399 / U399C | Apache RR310 (`SERIES_U399 = "RR Series"`, `bike_rr_310`) | Legacy `U399HelperService` |
| 23 | 1 | APACHE_U490 | RR310 variant (Inferred) | Legacy `U399HelperService`; unified Apache list |
| 23 | 2 | APACHE_U713 | RR310 variant (Inferred) | Legacy `U399HelperService`; unified `ApacheBleService` |
| 25 | 0 | ROHIN_U368 | **Ronin** (`SERIES_U368 = "RONIN"`, `KEYS.isRonin("25")`) | Legacy `U368HelperService` |
| 27 | 0 | APACHE_U507 | Apache RTR 160 4V (`KEYS.APACHE160 = "apache507"`, `isApache160`) | Legacy `U324HelperService` → `U507SendDataToCluster` when the service UUID is the U507 one, else N112 classes |
| 28 | - | Apache RTR 160 non-IoT | non-connected | none |
| 29 | 0 | RIDER_N360 / RAIDER | **Raider 125** (`forgetTvsRaider`, `SERIES_RAIDER`) | Legacy `N360HelperService` is live (dashboards); unified `BikeVariants.Raider` (NtorqBleHelper + `RaiderClusterDataReceiver`) also exists. See RAIDER.md |
| 31 | 0 | APACHE_U449 | Apache (Inferred: RTR 160 4V / 200 4V newer generation) | Legacy `U449HelperService`; unified `ApacheBleService` |
| 32 | 0 | APACHE_U469 | Apache (Inferred, sibling of U449) | Legacy `U449HelperService`; unified `ApacheBleService` |
| 33 | 0 | APACHE_U400 | Apache RTR 310 (`SERIES_U400 = "Apache RTR 310"`) | Legacy `U400HelperService` |
| 33 | 1 | APACHE_U712 | RTR 310 variant (Inferred) | Legacy `U400HelperService` (id 33) and unified `ApacheBleService` (`BikeType.APACHE_U712`); which wins is Unknown |
| 34 | 0 | NTORQ_U716 | Ntorq 125 | Unified `NtorqBleHelper` (RideLink `NTORQ_U716`) |
| 35 | 0 | APACHE_U796 | Basic Apache (`SERIES_APACHE_U796_797 = "U711"`, `isBasicApacheSeries`) | Unified `ApacheBleService` |
| 36 | 0 | APACHE_U797 | Basic Apache | Unified `ApacheBleService` |
| 37 | 0 | NTORQ_577_BASE | Ntorq LCD (`U577-Base(BLE)`) | Unified `NtorqBleHelper` |
| 38 | 0 | NTORQ_577_PREMIUM | Ntorq hybrid TFT (`U577-Premium(TLE)`) | Unified `NtorqBleHelper` |
| 39 | 0 | NTORQ_U812 | Ntorq 2025+ | Unified `JupiterBleService` |
| 40 | 0 | ZEST_U714 | Zest 110 | Unified `JupiterBleService` |
| 42 | 0 | APACHE_N597 | Apache (`N597`, product Unknown) | Unified `ApacheBleService` (legacy scan list also has `n597_bike_type`) |
| 43 | 0 | NTORQ_N251_STANDARD_NEW | Ntorq 2025+ | Unified `JupiterBleService` |
| 44 | 0 | NTORQ_U458_RACE_NEW | Ntorq 2025+ Race | Unified `JupiterBleService` |
| 45 | 0 | NTORQ_U377_SUPER_SQUAD_NEW | Ntorq 2025+ Super Squad | Unified `JupiterBleService` |

- **NTORQ 150 is not in 8.13.2** (no code, string or id). Support needs a newer TVS Connect APK. Unknown.
- The Ronin's vehicle id 25 is **not** in `BikeType`, so the unified stack would fall through to the iQube variant
  for it (`getBikeVariants()` default). Only the legacy `U368HelperService` handles the Ronin. Observed.

## 3. EV vehicles (Observed: `BluetoothConstants.VEHICLE_TYPE_*`, `BikeType`, `BleConnectViewModel.startBleService`)

| EV id | BikeType | Product (source) | Service |
|---|---|---|---|
| 7 | U_347 | iQube (2020, `SERIES_U347`) | `U347BleHelper` |
| 9 | U_347_UG | iQube upgrade | `U347BleHelper` |
| 14 | U_347_UG_PLUS | iQube | `U347BleHelper` |
| 16 | U347_GEN_3 | iQube gen 3 | `U347BleHelper` |
| 8 | U_467 | iQube S/ST family (`SERIES_U467`, Inferred product) | `U467BleHelper` |
| 10 | U_467_PLUS | iQube (`467plus`, `isIQubeSTBikes`) | `U467BleHelper` |
| 13 | U_467_UG | iQube | `U467BleHelper` |
| 15 | U467S_GEN_3 | iQube S gen 3 | `U467BleHelper` |
| 19 | U467_PLUS_UG | iQube (`isIQubeSTBikes`) | `U467BleHelper` |
| 17 | U546 | iQube (`SERIES_U546`) | `U467BleHelper` |
| 47 | U546_V1 | iQube | `U467BleHelper` |
| 46 | U_710S | iQube (`SERIES_U710 = "iQUBE"`) | `U467BleHelper` |
| 11 | U388 | `U388` (product Unknown; `isEVBike` lists it) | `U388BleHelper`, own service UUID, auth challenge |
| 21 | IKARUS | **TVS X** (`SERIES_TVSX`, `UtilsIkarusOnboarding`) | `U388BleHelper` path, auth challenge |
| 18 | U702 | Unknown EV | `U347BleHelper` |
| 20 | U_759 | Unknown EV (`u759`) | `U347BleHelper` |
| 48, 49, 52 | U829_A, U829_B, U829 | Inferred: TVS Orbiter (`SERIES_ORBITER` exists) | `U347BleHelper` |

## 4. GATT, MTU, write type, scan name

All families use the notify characteristic `00005354-…` and the write characteristic `00005352-…`. Observed in
`*BLEConnectionConfig` (unified) and, for legacy, by the same characteristic names in `SampleGattAttributes`.

| Family | Service UUID | MTU requested | Write type | Scan name rule |
|---|---|---|---|---|
| Unified Ntorq, Jupiter, Apache, iQube | `5456534D-5647-5341-5342-454E544F5251` (`TVSMVGSASBENTORQ`) | 512 default; 515 U577 base / U566; 141 iQube U467 family (existing PROTOCOL.md) | Jupiter family writes WITHOUT_RESPONSE (`writeCharacteristicJupiter`); Ntorq, Raider follow the characteristic; Apache WITH_RESPONSE | name contains `TVS`, not `HUD` (`BleEngine.onPeripheralScan`) |
| U388, TVS X | `5456534d-5647-5555-5342-454e544f5251` (`TVSMVGUU…`, `BluetoothConstants.U388_SERVICE_UUID`, `IKARUS_SERVICE_UUID`) | Unknown | 2 | name contains `TVSiQUBE` or `TVS` |
| Legacy N251 (vehicle 9) | server secret `SERVICE_UUID_N251`; RideLink's rider proves it is `TVSMVGSA…` | 246 (`N251HelperService`) | 1 (no response) | `TVSBT`/`TVSNT` = TI chip; other `TVS` names without `LE` = classic SPP |
| Legacy N360 (Raider) | server secret `SERVICE_UUID_N360C` (Inferred: `TVSMVGSA…`, shared service in `PROTOCOL.md`) | 137 (`N360HelperService`) | 1 for status (`N360DataSenderToCluster.writeData` sets write type 1), nav via `writeNavigationData` | `TVS` |
| Legacy U368 (Ronin), U399, U449, U507 | server secret `SERVICE_UUID_U399C` | 141 for U399 (`KEYS.U399_CLUSTER_MTU`); Ronin and U449 do not call `requestMtu` | 2 (with response) | name starts with `TVS` (`BluetoothDeviceListActivity` scan callback) |
| Legacy U400 (RTR 310) | server secret `SERVICE_UUID_RTR_U400` | 141 (`KEYS.U400_CLUSTER_MTU`) | 2 | `TVS` |
| Legacy N112 | server secret `SERVICE_UUID_N112` / `SERVICE_UUID_U507` | Unknown | 1 | any name |

The legacy service UUIDs are server-delivered and are **not** in the APK (Observed: only `SecretIdentifier` keys).
The only literal service UUIDs in the app are `TVSMVGSA…`, `TVSMVGUU…` and the EV default
`86307704-0382-9099-5342-454E544F5251` (`com.tvsm.ev.utils.CONSTANT.DEFAULT_SERVICE_UUID`). Inferred: every ICE
cluster serves `TVSMVGSA…`; RideLink should still locate the service by its `5352`/`5354` characteristics and log
every service it discovers (IMPLEMENTATION_PLAN.md phase 1), so a rider's Console log settles it.

## 5. Frame encryption (XOR obfuscation)

Several clusters XOR bytes `2 .. n-2` of every frame with one constant byte (start byte, DID and the trailing `FF`
stay plain). Some also need the checksum `255 - (sum of bytes 0..17) mod 256` in byte 18 first. This is
obfuscation of display data, not access control.

### Unified stack (Observed: `BleEngine.writeCharacteristicToCentral` TX, `BleEngine` receive switch at the
`decryptDataForApache` call RX, both on `WhenMappings.$EnumSwitchMapping$0`; keys in
`com.tvs.bluetooth.core.utils.BluetoothUtil.getKeyByteArray*`)

| Model | TX | RX |
|---|---|---|
| Jupiter family (U279, U566, U745, U812, Zest U714, N251 new, U458 new, U377 new) | plain | plain |
| U577 base (37) | plain | plain |
| **U577 premium (38)** | **XOR 0xEA** (`encryptDataU577Premium`) | **XOR 0xEA** (`decryptDataForNtorqU577`; also in `NtorqClusterDataReceiver.parseData` when vehicle id is 38) |
| **U716 (34)** | **XOR 0xEB** (`encryptData`) | plain |
| Raider (29), unified path | encrypted copy computed with 0xED, but the write sends the **plain** frame except music frames (`isMusicData`), which go checksum + 0xED | plain (Raider receiver only decrypts when vehicle id is 38, which never matches) |
| Apache series except U449/U469 (U796, U797, N597, U713, U712, U399, U400, U490) | XOR 0xEA (`encryptDataApache`) | XOR 0xEA (`decryptDataForApache`), except N597 which is plain |
| Apache U449, U469 | XOR 0xEB (`encryptU449Data`) | XOR 0xEB (`decryptU449Data`) |
| iQube U467 family (U_467, U_467_PLUS, U_467_UG, U467S_GEN_3, U467_PLUS_UG), U_710S, U388 | XOR 0xEB | plain (RX for U388 has its own `decryptData` in `U388ClusterDataReceiver`) |
| iQube U347 family, U702, U546, U546_V1, U759, U829, U829_A, U829_B | plain | plain |

`isChecksumNeeded = true` (social counts, music) adds the checksum before the XOR.

### Legacy stack (Observed: sender `writeData` methods, `TVSMMapActivity.writeDataFor*`, `security/*EncryptDecryptDataUtils`)

| Model | Status / data frames | Navigation frames | RX |
|---|---|---|---|
| N251 SmartXonnect (9), U408 Jupiter (22) | plain (`N109U347N251DataSenderToCluster`) | plain | plain |
| **N360 Raider (29)** | XOR **0xED** only when the cluster firmware is older than 2023 (see RAIDER.md); music frames always XOR 0xED (`writeMusicData`) | plain, no checksum (`BaseConnectHelperService.writeNavigationData`) | same date rule (`N360HelperService` decrypts with 0xED for old firmware) |
| **U368 Ronin (25)** | always XOR **0xEB** (`U368SendDataToCluster.writeData`) | checksum + XOR 0xEB (`writeDataForU368`) | decrypt 0xEB, checksum check `U399ChecksumUtils.isValidChecksumU368` |
| U399 RR310 (23) | always XOR **0xEA** | checksum + 0xEA (`writeDataForU399`) | decrypt 0xEA |
| U400 RTR 310 (33) | always XOR **0xEA** (uses `U399EncryptDecryptDataUtils`) | checksum + 0xEA (`writeDataForU400`) | decrypt 0xEA |
| U449 / U469 (31, 32) | always XOR **0xEB** (uses `U368EncryptDecryptDataUtils`) | checksum + 0xEB (`writeDataForU449`) | decrypt 0xEB |
| U507 (27, new UUID) | always XOR 0xEB | checksum + 0xEB (via `writeDataForU449`) | decrypt 0xEB |
| N112 (3), U507 old UUID | plain, write type 1 | plain | plain |

The disconnect frame (`5B 4A 01 00 … 10 11 12 FF`, `sendDisconnectCommandToCluster`) is written plain on every
legacy model.

## 6. Model detection on the wire

The display never sends the account vehicle id. What it does send (Observed):

- `5A 11` [9] vehicle model and [7] software version on every family that sends `5A 11`. The only value the stock app
  interprets is **0xE3 = Raider LCD cluster** (`N360ClusterDataReceiver`, `RaiderClusterDataReceiver`
  `handleSpeedOMeter`). Every other code is Unknown and must be collected from rider logs (RideLink already shows
  it in Settings: "Display reports model code").
- Family-specific DIDs: `5A 16` and `5B 42` SmartXonnect Ntorq; `5A 29` and `5A 6B` older Ntorq (U716/U577); but
  Apache also sends `5A 16` (lap timer, `ApacheSpeedOMeter4`) and `5A 29` (`ApacheBasicData`), and Raider and U388
  send `5A 6B` (music controls). **RideLink's `ClusterModel.detect()` would misclassify an Apache as SmartXonnect and
  a Raider as U577 premium.** Inferred. IMPLEMENTATION_PLAN.md phase 1 restricts auto-detection to the Ntorq family
  and makes the user pick other families.
