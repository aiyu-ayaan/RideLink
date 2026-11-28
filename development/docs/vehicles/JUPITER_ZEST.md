# Jupiter and Zest

Byte indexes 0-based. Labels: Observed / Inferred / Unknown.

## Models and stacks

| Id / theme | Code | Live stack | Notes |
|---|---|---|---|
| 22 / 0 | JUPITER_U408 (Jupiter 125 SmartXonnect) | Legacy `U408HelperService`, sender `N109U347N251DataSenderToCluster` (the Ntorq SmartXonnect sender), receiver `U408ClusterDataReceiver` | write type 2 |
| 22 / 1 | JUPITER_U532 | Legacy `U408HelperService` | write type 1 (`isU532Theme`) |
| 22 / 2 | JUPITER_U566 | Legacy `N251HelperService` (U566 theme) in the dashboards; unified `JupiterBleService` (`BikeType.JUPITER_U566`) | stock MTU 515 (existing `PROTOCOL.md`) |
| 22 / 3 | JUPITER_U279 | Unified `JupiterBleService` | only theme with find-my-vehicle (`EXTRA_IS_FIND_ME`) |
| 22 / 4 | JUPITER_U745 | Unified `JupiterBleService` | |
| 40 | ZEST_U714 | Unified `JupiterBleService` | |

Sources: `BikeVariantsComponentImplement.getBikeVariants`, `DashboardBikeListAdapter`, `HomeActivity.onResume`,
`BluetoothDeviceListActivity.connectToBike`, `U408HelperService`. Observed.

## What already works in RideLink

The unified Jupiter family is the protocol RideLink's 2025+ Ntorq models use (`ClusterFamily.JUPITER`): status frame
`sendJupiterMobileData`, nav `byteArrayForIceBikeNavigation`, `JupiterClusterDataReceiver`, plain frames, writes
without response. Observed in `BleEngine.writeCharacteristicToCentral` (Jupiter list → `writeCharacteristicJupiter`
WITHOUT_RESPONSE, no XOR). So U279, U745, U566 (unified) and Zest U714 mostly need:

- new `ClusterModel` entries with their vehicle id and theme;
- the Zest overspeed default (existing `PROTOCOL.md`: 70 when the alert is off, 120 for the others);
- their pictogram table (below);
- find-my-vehicle on U279 (existing `PROTOCOL.md` describes the status-frame flag);
- `5A 18` barometric pressure at [6] on the Zest (`JupiterSpeedOMeter4.barometricPressureU714`). Observed.

The legacy U408 / U532 themes use the same sender as RideLink's Ntorq SmartXonnect (`N109U347N251DataSenderToCluster`)
and the same navigation method (`N251N109U347sendNavigationData`, 800 ms), so they map onto
`ClusterFamily.SMARTXONNECT` with a different receiver (`U408ClusterDataReceiver`, not yet read) and write type.
Inferred.

## Pictogram tables (Observed: `BleNavigationDataSourceImp.sendNavigationDataToCluster`)

- U812, N251 new, U458 new, U377 new: `getPictogramMappingForNtorqU812` (RideLink's `NEW_NTORQ` table).
- Other Jupiter-receiver models (U279, U745, U566, Zest): `getNewPictogramMappingBasedOnClusterVersion`, except
  U566 with cluster software version > 7, which takes the other branch of the same line. Inside that function the
  U279 / U745 / U812 / Zest / new Ntorq models skip the big switch and go through a map lookup further down; the
  switch is keyed on a different id space (1..60) than the Mappls ids RideLink produces. **Unknown**: read
  `getNewPictogramMappingBasedOnClusterVersion` and `getClusterPictoId` in full and record each model's table before
  implementing. Until then, sending the U812 table to a Jupiter is a guess.

## Cluster → phone (Observed: `com.tvs.bike.core.protocol.jupiter.*`)

Same as RideLink's existing Jupiter-family parsing (`PROTOCOL.md`, "Display -> phone ride data"), plus the Zest
barometric byte above and `5A 37` calibration responses (follow-me headlamp and idle start-stop timers,
`JupiterCalibrationResponse`).

## Extras the stock app sends to this family

- Follow-me headlamp timer and idle start-stop timer: `5B 73` calibration frames (`sendCalibrationUpdate`, ids 18
  and 9), U577 variants through `sendVehicleTimerControl` (`5A F2 …`). Observed.
- Vehicle control `5A F1` (`sendVehicleControl`): illumination, mode, TSL, date, clear-call flag; U566 has its own
  branch. Observed.

## What RideLink needs

1. `ClusterModel` entries for U279, U745, U566, Zest U714 (and U408 / U532 under SmartXonnect).
2. The per-model pictogram tables once read (Unknown above).
3. Zest overspeed default, U279 find-my-vehicle flag.
4. Model labels and strings.
