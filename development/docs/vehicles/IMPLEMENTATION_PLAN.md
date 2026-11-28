# Implementation plan: all TVS vehicles

Written so a Sonnet (high effort) or Opus (medium effort) session can execute it phase by phase. Each phase is one
commit or a short series (`scope(Change) : Message`), ends with green unit tests, and updates `PROTOCOL.md` +
`ClusterProtocolTest` for every byte layout it implements, citing the stock class from these docs. Read the family
doc before each phase. Hardware is the bottleneck: ship each family behind a manual model pick, mark it 🟡 untested
in the README, and ask owners for Console logs on issue #20.

Ground rules for every phase:
- All BLE writes stay on `ClusterController.send` → `ClusterLink.send` (project rule). The frame transform
  (checksum, XOR) happens in exactly one place in that path.
- Every new user-facing string goes into `ui/src/main/res/values/strings.xml` **and all 22 `values-*` locales**
  (23 folders today). Use English text in the locales only if a translation is not available, and say so in the
  commit.
- No TVS code, assets or branding. Re-implement from the tables in these docs; never paste decompiled code.
- `tools/fake_cluster.py` must learn each new family (decode, plus XOR) so the frames can be checked without a
  vehicle.

## Phase 0: Fixes for shipped models (P0, small)

Docs: NTORQ.md.

1. `core/protocol/ClusterModel.kt`: add `txXor: Int?` and `rxXor: Int?` (or a `FrameCrypto` value) per model:
   U716 `tx 0xEB`, U577 premium `tx 0xEA, rx 0xEA`, all others null.
2. New `core/protocol/FrameCrypto.kt` (pure Kotlin): `encode(frame, key)` XORs bytes `2 .. size-2`;
   `decode` is the same function. Unit tests with hand-built vectors.
3. `ClusterController.send` (or `ClusterLink.send` if the model is known there) applies the TX transform after any
   checksum; the receive path in `ClusterController` (where frames reach `FrameDecoder` / `DisplayTelemetry.merge`
   / `ClusterModel.detect`) applies the RX transform first. Console logs both the plain and the sent bytes.
4. `ClusterProtocolTest`: one vector each for a U716 status frame and a U577 premium nav frame after XOR.
5. `PROTOCOL.md`: new "Frame obfuscation" section citing `BleEngine.writeCharacteristicToCentral`,
   `BluetoothUtil.encryptData` / `encryptDataU577Premium` / `decryptDataForNtorqU577`.
6. README: keep U716 / U577 as 🟡; CHANGELOG line.

Detection: the XOR never touches bytes 0 and 1 (start byte and DID), so DID-based detection (`detect()`) keeps
working on raw, still-encrypted frames and needs no change. Payload-based decisions (Raider LCD from `5A 11` [9],
telemetry parsing, call buttons) must run only after the frame is decoded with the known model's key. Add a test
that `detect()` gives the same answer for a plain and an XORed `5A 29`.

## Phase 1: Multi-family foundation (no new vehicle yet)

Docs: INVENTORY.md, FEATURES.md.

1. `ClusterFamily`: add `N360` (Raider), `APACHE`, `RONIN` (or fold Ronin into `APACHE` with a model flag),
   `IQUBE`. Keep existing values unchanged (they are persisted by name in `GarageStore` and backups).
2. `ClusterModel`: add a `Capabilities` data class (FEATURES.md flag table) with family defaults; replace direct
   `hasTft` / `hasLocation` / `hasMusic` reads with it (keep the old properties as delegates so callers compile).
   Add `theme: Int` and an `ev: Boolean` (EV id space).
3. `ClusterModel.detect()`: only return Ntorq models; never guess another family. Add the `5A 11` [9] model code to
   the Console line and to `GarageStore` so rider logs build the model-code table (only 0xE3 = Raider LCD is known).
4. `ClusterLink`: after `discoverServices`, if `ClusterProtocol.SERVICE` is missing, pick the first service that has
   both `5352` and `5354` characteristics; log every discovered service UUID and its characteristics to the Console
   (`note`). Do not fetch any TVS secret.
5. Per-model link options in `ClusterModel`: requested MTU (512 default; 137 Raider; 141 Apache legacy, Ronin none),
   write type (with / without response), nav resend period. `ClusterLink` reads them when the session starts.
6. UI: `ui/settings/SettingsScreen.kt` model picker grouped by family (Ntorq, Raider, Ronin, Apache, Jupiter /
   Zest, iQube), labels and strings; Ride screen and Settings toggles check capabilities (fuel vs battery tile,
   music, weather, gear, ride mode, range, laps).
7. `BackupCodec` needs nothing for new enum values (stored by name); confirm with a test that an unknown model name
   from a newer backup restores as null (Auto) instead of crashing (`runCatching { valueOf }` already does this in
   `GarageStore`; check `BackupRepository.restore`).
8. Tests: capability defaults per family; detect() never returns non-Ntorq; MTU / write-type table.

## Phase 2: Raider (issue #19)

Docs: RAIDER.md.

1. `ClusterModel.RAIDER` (vehicle 29, family `N360`), capabilities: music (TFT), weather (TFT), social counts, SMS
   sender, gear, range, location, birthday.
2. Firmware-date crypto: parse the BLE name (chars 5 and 6), store `legacyCrypto: Boolean` on the session; TX / RX
   XOR 0xED on data frames when the firmware is before 2023; music frames always XOR 0xED; nav frames always plain.
   Unit-test the name parser (`"TVS??B?…"` → plain, `"TVS??A?…"` → XOR, short names → XOR).
3. `ClusterProtocol`: Raider status frame (RAIDER.md table, battery 0..4); custom line 1 with two parameter bytes;
   artist DID `0x65`; reuse the SmartXonnect nav frame with reroute code 0xFF and the unified Raider pictogram table;
   800 ms stock period (keep RideLink's SmartXonnect resend logic, 2.2 s, unless a rider reports drop-outs).
4. `DisplayTelemetry.merge(frame, family)`: Raider `5A 10` (switch at [11], trip [14..15], RPM [16..17], average
   mileage [13]) and `5A 11` (gear [5], model [9] → TFT / LCD); call buttons read [11] low bits, ignoring bit 7.
5. TFT / LCD: from `5A 11` [9] == 0xE3 → LCD (custom pair 4 times, 250 ms; music capability off until confirmed).
   Read [9] only after decoding: on pre-2023 firmware the payload is XORed with 0xED.
6. `FrameDecoder` names for the Raider frames; Console Feature tests for a Raider status frame and nav sweep.
7. `fake_cluster.py --model raider [--legacy-crypto]`.
8. Ask the issue #19 requester for: BLE name, Console log with RX lines, arrow photos for left / right / U-turn /
   roundabout, call button test.

## Phase 3: Apache and Ronin

Docs: APACHE.md, RONIN.md.

1. Models: APACHE_RR310 (23/0), APACHE_RTR310 (33/0), APACHE_U449 (31), APACHE_U469 (32), APACHE_U796 / U797
   (35, 36), APACHE_N597 (42), RONIN (25). Skip N112 / U507 (old text-led nav) unless requested.
2. Crypto per model (INVENTORY.md section 5): 0xEA RR310 / RTR 310 / U796 / U797 / N597 (N597 RX plain), 0xEB
   U449 / U469 / Ronin; nav frames get the checksum then XOR; writes with response.
3. Status frames: unified Apache layout with the three tails; Ronin layout (RONIN.md). Before coding the legacy
   RR310 / RTR 310 variants, read the unnamed bytes in `U399DataSenderToCluster.sendMobileData` and
   `U400SenderDataToCluster.sendMobileData` (APACHE.md lists them) and record them.
4. Nav: Apache 20-byte frame with stage / prefix rules and the ICE pictogram table (APACHE.md); Ronin frame
   (RONIN.md). Test early whether the Ronin draws raw TVS short-instruction codes (it may need the ICE table, as the
   Race XP needed the U812 table).
5. Telemetry: Apache `5A 10/11/12/16/18`; gear, ride mode, cruising range, lean angle, lap timer. New Ride cards:
   gear and mode tile; race card reused for laps.
6. Capabilities: no social counts, no SMS sender (Apache unconfirmed), music only RTR 310 / unified.
7. `fake_cluster.py --model apache|ronin`.

## Phase 4: Jupiter and Zest

Docs: JUPITER_ZEST.md.

1. First finish the research gap: record the pictogram table for U279 / U745 / U566 / Zest from
   `BleNavigationDataSourceImp.getNewPictogramMappingBasedOnClusterVersion` and `getClusterPictoId`.
2. Models JUPITER_U279, JUPITER_U566, JUPITER_U745 (vehicle 22, themes 3 / 2 / 4) and ZEST_U714 (40) in
   `ClusterFamily.JUPITER`; JUPITER_U408 / U532 (22 / 0, 1) in `SMARTXONNECT` with write type 2 / 1.
3. Zest overspeed default 70; U279 find-my-vehicle flag (first 10 status frames after the user asks).
4. Optional: follow-me headlamp and idle start-stop timers (`5B 73` calibration frames) as Settings entries for
   models with `hasHeadlampTimers`.

## Phase 5: iQube family

Docs: EV.md.

1. EV id space and models for the U467 group and U347 group (not U388 / TVS X).
2. Status frame with the per-group tail; XOR 0xEB TX for the U467 sub-group and U_710S.
3. Telemetry: battery % and distance to empty (`5A 13` / `5A 23`), trip A / B (`5A 19` on U546), plus whatever an
   iQube log shows for speed and odometer.
4. UI: battery tile and range replace fuel; hide fuel log, refuel detection, low-fuel prompt (`hasFuel = false`);
   Care items for EVs (no PUC).
5. README: U388 and TVS X listed as not supported (vehicle access control).

## Phase 6: Old SmartXonnect Ntorq over classic Bluetooth (vehicle 8 and non-TI vehicle 9)

Docs: NTORQ.md. Research first: read `SppSendDataToCluster`, `BluetoothSppService` (service record UUID) and
`TVSMMapActivity.N251SppsendNavigationData`, record the format, then add a classic-Bluetooth link beside
`ClusterLink` (`BluetoothSocket`, RFCOMM) selected by model. Check against closed issue #15.

## Out of scope (do not plan)

Keyless entry, lock / unlock, trunk (U388, TVS X), DigiDoc, caller photo / image transfer, cluster OTA, Wi-Fi
mirroring, cricket / football / news feeds, HUD accessory. NTORQ 150 waits for a newer TVS Connect APK.

## Verification checklist per family

- `./gradlew :core:testDebugUnitTest` green, with byte-exact vectors for every new frame.
- `fake_cluster.py --model <family>` decodes every frame the app sends during Console → Demo ride.
- `./gradlew :app:assembleDebug`; Settings shows the model and only the supported cards.
- README table row added as 🟡 untested; issue #20 checklist updated; owner asked for a Console log.
