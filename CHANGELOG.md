# Changelog

## [0.2.1-alpha.12](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.11...v0.2.1-alpha.12) (2026-11-12)

### Other changes

* Fix(Analytics) : Period picker keeps labels on one line and scrolls at large font sizes; About shows the live GitHub profile
* Core(About) : Fetch the developer's GitHub profile (name, bio, avatar, counts)

## [0.2.1-alpha.11](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.10...v0.2.1-alpha.11) (2026-11-12)

### Other changes

* Cut next alpha build
* Docs(TODO) : Road-test list for auto-connect, call indicator and notification icons
* Core(Calls) : Keep the caller on the display for the whole call, phone and app calls, as stock does
* Ui(Analytics) : Full-width odometer tile on ride detail, hide empty charts on a day with no rides
* Ui(Analytics) : Today view with live ride card, km by hour, today's rides and yesterday comparison
* Core(Analytics) : Add Today period, km by hour of day and the live ride summary
* Fix(BLE) : Retry link setup with a watchdog, dial the scooter on its first advert, reconnect from the open app

## [0.2.1-alpha.10](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.9...v0.2.1-alpha.10) (2026-11-11)

### Other changes

* Fix(Nav) : SmartXonnect skips identical turn repeats, resends every 2.2 s, and shows chevrons only at the junction (#5)
* Cut next alpha build
* App(Service) : End ride session after 60 s without the scooter; nearby scan restarts it
* App(Service) : Wake ride session when the paired scooter advertises nearby

## [0.2.1-alpha.9](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.8...v0.2.1-alpha.9) (2026-11-10)

### Other changes

* Cut next alpha build
* Test(Rides) : Extra ride filter tests
* Docs(Rides) : Ride history and display hold notes
* Ui(Rides) : Ride history page with search, filter chips, filter sheet and paging; rename and star rides; recent 5 on Analytics
* Core(Rides) : Ride names, stars, start and end places, and a filtered paged history query
* Fix(Ride) : Hide tiles for values the display never sends instead of showing rows of dashes
* Fix(Display) : Hold turn frames while a message pop-up shows or a call rings, so both stay visible during a route

## [0.2.1-alpha.8](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.7...v0.2.1-alpha.8) (2026-11-08)

### Other changes

* Cut next alpha build
* Fix(Startup) : Start the controller's background jobs only after all its fields exist, so a fresh process no longer crashes and the app opens after process death
* Feat(Messages) : Queue pop-ups, add missed-call and delivery pop-ups, per-app and text filters, and a short sender flash during navigation
* Fix(Signal) : Ask the modem for fresh signal bars every 10 s so the display updates while the phone is locked
* Feat(Messages) : Show the app, sender and text of every new chat or SMS on the display for a few seconds
* Fix(Chats) : Count each new WhatsApp, Facebook and Instagram message like the TVS app so the display badge pops every time

## [0.2.1-alpha.7](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.6...v0.2.1-alpha.7) (2026-11-06)

### Other changes

* Fix(Theme) : Text on tinted cards uses the card's own on-colour so it stays readable in dark and dynamic themes

## [0.2.1-alpha.6](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.5...v0.2.1-alpha.6) (2026-11-06)

### Other changes

* Fix(Nav) : Back on Analytics or Settings returns to the Ride tab before leaving the app
* Fix(Theme) : Reapply status bar icon colour on resume and focus so the system cannot leave white icons on a light bar
* Ui(Care) : Fuel and service page, messages settings, Console feature tests
* App(Care) : Care notifications, WhatsApp auto-reply, location foreground service
* Core(Care) : Fuel log, refuel and low-fuel detection, service reminders, check-in SMS
* Ui(Theme) : Top bars stay on surface colour when scrolled
* Fix(Theme) : Dark window background stops white flash on predictive back
* Fix(Nav) : Keep scroll state of tabs and pages across navigation
* Docs(Readme) : Ntorq compatibility matrix and new SmartXonnect features

## [0.2.1-alpha.5](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.4...v0.2.1-alpha.5) (2026-11-05)

### Other changes

* Feat(SmartXonnect) : Experimental notification light clear, food delivery alerts and last-ride CO2
* Feat(SmartXonnect) : Race stats, last parked spot, display brightness, SMS sender, birthday greeting, missed-call auto-reply
* Core(Protocol) : SmartXonnect chat counts without checksum, caller cleared at once, arrow test walks every direction
* Core(Nav) : Race XP arrows use the 2025+ pictogram ids, roundabout exits get their own arrow
* Core(Protocol) : Auto-detect the SmartXonnect LCD (Race XP) and send its own nav and status frames
* Core(Nav) : Demo navigation runs even with Forward navigation off and ends with the stop frame
* Core(Nav) : Send the turn as words like the stock Mappls short text, not the Maps street name

## [0.2.1-alpha.4](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.3...v0.2.1-alpha.4) (2026-11-05)

### Other changes

* Docs(Arch) : Document Maps icon turns, nav repeat, wake lock and per-scooter rides
* Ui(Analytics) : Show rides per scooter with a scooter picker
* App(Service) : Hold a wake lock while connected and ask to skip battery optimisation
* Core(Nav) : Read the turn arrow from the Maps notification icon, repeat the turn every 2 s like stock, demo navigation

## [0.2.1-alpha.3](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.2...v0.2.1-alpha.3) (2026-11-05)

### Other changes

* Core(Nav) : Always send one text line in the nav control frame so the TFT shows the turn arrow
* Docs(Readme) : Add demo ride data & analytics screenshots to gallery
* Docs(Readme) : Switch screenshots path to docs/screenshots to bust Camo cache
* Docs(Screenshots) : Fix corrupted PNG headers from multi-display screencap
* Docs(Screenshots) : Refresh screenshots from running app build
* Docs(Screenshots) : Capture current AVD ride screen
* Docs(Readme) : Add project README with architecture, features and setup
* Ui(Update) : Keep the space before the download size

## [0.2.1-alpha.2](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.1-alpha.1...v0.2.1-alpha.2) (2026-11-04)

### Other changes

* Docs(Arch) : Document app shell, updater, developer console, ride distance sources and release flow
* App(Icon) : Tonal launcher icon matching the app theme, DEV tag and name on debug builds, single activity instance
* Ui(Analytics) : Ride detail page with distance source, all stats, charts, speed bands and Maps turn timeline; scooter chips on Ride
* Core(Analytics) : Ride distance from Maps route progress, odometer or speed per interval, record route and turns
* Core(Calls) : Strip bidi marks dialers wrap around caller numbers
* Core(Calls) : Send dialer-formatted caller numbers as digits only
* Core(Protocol) : Checksum social count and music frames like the stock app, phone-state backup for incoming calls

## [0.2.1-alpha.1](https://github.com/aiyu-ayaan/RideLink/compare/v0.2.0...v0.2.1-alpha.1) (2026-11-04)

### Other changes

* Ui(Nav) : Floating pill bar, Settings sections, predictive back, Console as developer option with saved logs, About, Updates
* Core(Update) : Self-update from GitHub releases with channels, checksum and system installer
* Ci(Release) : Replace debug APK workflow with signed release flow from Rewire, version from VERSION file
* App(Splash) : Match splash background and mark to the app theme, light, dark and dynamic
* Docs(Garage) : Document multi-scooter storage and switching
* Ui(Settings) : List saved scooters, switch between them, add and remove
* Core(Garage) : Store every paired scooter with its own model and reset display state on bike switch
* App(Notify) : Detect ringing calls on more dialers, rebind the listener, note unparsed Maps and call events in Console
* Core(Nav) : Match Maps distances written with no-break spaces
* App(Music) : Fall back to another active player when a session ends
* Core(Nav) : Send one turn at a time, drop stale steps, resend after reconnect
* Core(Nav) : Find turn distance anywhere in Maps text and strip separators
* Ci(Build) : Add debug APK workflow
* Core(Nav) : Parse Maps direction from any field so dashboard arrow shows
* Ui(Analytics) : Fix odometer smart cast across modules
* App(Splash) : Add Closed loop launcher icon and animated splash screen with safe exit
* Docs(Brand) : Add Closed loop logo masters, brand guide and splash preview
* Docs(Brand) : Rethink ring and spark without the vehicle, round 4
* Docs(Brand) : Rethink concept B with the scooter, round 3
* Docs(Brand) : Add logo concepts round 2
* Ui(Analytics) : Add Analytics tab with charts, records, ride history and CSV export
* Core(Analytics) : Record rides automatically and compute period stats
* Ui(Console) : Decode every frame and add Copy log for scooter captures
* Core(Display) : Fix odometer scale, send real signal and missed calls, add live display text templates
* Core(Weather) : Fix getCurrentLocation overload ambiguity
* Core(Calls) : Text the caller on reject-with-SMS and open the assistant from the voice button
* Ui(Settings) : Add custom display text lines with send-now
* Ui(Console) : Add per-frame test chips and return to the default screen before navigation
* Ui(Vehicle) : Show engine, battery and service data and the display's model code
* Core(Status) : Add overspeed alert limit and missed-call badge
* Docs(Handoff) : Record TFT feature port, version 0.2.0 and what still needs a build
* App(Social) : Fix group-summary flag precedence in unread count
* Ui(Ride) : Add ride data card, Settings toggle and demo data for every TFT feature
* Core(Calls) : Accept and reject calls from the scooter switch, parse display ride data
* Core(Music) : Send song, artist and play state from the active media session
* Core(Weather) : Send weather, AQI, temperature and place name to TFT displays
* Core(Social) : Send WhatsApp, Facebook and Instagram unread counts as 5B 9C
* App(Permissions) : Ask for phone state and coarse location so signal and network show
* App(Connect) : Auto-connect when a scooter is paired, ride button becomes Disconnect
* Core(Protocol) : Read speed from frame[2], ParsingMeta is 1-based
* Docs(Handoff) : Record TFT feature map, testing method and next steps
* Docs(Project) : Add CLAUDE.md, architecture, design system, TODO and run.sh
* Ui(Ride) : Add turn-arrow dashboard, speed readout and demo ride
* Tools(Emulation) : Add BlueZ central test for the fake display
* Core(Model) : Add Auto model detection from display frames
* App(RideLink) : Add BLE link, ride service and M3 Expressive UI
* Core(Protocol) : Add cluster frame encoder, Maps nav parser and Gradle setup
* Add recovered BLE protocol notes and fake cluster tool

