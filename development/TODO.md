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
- [ ] Cricket / football / news, vehicle settings, image transfer, 5A 6B music controls (need the decompile shared) (#67)
- [ ] Build the APK, run Demo ride on the emulator and refresh screenshots (blocked in the cloud session: dl.google.com not reachable) (#69)
- [ ] Re-run fake_cluster.py with a real phone to check every new frame decodes (#68)

- [x] Analytics tab: automatic ride recording, period stats, charts, records, ride history, CSV export, sample rides

- [x] Logo ("Closed loop"), launcher + themed + notification icons, animated splash screen

- [x] Fuel log with full-to-full mileage, refuel detection, low-fuel prompt, service items + papers, display flash
- [x] Parked spot only when stopped, precise location; WhatsApp auto-reply while riding; ride check-in SMS
- [x] Console -> Feature tests (refuel, low fuel, service due, park here, check-in SMS, pretend riding)

- [x] Ride history page: names, stars, From A to B titles, filters, pagination
- [x] Turn frames held while a pop-up or call shows; tiles hide values the display never sends
- [x] Analytics Today view (live ride, km by hour, today's rides); full-width odometer tile
- [x] All 22 Scheduled Indian Languages + English with in-app language picker and RTL support
- [x] Dialled calls stay off the display; only a call that rang sends the caller frame
- [x] Fuel saved as gauge bars, ride fuel drop from settled levels, trip tile from trip steps or the odometer
- [x] BLE retry backoff, stale GATT refresh, Bluetooth off/on resume, session keeps waiting while battery optimised
- [x] Quick settings tile with floating connection panel and live scooter stats
- [x] Unread chat badges start from the notification shade on every connect
- [x] Charts: press and drag to read, shared crosshair on ride detail, tap through to rides, six new graphs

## Needs the real scooter
- [ ] Auto-connect (2026-11-13 rework): ignition on with the app open, closed, and after 60 s idle; Console log `ClusterLink` lines (#27)
- [x] Answered call: caller frames stop and the route comes back, as stock does for our models (issue #16).
- [ ] Notification icons on the Race XP: rider reports them "not working properly"; get a Console "Copy log" with STATUS and 5B 9C lines (#29)
- [ ] During a Maps route: caller name and message pop-ups stay on the display (turn frames held) (#30)
- [ ] Refuel detection: check the bar jump at a real pump (ignition off and on) and that a slosh does not log a stop (#31)
- [ ] Service flash on custom line 1: does the Race XP / XT show it on connect (#31)
- [ ] WhatsApp auto-reply with a real chat while riding (or Pretend riding) (#28)
- [ ] Location while screen off: parked spot accuracy after a ride (FGS location type) (#31)
- [ ] Analytics: check recorded distance against the scooter's trip meter on a real ride (#31)
- [ ] Trip meter: 5A 10 [14..16] reads empty on the rider's XT; get a Console "Copy log" with RX frames (#31)
- [ ] Auto-connect (2026-11-18 rework): ignition off for 2+ minutes, then on, with battery optimisation on and off; Console `Link:` lines (#27)
- [ ] Outgoing phone and WhatsApp call on the scooter: the display must show no caller (#28)
- [ ] Fuel drop on a real ride: bars in the ride detail match the gauge (#31)
- [ ] Unread badge after ignition on with chats already waiting in the shade (#29)
- [ ] Panel live stats while connected (#31)
- [ ] Trip A / B: Console "Copy log" (RX `5a 10` lines) while switching trips on the display (#31)
- [ ] Signal bars, call icon, notification badge: still missing? compare decoded STATUS lines, try model U577 premium (#29)
- [ ] Pair the user's Ntorq 125 Race XP (2026, U458) and confirm the display accepts the 2025+ status frame (#32)
- [ ] Race XP (SmartXonnect LCD): left family arrows confirmed (ids 0/1/2); check right 10, sharp right 11, slight right 12, keep left 7 / right 17, U-turn 19, destination 22, straight 57, roundabout 48 / exits 28 / 32 with Demo navigation (Console -> Arrow codes 0 to 80 to see any other id) (#30)
- [ ] SmartXonnect U445B / U566 themes: alert block, code remap and 5A 49 control 2 not implemented (theme id comes from the TVS account) (#24)
- [ ] Real Maps route on the scooter: Console should show `Maps: <street> = <TURN> (icon ... 1.00)`; check the meter stays in nav mode between Maps updates (2 s repeat) (#30)
- [ ] Calls / WhatsApp on the Race XP: retest with battery optimisation off + wake lock; Console notes show what was detected (#28)
- [ ] Confirm caller frame clears correctly when a call ends (stock app relies on call state) (#28)
- [ ] Capture the display's incoming frames to refine Auto detection (U716 vs U577 base) (#32)
- [ ] Check whether the display needs Bluetooth bonding (#27)

## All TVS vehicles (issue #20; research in `development/docs/vehicles/`, plan in `IMPLEMENTATION_PLAN.md`)
- [x] Research TVS Connect 8.13.2 for every vehicle family: ids, stacks, encryption, frames, feature matrix (2026-11-29)
- [x] P0 Phase 0: XOR frames for U716 (TX 0xEB) and U577 premium (TX/RX 0xEA) (`FrameCrypto`, `tools/emulate_test.py`) (2026-11-29)
- [ ] Phase 0 follow-up: Auto mode never XORs (detect() cannot tell U716 / U577 base / premium apart); tell U716 and U577 premium riders to pick their model (Settings hint + `model_auto_using`, 23 locales), or detect premium from its 0xEA RX payload once a capture confirms it (#22)
- [ ] Confirm on a real U716 / U577 premium (Console log with RX lines via the vehicle issue form) (#23)
- [ ] P0 Vehicle 8 `N251Spp` and non-TI vehicle 9 clusters use classic Bluetooth SPP for data (found by BLE scan, then RFCOMM); possible cause of issue #15 "detected but keeps searching" (research `SppSendDataToCluster` first) (#34)
- [ ] Phase 1: multi-family foundation: new `ClusterFamily` values, capability flags, detect() limited to Ntorq, service found by `5352`/`5354` chars, per-model MTU / write type (#36)
- [ ] Phase 2: Raider (issue #19): firmware-date XOR 0xED, status frame, Raider pictogram table, TFT/LCD from `5A 11` [9] (#19)
- [ ] Phase 3: Apache (RR310, RTR 310, U449/U469, U796/U797, N597) and Ronin: XOR 0xEA/0xEB, checksum nav, gear / mode / range / laps (#47, #52)
- [ ] Phase 4: Jupiter U279/U566/U745, Zest U714, legacy Jupiter U408/U532 (first record their pictogram tables) (#55)
- [ ] Phase 5: iQube family: battery %, range, trip A/B; hide fuel features for EVs (#59)
- [ ] Research gaps: Raider trip A/B DID, Raider call-status byte values, legacy RR310 / RTR 310 status bytes 15/16/18, Ronin RX DID mapping (`U368ClusterDataManager`), Jupiter / EV pictogram tables (#45, #49, #54, #56, #60)
- [ ] Collect `5A 11` [9] model codes from rider logs (only 0xE3 = Raider LCD is known) (#38)
- [ ] NTORQ 150: not in 8.13.2; needs a newer TVS Connect APK (#25)
- [ ] U388 and TVS X: blocked by the access-control challenge; say so in the README (no plan) (#59)

## Later
- [ ] Turn arrow fallback if a Maps update renames its `maneuver_*` drawables (Console note then says `0 Maps icons`) (#66)
- [ ] Legacy Ntorq SmartXonnect (VehicleCode "9") uses separate code, not analysed (#34)
- [x] Optional Google sign-in (Firebase Auth) with skippable welcome screen
- [x] Firestore cloud backup: WorkManager schedule (manual/daily/weekly/monthly, Wi-Fi only), Back up now,
      incremental by hash, conflict choice (merge / use cloud / use phone), restore, delete; Firebase Analytics on
- [x] Backup tested on a phone (seeded data): first backup, no-op, incremental, changed-elsewhere conflict + merge,
      fresh-install restore, use phone, use cloud, delete + marker, sign-in check respects marker, release build
- [x] Re-pair a saved scooter (history follows the new address), Play Store style account dialog, usage statistics switch
- [ ] Backup: test on two real phones (conflict, merge, restore on a fresh install) against the live rules (#64)
- [ ] Backup: notify when a scheduled backup stops on a conflict (today it only shows in Settings -> Backup) (#65)
