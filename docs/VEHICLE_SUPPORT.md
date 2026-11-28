# Vehicle support

Goal: support almost every TVS vehicle with a Bluetooth display, so that RideLink can fully replace TVS Connect for
the riding features. Progress is tracked in [issue #20](https://github.com/aiyu-ayaan/RideLink/issues/20).

## Supported

The NTORQ 125 variants. See the [supported scooters table](../README.md#supported-scooters) for their test status.

## Not supported yet

The notes come from static analysis of the official app. None of them is confirmed on a real vehicle.

| Vehicle | What is known so far |
|---|---|
| NTORQ 150 | Not in the TVS Connect version analysed; needs a newer one. |
| Raider ([#19](https://github.com/aiyu-ayaan/RideLink/issues/19)) | SmartXonnect-style navigation with its own arrow table. TFT and LCD clusters are told apart by the model code the display sends. Older cluster firmware obfuscates frames. Status frame, gear and trip data mapped. |
| Apache | RR310, RTR 310, RTR 4V and basic Apache codes mapped. Frames are obfuscated per model. Gear, ride mode, range and lap timer data mapped. |
| Ronin | Mapped. Frames are always obfuscated. Status and navigation frames recorded. |
| Jupiter (U279, U566, U745) | Same family as the 2025+ NTORQ (status frame and receiver). U566 uses MTU 515. U279 has find-my-vehicle. Arrow tables still to record. |
| Zest 110 (U714) | Jupiter family. Overspeed byte is 70. |
| iQube | Status frame, battery % and range mapped. Fuel features would be hidden. |
| U388 and TVS X | Not planned: the display requires an account-bound security handshake. |

Detailed research notes live in the maintainer's development docs.

## Report your vehicle

Open the [vehicle report form](https://github.com/aiyu-ayaan/RideLink/issues/new?template=vehicle_report.yml), or comment on [issue #20](https://github.com/aiyu-ayaan/RideLink/issues/20), with:

- Vehicle model and year
- Display type (LCD or TFT)
- Whether RideLink finds and connects to the display
- A Console log, if it connects: **Settings → Developer options → Developer mode**, then **Save logs**

Remove names, phone numbers, locations and Bluetooth addresses from the log before you share it.

## Adding a vehicle

Follow the phased plan in `development/docs/vehicles/IMPLEMENTATION_PLAN.md`. In short: every byte layout goes into
`PROTOCOL.md` with byte-exact tests in `ClusterProtocolTest`, each model gets capability flags so only supported
features show, and a vehicle is marked tested only after a rider confirms it on the real vehicle.
