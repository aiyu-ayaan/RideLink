# TODO

## Done
- [x] Recover BLE protocol from TVS Connect 8.13.2 (`PROTOCOL.md`)
- [x] Fake display (`tools/fake_cluster.py`) and two-adapter BLE test (`tools/central_test.py`, PASS)
- [x] Frame encoder for both display families with byte-exact unit tests
- [x] BLE link, foreground ride service, boot restart
- [x] Google Maps directions and incoming calls from notifications
- [x] Auto model detection (older Ntorq via 5A 6B / 5A 29 frames)
- [x] Ride dashboard (turn arrow, distance, ETA, left, speed), demo ride, console

## Needs the real scooter
- [ ] Pair the user's Ntorq XT (2026) and confirm the display accepts the 2025+ status frame
- [ ] Confirm turn arrows show the right pictogram for each maneuver
- [ ] Confirm caller frame clears correctly when a call ends (stock app relies on call state)
- [ ] Capture the display's incoming frames to refine Auto detection (U716 vs U577 base)
- [ ] Check whether the display needs Bluetooth bonding

## Later
- [ ] Map Google Maps non-English instructions (`MapsNotificationParser` is English-only)
- [ ] Temperature in the status frame (stock app uses a weather API)
- [ ] Missed-call count for the older Ntorq family (needs READ_CALL_LOG)
- [ ] Call accept/reject from the scooter buttons (display sends it in the 5A 10 frame)
- [ ] Legacy Ntorq SmartXonnect (VehicleCode "9") uses separate code, not analysed
