# PodFinder

A small, no-root Android app (Android 13+) for finding AirPods and other Apple Find My devices
that are **nearby**. It reads the Bluetooth signals those devices broadcast and turns them into a
"you're getting warmer" finder.

## What it does

- **Pick one device and track it.** Tap a device in the list to pin it and drop into a full-screen
  finder: a big radial meter that fills and turns from cold-blue to hot-red as you close in, a
  warmer/colder arrow, a rough distance, and **haptic pulses that quicken like a metal detector**
  the nearer you get. Walk around; follow the buzz.
- **Live status.** Battery, charging and in-ear for each AirPod, decoded from the broadcast.
- **Home-screen widget.** Shows your pinned device and its last-seen status; tap to jump straight
  into the finder. The widget doesn't scan in the background, so it's battery-cheap.
- **Play sound (limited).** For a Find My device that's broadcasting in "separated" mode (away from
  its owner's Apple device), PodFinder can trigger the anti-stalking sound over Bluetooth LE.

## What it deliberately does NOT do, and why

The loud Find My "Play Sound" beep (the one that carries across the house and works with the case
shut) **cannot be triggered from Android.** It's a Find My command signed with the owner's private
key: the AirPods issue a fresh nonce and verify an ECDSA signature before they'll sound. Those keys
live in the Apple/iCloud keychain and can't be extracted on current macOS. This was confirmed by
reverse-engineering `bluetoothd`/`searchpartyd` (`AirPodsRequestSignatureValidation`,
`verifyFindMyNetworkId`, `For S2 verification: nonce =`). The quiet "connected" chime is signed the
same way. The only owner-free sound Android can send is the separated-state anti-stalking beep above.

For the real loud beep and last-known location, sign in at **icloud.com/find** in a browser.

See `../RESEARCH.md` for the full write-up.

## Install

Download `app-release.apk` from the latest GitHub release and sideload it (allow installs from your
browser/files app). It's signed with a stable key, so updates install over the top. Minimum
Android 13.

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest assembleDebug          # debug build + unit tests
./gradlew assembleRelease                           # signed release APK (needs keystore.properties)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release signing reads `keystore.properties` at the repo root (gitignored). To build your own
release, create a keystore and a matching `keystore.properties`:

```
storeFile=podfinder-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Layout

- `core/` — pure Kotlin, unit-tested: Continuity/proximity/offline-finding decoders, the RSSI
  meter, pinning, haptic cadence, protocol selection, and the play-sound `SoundSession` state
  machine. 35 tests.
- `ble/` — Android glue: `BleScanner`, `GattSoundPlayer`, `Haptics`.
- `ui/SignalMeterView` — the dependency-free radial gauge.
- `MainActivity` — list + full-screen finder. `PodFinderWidget` — the home-screen widget.

## Credits

Protocol facts only (UUIDs, opcodes, byte layouts, advertisement captures), not code, from:
[seemoo-lab/AirGuard](https://github.com/seemoo-lab/AirGuard),
[d4rken-org/capod](https://github.com/d4rken-org/capod),
[adolfintel/OpenPods](https://github.com/adolfintel/OpenPods), and the IETF DULT draft.
