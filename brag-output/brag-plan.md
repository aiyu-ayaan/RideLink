# RideLink — brag plan

**Format:** vertical 9:16, 1440×2560 (2K), 30 fps, 64.8 s (extended cut on request: slower, settled pacing). **Tone:** cinematic dev-story, polished, dark.
**Angle:** "The official app is rated 2.7★. So I rebuilt it — no account, no cloud — straight to the scooter display."

## What it is
RideLink: a private, local-first Android app that talks to TVS SmartXonnect scooter displays over BLE.
Replaces TVS Connect for riding features: Google Maps turns on the display, caller ID + answer/reject from the
handlebar switch, music, weather, ride analytics, multi-scooter garage. Zero accounts, zero cloud, zero tracking.
Built by reverse-engineering the BLE protocol (PROTOCOL.md). Open source on GitHub, Android 8+.

## Research (public sources, used as on-screen problems)
- TVS Connect (com.tvsm.connect) Play Store average ≈ 2.7★ over ~110K ratings (AppGoblin snapshots).
- Riders report: "chassis number not found", "connects only one time after installing", connection
  "disconnected while riding", "'mac_id is not valid' & 'invalid speedometer'" (App Store reviews,
  TVS community forum). Unstar lists it as the most-complained auto app in India.
- Stock app requires an account + chassis registration before pairing.

## Visual identity
RideLink dark scheme from `ui/theme/Theme.kt` (seed #B3261E): surface #1A1110, primaryContainer #73342C,
tertiaryContainer #574419 / on #FCDFA6, secondaryContainer #5D3F3B. Brand: RideLink Red #B3261E, Spark Amber
#FFB82E, white ring. Roboto Flex + Material Symbols Rounded. Closed-loop mark with the real splash choreography
(`splash-preview.html`). Problem act is desaturated grey with error-red accents; the turn floods to brand red.
No TVS logos or assets (CLAUDE.md rule) — TVS is named in text only, with a not-affiliated line at the end.

## Storyboard (100 BPM, beat = 0.6 s, every cut on a beat)
| # | Time | Scene |
|---|---|---|
| 1 | 0.0–4.8 | Hook: rating counts 5.0 → 2.7★, stars drain, "on Google Play · ~110K ratings" |
| 2 | 4.8–12.0 | Pain: 4 rider complaints stack in, one per 1.2 s, then hold |
| 3 | 12.0–16.8 | Turn: brand red, "So I built my own.", real splash choreography, RideLink + tagline |
| 4 | 16.8–22.8 | Connect: phone in, Searching → Connected; NTORQ-style TFT cluster rises and boots (telltales, needle sweep), RIDELINK / CONNECTED |
| 5 | 22.8–30.0 | Navigation: dashboard card, 400 m → turn (right indicator blinks on the beat), then "Continue onto 100 Feet Rd"; cluster mirrors arrow, street, distance, speed |
| 6 | 30.0–36.0 | Calls: Mom rings, answered from the scooter switch at 32.4, call ends at 34.8 |
| 7 | 36.0–40.8 | Music + weather cards, page scrolls; cluster shows NIGHT RIDE / LO-FI BEATS and 27° |
| 8 | 40.8–46.8 | Cluster sinks, phone grows, pill switches to Analytics: week distance, bars, records |
| 9 | 46.8–52.8 | Ride history: list scrolls in, tap Sat 31 Oct ride → detail page (distance source, stats, Maps turn timeline, speed chart) |
| 10 | 52.8–57.6 | Proof: real nav-control frame, byte by byte; Zero accounts / cloud / tracking |
| 11 | 57.6–64.8 | Outro: icon, RideLink, feature chips, GitHub link, not-affiliated line; long hold, fade to black |

## Sound
100 BPM in A minor. Act 1: muted pad, ticking hats, detuned error blips on each card (in key, low in the mix).
Riser into 8.0, impact + brand chord on the logo. 10.0 onward: full groove (kick, clap, bass, plucked arp,
A-major lift); soft UI ticks on beat for connect / arrow / call / tab. Outro: final chord rings out.
