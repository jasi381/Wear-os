# Wear OS ↔ Phone Connectivity

A minimal reference project demonstrating the **Wearable Data Layer API** across
two parts:

- **Part 1** — bidirectional peer detection (branch `part1`)
- **Part 2** — real-time messaging and persistent data sync (this branch, `part2`)

Both sides answer independent questions in real time:

- Is the other **device** paired & reachable over BT/Wi-Fi? (`deviceConnected`)
- Is the other side's **app** installed and responding? (`appAlive`)
- What **messages** and **counter taps** has the peer sent?

---

## Part 1 — Peer Detection

### How detection works

Each manager exposes three `StateFlow`s:

```kotlin
val deviceConnected: StateFlow<Boolean?>  // null = checking, true = paired node reachable
val appAlive:        StateFlow<Boolean?>  // null = checking, true = peer app pong'd back
val <peer>NodeId:    StateFlow<String?>   // active node id, if any
```

On `start()` each manager:

1. Registers a `MessageClient.OnMessageReceivedListener` for `/jasmeet/*` paths.
2. Runs a 10 s heartbeat that calls `NodeClient.connectedNodes`:
    - empty list → `deviceConnected = false`, `appAlive = false`
    - non-empty → `deviceConnected = true`, then sends `PING` to the best node
      (prefers `isNearby`) and starts a 5 s pong timeout.
3. On `PONG_PATH` received → `appAlive = true`. On timeout → `appAlive = false`.

### Interpreting the two flags

| `deviceConnected` | `appAlive` | Meaning                                           |
|-------------------|------------|---------------------------------------------------|
| true              | true       | Peer reachable & app installed/responding         |
| true              | false      | Peer reachable, but peer app not installed/alive  |
| false             | false      | No paired node reachable (BT off / out of range)  |

---

## Part 2 — Sending Data

### Project layout

```
WearOs/
├── app/              # phone companion (applicationId: com.jasmeet.wearos)
│   └── .../connectivity/CompanionCapabilityManager.kt   ← Part 1
│   └── .../data/DataLayerManager.kt                     ← Part 2
└── wear/             # watch app      (applicationId: com.jasmeet.wearos)
    └── .../connectivity/WearCapabilityManager.kt        ← Part 1
    └── .../data/DataLayerManager.kt                     ← Part 2
```

### MessageClient — real-time text messages

`MessageClient.sendMessage(nodeId, path, bytes)` delivers a one-shot payload to a
specific node. It is fire-and-forget: if the peer is not reachable the message is
dropped (no buffering).

```
Phone types a message → sendMessage(watchNodeId, "/jasmeet/message", bytes)
Watch's OnMessageReceivedListener fires → _lastReceivedMessage.value = text
```

Both sides can send; the watch has a "Say Hi!" shortcut because on-screen keyboards
are awkward on Wear OS.

**Paths used:**

| Path                | Direction          | API           |
|---------------------|--------------------|---------------|
| `/jasmeet/ping`     | phone ↔ watch      | MessageClient |
| `/jasmeet/pong`     | phone ↔ watch      | MessageClient |
| `/jasmeet/message`  | phone ↔ watch      | MessageClient |

The existing `PingPongListenerService` (path prefix `/jasmeet`) receives messages in
the background on both sides. Pings are replied to automatically; user messages
(`/jasmeet/message`) are only displayed when the activity is in the foreground.

### DataClient — persistent synced state

`DataClient.putDataItem(PutDataRequest)` writes a `DataItem` (key-value map) that:
- persists on the source node
- syncs to all connected nodes automatically
- survives reconnects — the peer reads it fresh on `start()`

Counter paths are asymmetric so each side only reacts to the **peer's** write, not
its own echo:

```
phone writes  /jasmeet/counter/phone  → watch's OnDataChangedListener sees it
watch writes  /jasmeet/counter/wear   → phone's OnDataChangedListener sees it
```

A `ts` field is written alongside the value to force a change event even if the
counter value hasn't changed (DataClient deduplicates identical DataItems by content).

```kotlin
PutDataMapRequest.create(MY_COUNTER_PATH).apply {
    dataMap.putInt("value", next)
    dataMap.putLong("ts", System.currentTimeMillis())  // prevents dedup
}.asPutDataRequest().setUrgent()
```

`setUrgent()` bypasses the 30-minute background sync delay and delivers immediately.

### DataLayerManager API

```kotlin
// Both sides expose the same interface
val lastReceivedMessage: StateFlow<String?>  // last text message from peer
val myCounter:           StateFlow<Int>      // this side's tap count (local + written to DataClient)
val peerCounter:         StateFlow<Int?>     // peer's tap count (read from DataClient)

fun sendMessage(nodeId: String, text: String)  // MessageClient
fun incrementMyCounter()                        // DataClient putDataItem
fun start() / stop()                            // call from onStart / onStop
```

---

## Running

Two emulators required: a phone + a Wear OS watch, paired via
Android Studio **Device Manager → Pair Wearable**.

```bash
./gradlew :app:installDebug :wear:installDebug
```

### Testing scenarios

| Scenario                        | How to trigger                           | Expected result                         |
|---------------------------------|------------------------------------------|-----------------------------------------|
| Send message phone → watch      | Type in phone text field, tap Send       | Watch shows text under "Received"       |
| Send message watch → phone      | Tap "Say Hi!" on watch                   | Phone shows "Hi from Watch!"            |
| Increment counter on phone      | Tap "Tap +1" on phone                    | Watch's "Phone taps" updates            |
| Increment counter on watch      | Tap "Tap +1" on watch                    | Phone's "Watch taps" updates            |
| Counter survives background     | Increment, background app, reopen        | Count still shown (DataClient persists) |
| Message lost while backgrounded | Send while peer app is closed            | Message not shown (MessageClient drops) |

---

## Gotchas (read before you debug)

### 1. `applicationId` MUST be identical on both modules

```kotlin
// app/build.gradle.kts  and  wear/build.gradle.kts
defaultConfig { applicationId = "com.jasmeet.wearos" }   // SAME on both
```

### 2. `DataClient` deduplicates identical DataItems

If you write the same counter value twice (e.g. `putDataItem` with `value=3` twice),
the second write is silently dropped. Always include a `ts` timestamp field to force
the change event.

### 3. `setUrgent()` is required for interactive demos

Without it, DataClient batches writes for up to 30 minutes. Always call
`.setUrgent()` on the `PutDataRequest` when immediate delivery is expected.

### 4. `MessageClient` does NOT buffer messages

If the peer app is backgrounded and not registered as a listener, the message is
dropped. For reliable delivery, use `DataClient` instead. For notifications while
backgrounded, use `WearableListenerService` + a notification.

### 5. Don't skip the lifecycle hooks

`start()` and `stop()` on both managers must be called from `onStart`/`onStop`.
Forgetting leaks listeners and the heartbeat `Runnable` across config changes.

### 6. Counter paths are asymmetric on purpose

`/jasmeet/counter/phone` is written by the phone and read by the watch.
`/jasmeet/counter/wear` is written by the watch and read by the phone.
Using a single shared path would cause each side to react to its own write (echo).

## Logs

```bash
adb -s <phone-id> logcat -s CompanionCapabilityMgr,DataLayerMgr
adb -s <wear-id>  logcat -s WearCapabilityMgr,WearDataLayerMgr
```
