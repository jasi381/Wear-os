# Wear OS ↔ Phone Connectivity

A minimal reference project showing **bidirectional peer detection** between a
Wear OS watch and its paired Android phone using the Wearable Data Layer
`CapabilityClient`.

Both sides answer three questions in real time:

- Is the other device **connected**?
- Is the companion app **installed** on it?
- Was the companion app just **uninstalled** (vs. merely disconnected)?

## Project layout

```
WearOs/
├── app/              # phone companion (applicationId: com.jasmeet.wearos)
│   └── .../connectivity/CompanionCapabilityManager.kt
└── wear/             # watch app      (applicationId: com.jasmeet.wearos)
    └── .../connectivity/WearCapabilityManager.kt
```

Each module advertises one capability and listens for the other's:

| Module | Advertises                | Listens for                |
|--------|---------------------------|----------------------------|
| `app`  | `jasmeet_wearos_mobile`   | `jasmeet_wearos_wear`      |
| `wear` | `jasmeet_wearos_wear`     | `jasmeet_wearos_mobile`    |

Declared in `src/main/res/values/wear.xml` on each side:

```xml
<resources>
    <string-array name="android_wear_capabilities">
        <item>jasmeet_wearos_mobile</item>   <!-- or ..._wear on watch side -->
    </string-array>
</resources>
```

## How detection works

Each `CapabilityManager` exposes three `StateFlow`s:

```kotlin
val peerReachable: StateFlow<Boolean?>   // null = checking, true = online & reachable
val peerInstalled: StateFlow<Boolean?>   // null = checking, true = installed somewhere
val <peer>NodeId:  StateFlow<String?>    // active reachable node id, if any
```

On start the manager:

1. Registers a `CapabilityClient.OnCapabilityChangedListener` with
   `FILTER_REACHABLE`.
2. Calls `getCapability(..., FILTER_ALL)` for the first-load installed check.
3. Calls `getCapability(..., FILTER_REACHABLE)` for the first-load online check.

When the callback fires with `reachable == false`, the manager re-queries
`FILTER_ALL` to disambiguate:

| `reachable` | `installed` | Meaning                         |
|-------------|-------------|---------------------------------|
| true        | true        | Peer is online & app is running |
| false       | true        | Peer is offline / BT off        |
| false       | false       | **Peer app was uninstalled**    |

## Running

Two emulators required: a phone + a Wear OS watch, paired via
Android Studio **Device Manager → Pair Wearable**.

```bash
./gradlew :app:installDebug :wear:installDebug
```

Gradle auto-routes each APK to the matching device.

## Gotchas (read before you debug)

### 1. `applicationId` MUST be identical on both modules

The single biggest source of "why does it show disconnected on both sides?".

```kotlin
// app/build.gradle.kts
defaultConfig { applicationId = "com.jasmeet.wearos" }

// wear/build.gradle.kts
defaultConfig { applicationId = "com.jasmeet.wearos" }   // <-- SAME
```

The `CapabilityClient` resolves capabilities **per applicationId**. Different
`applicationId` → the two installs look like totally unrelated apps and neither
side ever reports the other as installed or reachable.

The `namespace` can (and often does) differ — that only affects generated
`R` / `BuildConfig` packages.

If you ever change `applicationId`, **uninstall the old app manually** before
reinstalling, otherwise both copies sit on the device and confuse the data
layer:

```bash
adb -s <wear-id> uninstall com.jasmeet.wear     # old id
./gradlew :wear:installDebug                     # installs com.jasmeet.wearos
```

### 2. Each side listens for the OTHER side's capability

It's easy to copy-paste and end up with both managers listening for the same
string. The pattern is:

- Phone advertises `..._mobile`, listens for `..._wear`
- Watch advertises `..._wear`, listens for `..._mobile`

Advertising your own capability is what makes the peer see you; listening for
the peer's capability is what tells you about them.

### 3. `FILTER_REACHABLE` alone can't tell you "uninstalled"

`FILTER_REACHABLE` only reports nodes that are online *and* running a
capability. A watch going out of Bluetooth range looks identical to the app
being uninstalled — both give you `nodes = []`. Always re-check with
`FILTER_ALL` before concluding "uninstalled".

### 4. Register for `FILTER_REACHABLE`, not `FILTER_ALL`

`addListener(..., FILTER_ALL)` does not reliably fire when the peer goes
offline. Use `FILTER_REACHABLE` for the live listener; use `FILTER_ALL` only
for the on-demand installed check.

### 5. Emulator BT bridge is flaky

On fresh emulator pairs, the first capability event sometimes takes
30–60 seconds after install. The UI has a **Refresh** button that calls
`manager.refresh()` to force a `getCapability` roundtrip if you don't want to
wait.

### 6. Watch app must be launched at least once

Wear OS lazy-initialises capability advertisement. If the watch app has never
been opened, the phone won't see the `..._wear` capability even though the
APK is installed. Tap the watch app icon once after install.

### 7. Capability XML file name does not matter — the resource name does

The file can be called `wear.xml`, `capabilities.xml`, anything — what matters
is the `string-array name="android_wear_capabilities"`. Multiple XML files
contributing to the same array get merged.

### 8. Don't skip the lifecycle hooks

`start()` and `stop()` are called from `onStart` / `onStop`. Forgetting to
call `stop()` leaks the listener across config changes. Forgetting to call
`start()` (e.g. calling it only in `onCreate` but losing the manager across a
process restart) gives stale state.

## Testing the 4 scenarios

| Scenario                       | How to trigger                                      | Phone UI shows                | Watch UI shows                |
|--------------------------------|-----------------------------------------------------|-------------------------------|-------------------------------|
| Happy path                     | Both running, watch app opened once                 | connected ✓  installed ✓      | connected ✓  installed ✓      |
| Watch disconnect               | `adb -s <wear> emu kill`                            | connected ✗  installed ✓      | (n/a — off)                   |
| Watch app uninstalled          | `adb -s <wear> uninstall com.jasmeet.wearos`        | connected ✗  installed ✗      | (n/a — gone)                  |
| Phone app uninstalled          | `adb -s <phone> uninstall com.jasmeet.wearos`       | (n/a — gone)                  | connected ✗  installed ✗      |

Watch logs on either side:

```bash
adb -s <phone-id> logcat -s CompanionCapabilityMgr
adb -s <wear-id>  logcat -s WearCapabilityMgr
```

## Part 1 scope

This commit covers connectivity detection only. Message passing, DataClient
sync, authentication handoff, and wear-specific features will come in later
parts.
