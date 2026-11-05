# TODO

## Done
- [x] Recover BLE protocol from TVS Connect 8.13.2 (`PROTOCOL.md`)
- [x] Fake display (`tools/fake_cluster.py`) and two-adapter BLE test (`tools/central_test.py`, PASS)
- [x] Frame encoder for both display families with byte-exact unit tests
- [x] BLE link, foreground ride service, boot restart
- [x] Google Maps directions and incoming calls from notifications
- [x] Auto model detection (older Ntorq via 5A 6B / 5A 29 frames)
- [x] Ride dashboard (turn arrow, distance, ETA, left, speed), demo ride, console

## Next (see development/HANDOFF.md for byte layouts)
- [x] Fix speed byte index: `frame[2]`, not `frame[3]` (ParsingMeta is 1-based)
- [x] Auto-connect when paired (no "Start ride" needed); button becomes Disconnect
- [x] Request READ_PHONE_STATE + ACCESS_COARSE_LOCATION so signal and network type are real
- [x] Social notification counts `5B 9C` (WhatsApp/Facebook/Instagram); drop the sender-name hack
- [x] Weather + AQI `5B 5D` with screen switch `5B 4C 00 78` (Open-Meteo, no key) + temp in status
- [x] Current location name `5B 5C`
- [x] Music: song `5B 62`, artist `5B 63`, play state `5B 60`
- [x] Call accept/reject from scooter buttons (5A 10 byte 12) with ANSWER_PHONE_CALLS
- [x] Ride data on the Ride screen: odometer, trip, fuel, range, avg/top speed, economy
- [x] Settings toggles for each feature; PROTOCOL.md and tests updated
- [x] Overspeed alert limit and missed-call badge in the status frame
- [x] Vehicle card (5A 18 + diagnostics) and model code reported by the display in Settings
- [x] Custom display text lines, reject-with-SMS message, voice assistant button
- [ ] Cricket / football / news, vehicle settings, image transfer, 5A 6B music controls (need the decompile shared)
- [ ] Build the APK, run Demo ride on the emulator and refresh screenshots (blocked in the cloud session: dl.google.com not reachable)
- [ ] Re-run fake_cluster.py with a real phone to check every new frame decodes

- [x] Analytics tab: automatic ride recording, period stats, charts, records, ride history, CSV export, sample rides

- [x] Logo ("Closed loop"), launcher + themed + notification icons, animated splash screen

## Needs the real scooter
- [ ] Analytics: check recorded distance against the scooter's trip meter on a real ride
- [ ] Trip meter: 5A 10 [14..16] reads empty on the rider's XT; get a Console "Copy log" with RX frames
- [ ] Signal bars, call icon, notification badge: still missing? compare decoded STATUS lines, try model U577 premium
- [ ] Pair the user's Ntorq 125 Race XP (2026, U458) and confirm the display accepts the 2025+ status frame
- [ ] Race XP (SmartXonnect LCD): left family arrows confirmed (ids 0/1/2); check right 10, sharp right 11, slight right 12, keep left 7 / right 17, U-turn 19, destination 22, straight 57, roundabout 48 / exits 28 / 32 with Demo navigation (Console -> Arrow codes 0 to 80 to see any other id)
- [ ] SmartXonnect U445B / U566 themes: alert block, code remap and 5A 49 control 2 not implemented (theme id comes from the TVS account)
- [ ] Real Maps route on the scooter: Console should show `Maps: <street> = <TURN> (icon ... 1.00)`; check the meter stays in nav mode between Maps updates (2 s repeat)
- [ ] Calls / WhatsApp on the Race XP: retest with battery optimisation off + wake lock; Console notes show what was detected
- [ ] Confirm caller frame clears correctly when a call ends (stock app relies on call state)
- [ ] Capture the display's incoming frames to refine Auto detection (U716 vs U577 base)
- [ ] Check whether the display needs Bluetooth bonding

## Later
- [ ] Turn arrow fallback if a Maps update renames its `maneuver_*` drawables (Console note then says `0 Maps icons`)
- [ ] Legacy Ntorq SmartXonnect (VehicleCode "9") uses separate code, not analysed
