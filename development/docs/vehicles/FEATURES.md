# Feature matrix by family

Which rider-facing features exist on which cluster family in the stock app, and the capability flags RideLink needs
so each screen and frame appears only where the vehicle supports it. Labels: **Y** observed in the stock sender or
receiver for that family; **N** observed absent (the family's sender class has no method for it, checked in the
sender and its helper service, including the `IMusicDetailsCallBack` wiring, which only `N360HelperService` has);
**?** Unknown; **-** not applicable. Families:

- **SX**: Ntorq SmartXonnect LCD (9) and legacy Jupiter U408 / U532 (22). RideLink `SMARTXONNECT`.
- **NT**: older Ntorq U716 / U577 (34, 37, 38). RideLink `NTORQ`.
- **JU**: unified Jupiter family: 2025+ Ntorq, Jupiter U279 / U566 / U745, Zest U714. RideLink `JUPITER`.
- **RA**: Raider (29). **RO**: Ronin (25). **AP**: Apache (both stacks). **EV**: iQube groups. **XU**: U388 / TVS X
  (blocked, access control).

## Phone → cluster

| Feature | SX | NT | JU | RA | RO | AP | EV | Frame / source |
|---|---|---|---|---|---|---|---|---|
| Turn-by-turn | Y | Y | Y | Y | Y | Y | Y | `5A 4E` + `5B 4F`; layouts per family doc |
| Clock, battery, signal (status frame) | Y | Y | Y | Y | Y | Y | Y | `5B 4A`, layout per family |
| Overspeed limit in status | Y | Y | Y | Y | Y | Y | fixed 120 | status [3] |
| Caller name | Y | Y | Y | Y | Y | Y | Y | `5B 43` |
| Missed-call count | Y | Y | 0 in stock | Y | Y | Y | Y | status frame |
| SMS sender | Y | N | N | Y | Y | ? | ? | `5B 53` |
| Missed SMS count | N | N | N | N | Y | ? | N | Ronin status [15] |
| Chat counts (WA / FB / IG) | Y | Y | Y | Y | N | unified ?, legacy N | ? | `5B 9C` (unified `BleWriteSocialMediaNotificationDataSrcImpl` has no family check) |
| Rider name | Y | Y | Y | Y | Y | Y | ? | `5B 52` |
| Custom text lines / pop-ups | Y | Y | Y | Y (2 param bytes) | Y | Y (feedback id) | ? | `5B 4C`, `5B 63` |
| Music (song, artist, state) | N | Y | Y | Y (TFT; artist `65`, always XOR) | N | U400 / unified only | Y | `5B 62/63/65/60` |
| Weather + AQI screen | N | U577P | Y (TFT) | Y (TFT) | placeholder | ? | ? | `5B 5D` + `5B 4C 00 78` |
| Place name | Y | U577P | Y | Y | Y | Y (`sendLocationToCluster`) | ? | `5B 5C` |
| Backlight / ride mode / date (`5A F1`) | Y | Y | Y | Y | Y | Y | ? | vehicle control |
| Birthday / purchase greeting | Y | ? | ? | Y | ? | Y | ? | `5A 7E` / `5B 7E` |
| Follow-me headlamp, idle start-stop timers | N | Y | Y | ? | N | Y | ? | `5B 73` calibration, `5A F2` |
| Find my vehicle flag | N | N | U279 only | N | N | N | N | status flag |
| Crash alert flag | N | N | N | N | Y | U399 / U449 | N | status byte |
| Low-fuel prompt on display | Y | ? | ? | Y | Y | ? | - | `sendAutoNavigationCommand`, Ronin `5A 4E … 51 01` |
| Cricket / football / news | Y | ? | ? | Y | N | N | N | server feeds, out of scope |
| Caller photo, DigiDoc, OTA, mirroring | U445B | U577P | ? | TFT | N | U399 / U400 | Y | out of scope |
| Keyless lock / trunk | N | N | N | N | N | N | N | XU only, out of scope |

## Cluster → phone

| Data | SX | NT | JU | RA | RO | AP | EV | Frame |
|---|---|---|---|---|---|---|---|---|
| Speed, odometer | Y | Y | Y | Y | Y | Y | Y? | `5A 10` |
| Fuel level / bars | Y | Y | Y | Y | Y (%) | Y | - | `5A 10` [6] |
| Battery % | - | - | - | - | - | - | Y | `5A 13` / `5A 23` [2] |
| Range / distance to empty | N (Race XP) | Y | Y | Y | ? | Y (cruising range `5A 12`) | Y (`5A 13` [3..4]) | per family |
| Average / instant mileage | some themes | Y | Y | Y | Y | Y | - | `5A 19` / `5A 10` |
| Trip meter | Y | Y | Y | Y (A / B, DID ?) | ? | Y (`5A 12` trip) | Y (A / B on U546) | |
| RPM | Y | Y | Y | Y | ? | Y | - | `5A 10` |
| Gear position | N | N | N | Y (`5A 11` [5]) | Y | Y | - | |
| Ride mode | N | N | N | Y (`5A F1` echo) | Y | Y (`5A 11` [17]) | eco / power / sport | |
| Lean angle, torque, acceleration | N | N | N | N | Y | Y (`5A 12`) | N | |
| Lap timer, best lap, 0-60 | race stats `5A 16` | N | 0-60 [13] | 0-60 [12] | Y | Y (`5A 16`) | N | |
| Engine data (load, temps, voltage) | Y | Y | Y | Y | Y | Y | - | `5A 18` |
| Service reminder, MIL | Y | Y | Y | Y | Y | Y (+ ABS MIL) | ? | `5A 11` |
| Turn indicators | Y | Y | Y | Y | ? | Y | ? | `5A 11` [12] |
| Call accept / reject buttons | Y ([12]) | Y ([12]) | Y ([12]) | Y ([11], bit 7 flag) | ? | Y ([11]) | Y (U388 frame) | `5A 10` |
| Music buttons | N | `5A 6B` | ? | `5A 6B` | ? | ? | `5A 6B` / `5A 54` | |

## Capability flags for `ClusterModel`

RideLink today has `hasTft`, `hasLocation`, `hasMusic`. The matrix needs these per model (default from the family,
overridable per model):

| Flag | Gates |
|---|---|
| `hasMusic` | music frames, Music card |
| `hasWeather` (today `hasTft`) | weather / AQI frames, Weather card |
| `hasLocation` | place-name frame |
| `hasSocialCounts` | `5B 9C` |
| `hasSmsSender` | `5B 53`, SMS settings |
| `hasFuel` (false for EVs) | fuel tile, fuel log, refuel detection, low-fuel prompt |
| `hasBattery` (EV) | battery % and range tile, EV charge reminders |
| `hasGear` | gear tile, gear stats in analytics |
| `hasRideMode` | ride-mode tile and analytics split |
| `hasRange` | range tile, low-range alert |
| `hasLapTimer` | race stats card |
| `hasLeanAngle` | lean angle in ride detail |
| `hasFindMe` | find-my-vehicle (U279) |
| `hasHeadlampTimers` | follow-me headlamp / idle start-stop settings |
| `hasBirthday` | greeting settings |

Every new Settings toggle and Ride card checks its flag, so a rider never sees a feature their display cannot show
(project rule: every TFT feature has a Settings toggle).
