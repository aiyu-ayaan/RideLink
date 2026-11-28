# Findings for the Ntorq models RideLink already supports

These come out of the all-vehicle research and change, or may change, behaviour of shipped models. Labels:
Observed / Inferred / Unknown.

## P0: U716 and U577 premium frames are XOR-encrypted in stock

`BleEngine.writeCharacteristicToCentral` (unified stack, the stack that runs U716 / U577) XORs bytes `2 .. n-2` of
every frame before writing (Observed, exact switch table checked with a script, see INVENTORY.md section 5):

| Model | TX | RX | RideLink today |
|---|---|---|---|
| NTORQ_U716 (34) | XOR 0xEB | plain | plain |
| NTORQ_U577_BASE (37) | plain | plain | plain (correct) |
| NTORQ_U577_PREMIUM (38) | XOR 0xEA | XOR 0xEA | plain both ways |
| 2025+ Ntorq (39, 43, 44, 45) | plain | plain | plain (correct) |
| SmartXonnect (9) | plain | plain | plain (correct, confirmed on the rider's Race XP) |

So a real U716 or U577 premium cluster would most likely ignore RideLink's frames, and RideLink would misread a
U577 premium's telemetry. Neither model has been tested on a real scooter (README table). `isChecksumNeeded` frames
(social counts, music) get the checksum first, then the XOR. Fixed in phase 0 (2026-11-29): `FrameCrypto` in
`ClusterController.send` and the receive path, keyed by the model picked in Settings (Auto stays plain). Untested on
hardware.

## Resolved: U577 premium pictogram table

The unified U577 premium branch passes `getClusterPictogramMappingForU577PremiumMappls(id)` through
`Integer.parseInt(…, 16)` (Observed, `BleNavigationDataSourceImp.sendNavigationDataToCluster`, vehicle "38"), which
is what RideLink's `PREMIUM_U577` table and `PROTOCOL.md` already do. For the keys RideLink produces the values
match. No change needed; the full stock table also covers 12>10, 14>10, 17>1, 18>11, 19>2, 20>12, 21>57, 24>10,
25>10, 36>23, 41>19, 50..57>57, 65..71>27..33, 72>61, 73>7, 74>7, 75>17 if more maneuvers are added.

## P0: vehicle 8 (`N251Spp`) is classic Bluetooth, not BLE

- Account vehicle id 8 (`NTORQ_STANDARD_SPP`, first-generation Ntorq 125) is handled by `BluetoothSppService`, a
  classic RFCOMM / SPP socket, not GATT. Observed (`DashboardBikeListAdapter` sets bike type `N251Spp` for "8";
  `TVSMMapActivity.N251SppsendNavigationData`; `SppSendDataToCluster`).
- Vehicle 9 clusters also come in two radios: names starting `TVSBT` / `TVSNT` are the TI BLE chip; other `TVS`
  names **without** `LE` start the SPP service (`BluetoothDeviceListActivity`, connect branch for `isN251Bike()`).
  Observed.
- Stock finds these clusters with its **BLE** scanner (the SPP branch runs right after
  `bluetoothLeScanner.stopScan`), so they are probably dual-mode: they appear in RideLink's scan list, but GATT
  service discovery then finds no usable TVS service and the link keeps retrying. Inferred. That matches closed
  issue #15 ("Bluetooth detected, but it keeps on searching", Ntorq 2019), which was answered with pairing-mode
  advice, not a fix; it is a possible cause, unconfirmed. A Console log from such a scooter (service list from
  phase 1 of the plan) would confirm it. Supporting them needs a classic-Bluetooth link (`BluetoothSocket`,
  RFCOMM) next to `ClusterLink`; the SPP frame format is in `SppSendDataToCluster` and `N251SppsendNavigationData`
  (not yet read).

## Detection hazard

`ClusterModel.detect()` maps `5A 16` / `5B 42` to SmartXonnect and `5A 29` / `5A 6B` to U577 premium. Apache sends
`5A 16` and `5A 29`, Raider and U388 send `5A 6B`. Harmless today (only Ntorq models are offered), but auto-detection
must stay limited to the Ntorq family once other families exist. See INVENTORY.md section 6.

## NTORQ 150

Not present in TVS Connect 8.13.2 (no vehicle code, string or class). Needs a newer APK. Unknown.
