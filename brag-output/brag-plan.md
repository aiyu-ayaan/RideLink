# RideLink — brag plan

**Format:** vertical 9:16, 1440×2560 (2K), 30 fps, 25 s. **Tone:** cinematic dev-story, polished, dark.
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

## Storyboard (120 BPM, beat = 0.5 s, every cut on a beat)
| # | Time | Scene | On screen |
|---|---|---|---|
| 1 | 0.0–3.0 | Hook | Big rating counts 5.0 → 2.7★, stars drain. "The official TVS Connect app" / "2.7★ on Google Play" |
| 2 | 3.0–8.0 | Pain | "What riders keep hitting:" then 4 error cards stack in on beats: Chassis number not found · Connects once, then never again · Disconnects mid-ride · mac_id is not valid. Glitch on each. |
| 3 | 8.0–10.5 | Turn | Hard cut to red. "So I built my own." Logo draws with the real splash choreography, spark pops, ripple. "RideLink" + tagline |
| 4 | 10.5–20.0 | Product | Phone with rebuilt current Ride screen. 10.5 Searching (loading cookie) → 11.5 Connected. Caption "No account. It just connects." 13.0 nav dashboard springs in, distance counts 300 → 50 m, speed ticks; scooter display panel mirrors the arrow over a BLE pulse. Caption "Google Maps turns, on your display." 15.5 call card "Mom" + handlebar answer. Caption "Answer calls from the handlebar." 17.5 pill bar switches to Analytics, bars grow. Caption "Every ride. Only on your phone." |
| 5 | 20.0–22.5 | Proof | Real BLE frames stream (5B 4F …, 5A 4E …). "Rebuilt the BLE protocol from scratch." then "Zero accounts. Zero cloud. Zero tracking." |
| 6 | 22.5–25.0 | Outro | Mark + RideLink + "Free & open source · Android 8+" + github.com/aiyu-ayaan/RideLink; small "Independent project. Not affiliated with TVS Motor Company." |

UI text inside the phone uses demo values (the app's own Demo ride / sample rides): "TVSBT01", MG Road, Mom.

## Sound
120 BPM in A minor. Act 1: muted pad, ticking hats, detuned error blips on each card (in key, low in the mix).
Riser into 8.0, impact + brand chord on the logo. 10.0 onward: full groove (kick, clap, bass, plucked arp,
A-major lift); soft UI ticks on beat for connect / arrow / call / tab. Outro: final chord rings out.
