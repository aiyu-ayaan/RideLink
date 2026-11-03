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
- [ ] Ride data on the Ride screen: odometer, trip, fuel, range, avg/top speed, economy
- [ ] Settings toggles for each feature; update PROTOCOL.md, tests, screenshots

## Needs the real scooter
- [ ] Pair the user's Ntorq XT (2026) and confirm the display accepts the 2025+ status frame
- [ ] Confirm turn arrows show the right pictogram for each maneuver
- [ ] Confirm caller frame clears correctly when a call ends (stock app relies on call state)
- [ ] Capture the display's incoming frames to refine Auto detection (U716 vs U577 base)
- [ ] Check whether the display needs Bluetooth bonding

## Later
- [ ] Map Google Maps non-English instructions (`MapsNotificationParser` is English-only)
- [ ] Missed-call count for the older Ntorq family (needs READ_CALL_LOG)
- [ ] Legacy Ntorq SmartXonnect (VehicleCode "9") uses separate code, not analysed
