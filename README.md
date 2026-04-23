# Wear OS ↔ Phone Connectivity

A minimal reference project showing **bidirectional peer detection** between a
Wear OS watch and its paired Android phone using the Wearable Data Layer
`NodeClient` + `MessageClient` ping/pong heartbeat (pattern ported from
`kc-android`).

Both sides answer two independent questions in real time:

- Is the other **device** paired & reachable over BT/Wi-Fi? (`deviceConnected`)
- Is the other side's **app** installed and responding? (`appAlive`)

Separating these matters: a watch can be connected (official Wear OS companion
shows "connected") while our wear APK isn't installed. A single
`CapabilityClient` signal collapses both into "not connected" and hides the
distinction.

## Project layout

```
WearOs/
├── app/              # phone companion (applicationId: com.jasmeet.wearos)
│   └── .../connectivity/CompanionCapabilityManager.kt
└── wear/             # watch app      (applicationId: com.jasmeet.wearos)
    └── .../connectivity/WearCapabilityManager.kt
```

## How detection works

Each manager exposes three `StateFlow`s:

```kotlin
val deviceConnected: StateFlow<Boolean?>  // null = checking, true = paired node reachable
val appAlive:        StateFlow<Boolean?>  // null = checking, true = peer app pong'd back
val <peer>NodeId:    StateFlow<String?>   // active node id, if any
```

On `start()` each manager:

1. Registers a `MessageClient.OnMessageReceivedListener` for the `/jasmeet/*` paths.
2. Runs a 10 s heartbeat that calls `NodeClient.connectedNodes`:
    - empty list → `deviceConnected = false`, `appAlive = false`
    - non-empty → `deviceConnected = true`, then sends `PING` to the best node
      (prefers `isNearby`) and starts a 5 s pong timeout.
3. On `PONG_PATH` received → `appAlive = true`. On timeout → `appAlive = false`.

Each side also registers a `WearableListenerService`
(`PingPongListenerService`) with an intent-filter for
`com.google.android.gms.wearable.MESSAGE_RECEIVED` on `/jasmeet/*` so it
replies to pings even when the foreground activity is gone.

### Interpreting the two flags

| `deviceConnected` | `appAlive` | Meaning                                           |
|-------------------|------------|---------------------------------------------------|
| true              | true       | Peer reachable & app installed/responding         |
| true              | false      | Peer reachable, but peer app not installed/alive  |
| false             | false      | No paired node reachable (BT off / out of range)  |

The previous `jasmeet_wearos_{mobile,wear}` capability entries in `wear.xml`
are no longer load-bearing for detection — they can stay (harmless) or be
removed without affecting behaviour.

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

### 2. `PingPongListenerService` must be registered in the manifest

If you forget the `<service>` entry or the `MESSAGE_RECEIVED` intent-filter,
the peer can still *ping* you but you'll never reply — so `appAlive` on the
other side stays stuck at `false` even though your app is installed. Path
prefix in the filter (`/jasmeet`) must match `PING_PATH` / `PONG_PATH`.

### 3. `NodeClient` alone doesn't prove the peer app is installed

`connectedNodes` returns *any* paired Wear OS node, regardless of what apps
are on it. That's the whole point: `deviceConnected` is the
app-independent signal. The pong response is what confirms the peer APK is
present and running.

### 4. Emulator BT bridge is flaky

On fresh emulator pairs, the first heartbeat can take up to ~15 s to flip
`deviceConnected = true`. The UI has a **Refresh** button that calls
`manager.refresh()` to trigger an immediate `NodeClient.connectedNodes` +
`PING` roundtrip.

### 5. Don't skip the lifecycle hooks

`start()` and `stop()` are called from `onStart` / `onStop`. Forgetting to
call `stop()` leaks the message listener and the heartbeat `Runnable` across
config changes.

### 6. `WearableListenerService` runs in its own process

`PingPongListenerService` fires even when the activity is gone — that's the
feature. But it means you can't share in-memory state between the manager and
the service; keep the reply logic self-contained (as it is here).

## Testing the scenarios

| Scenario                    | How to trigger                                      | Phone UI shows          | Watch UI shows          |
|-----------------------------|-----------------------------------------------------|-------------------------|-------------------------|
| Happy path                  | Both APKs installed, both devices on                | connected ✓  app ✓      | connected ✓  app ✓      |
| Wear APK not installed      | phone+watch paired, only phone APK installed        | connected ✓  app ✗      | (n/a — gone)            |
| Phone APK not installed     | phone+watch paired, only wear APK installed         | (n/a — gone)            | connected ✓  app ✗      |
| Watch powered off / BT off  | `adb -s <wear> emu kill`                            | connected ✗  app ✗      | (n/a — off)             |

Watch logs on either side:

```bash
adb -s <phone-id> logcat -s CompanionCapabilityMgr
adb -s <wear-id>  logcat -s WearCapabilityMgr
```

