# Vehicle research (TVS Connect 8.13.2)

This folder holds the research for supporting every TVS vehicle with a Bluetooth display, so RideLink can
replace TVS Connect for the riding features on all of them (issue #20, Raider request #19). It is research only:
nothing here is implemented yet. When a layout is implemented it moves into `PROTOCOL.md` together with the
encoder and `ClusterProtocolTest`, as the project rules require.

## Files

| File | What it answers |
|---|---|
| [INVENTORY.md](INVENTORY.md) | Every vehicle id in the stock app, which BLE stack and service class runs it, MTU, write type, frame encryption |
| [FEATURES.md](FEATURES.md) | Which feature exists on which family (navigation, calls, music, weather, gear, range, battery, laps...) and the capability flags RideLink needs |
| [RAIDER.md](RAIDER.md) | TVS Raider 125 (vehicle 29, N360): TFT and LCD clusters |
| [RONIN.md](RONIN.md) | TVS Ronin (vehicle 25, U368) |
| [APACHE.md](APACHE.md) | Apache RR310, RTR 310, RTR 4V and the other Apache codes, both stacks |
| [JUPITER_ZEST.md](JUPITER_ZEST.md) | Jupiter (vehicle 22 legacy themes, U279/U566/U745) and Zest U714 |
| [EV.md](EV.md) | iQube family, TVS X, U388 and other EVs: battery, range, the auth challenge |
| [NTORQ.md](NTORQ.md) | New findings that affect the Ntorq models RideLink already ships |
| [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | Phased plan with exact files, tests and verification steps |

## Source and method

- Source: static decompile of `TVS-Connect_8.13.2_apkcube.apks` (base.apk), `com.tvsm.connect` 8.13.2, the same
  build `PROTOCOL.md` cites (sha256 `0fd93260…b3fec9`). jadx 1.5.3: `jadx -j 8 --no-res --show-bad-code` for code,
  `jadx --no-src` for resources. The code pass takes about 20 minutes.
- A copy of the relevant decompiled packages is kept in the gitignored `jadx/src-8.13.2/` folder (BLE, protocol,
  legacy bluetooth, map activity, utils, decoded `strings.xml`, `res/raw/*.json`). `jadx/src-8.13.2/meta.py` dumps
  every `@ParsingMeta` frame class as 0-based layouts; its output is `jadx/src-8.13.2/frames.txt`. Never commit
  that folder.
- Nothing here was observed on a real vehicle other than the Ntorq models listed in the README. Every claim is
  static evidence from the stock app, labelled:
  - **Observed**: read directly in the decompiled code (class and method cited).
  - **Inferred**: a conclusion drawn from several observed facts, not stated in one place.
  - **Unknown**: not resolved; the doc names the class to read next or the capture that would answer it.
- Byte indexes are **0-based** everywhere in these docs. `@ParsingMeta(start = n)` in the stock app is 1-based, so
  `start = 3` is index 2 here.
- Product names next to internal codes (U368 = Ronin and so on) come from strings in the app (`KEYS.SERIES_*`,
  `strings.xml`). Where the app has no string, the name is marked Inferred.

## The two stock BLE stacks (read this first)

The stock app carries two independent BLE implementations. Which one runs depends on the vehicle id, and the frame
formats differ between them, so every family doc states which stack is live.

1. **Legacy stack** (`com.tvsm.connect.bluetooth.bluetoothlegatt.*HelperService`, all extending
   `BaseConnectHelperService`). One helper service per model, a sender class in `bluetooth/sendreceive/` and a
   receiver class next to it. GATT UUIDs are read from preferences filled from the TVS server (`SecretIdentifier`
   keys such as `SERVICE_UUID_N360C`); they are not in the APK. Navigation frames are built in
   `com.tvsm.connect.map.ui.view.TVSMMapActivity`. RideLink's SmartXonnect support (vehicle 9) already follows
   this stack.
2. **Unified stack** (`com.tvs.ble.feature.handler.BleEngine`, services `JupiterBleService`, `NtorqBleHelper`,
   `ApacheBleService`, `U467BleHelper`, `U347BleHelper`, `U388BleHelper`). Frame builders are in
   `com.tvs.bluetooth.core.utils.BlePacketsProvider` and `com.tvs.ble.feature.datasender.*MobileToCluster`;
   receivers in `com.tvs.ble.feature.datareceiver`; frame classes with `@ParsingMeta` in
   `com.tvs.bike.core.protocol`. RideLink's U716, U577 and 2025+ Ntorq support follows this stack.

Routing is in `com.tvs.bike.core.di.BikeVariantsComponentImplement.getBikeVariants()` (unified) and in
`DashboardBikeListAdapter`, `HomeActivity.onResume`, `unified/ui/fragment/DashboardFragment` and
`BluetoothDeviceListActivity.connectToBike` (legacy). See [INVENTORY.md](INVENTORY.md).

## Not in scope

- Keyless entry, lock/unlock, trunk, immobiliser and anything else that grants access to a vehicle (EV U388 and
  TVS X). The stock app authenticates those with a challenge-response; RideLink does not reproduce it.
- Document transfer (DigiDoc), caller photo / image transfer, cluster OTA, Wi-Fi screen mirroring, HUD accessory,
  Garmin, and cricket / football / news feeds that depend on TVS servers. They are listed in FEATURES.md so the
  matrix is complete, but no plan covers them.
- Fetching any TVS server secret. RideLink finds the GATT service by its characteristics instead
  (IMPLEMENTATION_PLAN.md, phase 1).
