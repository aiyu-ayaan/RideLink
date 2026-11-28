# Vehicle support

Goal: support almost every TVS vehicle with a Bluetooth display, so that RideLink can fully replace TVS Connect for
the riding features. Progress is tracked in [issue #20](https://github.com/aiyu-ayaan/RideLink/issues/20).

## Supported

The NTORQ 125 variants. See the [supported scooters table](../README.md#supported-scooters) for their test status.

## Not supported yet

The notes come from static analysis recorded in [`PROTOCOL.md`](../PROTOCOL.md). None of them is confirmed on a real
vehicle, and an empty analysis does not mean the vehicle works differently.

| Vehicle | What is known so far |
|---|---|
| NTORQ 150 | Nothing recorded. |
| Raider ([#19](https://github.com/aiyu-ayaan/RideLink/issues/19)) | Same GATT service and name filter as NTORQ. Status frame tail differs. The navigation-on byte in the control frame is 1/0 (running/stopped) instead of 0/`FF`. Artist text uses DID `0x65`; music checksum behaviour differs. |
| Apache | Same GATT service as NTORQ. Music checksum behaviour differs. |
| Ronin | Nothing recorded. |
| Jupiter (U279, U566, U745) | Same family as the 2025+ NTORQ (status frame and receiver). U566 uses MTU 515. U279 has find-my-vehicle. |
| Zest 110 (U714) | Jupiter family. Overspeed byte is 70. |
| iQube (U467 family) | MTU 141. The stock scan accepts names containing `TVSiQUBE`. Receives SMS sender frames. |
| U388 display family | Needs a user ID frame (`5B 22`) after connecting. Receives SMS sender frames. |

## Report your vehicle

Comment on [issue #20](https://github.com/aiyu-ayaan/RideLink/issues/20) with:

- Vehicle model and year
- Display type (LCD or TFT)
- Whether RideLink finds and connects to the display
- A Console log, if it connects: **Settings → Developer options → Developer mode**, then **Save logs**

Remove names, phone numbers, locations and Bluetooth addresses from the log before you share it.

## Adding a vehicle

1. Record every byte layout in `PROTOCOL.md`, with the stock-app class it came from.
2. Add the model to `ClusterModel` (and a `ClusterFamily` if no existing one fits) in
   `core/.../protocol/ClusterModel.kt`. Check `pictogram`, `weatherIcon`, `hasTft`, `hasLocation`, `hasMusic` and
   `detect`.
3. Update the frame builders in `ClusterProtocol` and the parsers in `DisplayTelemetry`.
4. Check the connection: the scan name filter and MTU in `core/.../ble/ClusterLink.kt`.
5. Add the model label in `SettingsScreen` and its string in `ui/src/main/res/values*/strings.xml`.
6. Add byte-exact tests to `ClusterProtocolTest`, then run `./gradlew :core:testDebugUnitTest`.
7. Move the vehicle to the README table. Mark it tested only after a rider confirms it on the real vehicle.
