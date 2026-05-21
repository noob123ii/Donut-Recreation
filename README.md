# Donut Recreation

A PaperMC plugin that takes a serious swing at recreating the Donut SMP toolkit on your own
server: an anti-freecam / anti-xray base hider, a `/sus` review GUI, a configurable `/punish`
system with alt-evasion detection, and `/spawn` decoys (fake stashes, fake spawners, fake
players, fake bedrock spawners). All client-side trickery is done with PacketEvents — the
real world on disk is never touched.

> **Tested on:** Paper 1.21.4. Other 1.21.x builds will likely work but aren't guaranteed.

---

## What it actually does

### `/sus` — review queue
- Opens a chest GUI listing every player who has been flagged (manually via `/sus <name>`,
  or automatically by the behaviour tracker)
- Click a head → you're moved into spectator mode following that player, and their flag
  gets cleared from the queue
- Refresh / paginate buttons live on the bottom row
- The behaviour tracker watches for sustained elytra flights, mining bursts (possible
  base-finding), and macro-like repetition and pushes flags in the background

### `/offand <player> <reason>` (alias `/punish`)
- Bans + optionally wipes the target's data
- Reasons live in `config.yml` under `punishments:` — each one configures its own ban
  duration and whether to reset the player's inventory / enderchest / xp / stats
- Stores the ban in `playerdata.db` so the alt-detection layer can use it
- OP-only

### `/spawn <decoy>` — base honeypots
- `fakestash` — 6×6×4 hollow stone room with chests, only visible to you, reverts in 5 min
- `fakespawner` — single ghost spawner where you're looking
- `fakeplayer` — 10-second NPC standing in front of you (PacketEvents)
- `fakebedrockspawner` — finds the nearest standable 1×2 slot at y=63 and sets up a
  spawner in front, an amethyst bud behind, and a fake player standing between them
- All blocks are **ghost blocks** — clients see them, the server doesn't

### Anti-freecam / hider
- Below a configurable Y, the world is rewritten on the wire as a procedural noise pattern
  of deepslate / tuff / amethyst so freecam clients see junk instead of the real ores
- Geodes, light data and block-entity NBT are also scrubbed so clients can't sniff them
  through debug tooling
- Players physically near their own base are revealed via flood-fill so the floor opens up
  organically as they walk; staff with the bypass perm always see the truth

### Alt-evasion (`AltBanListener`)
On every join, if the joining player shares an IP with somebody who's currently banned,
one of three policies fires:
- **`strict`** — auto-ban the new account for 1 month
- **`new-accounts-only`** — auto-ban only if the account is younger than
  `alt-ban-account-min-age-hours` or has never joined from that IP before; otherwise
  just sus-flag and notify staff (default)
- **`flag-only`** — never auto-ban, only flag

---

## Installing

1. Drop `DonutRecreation.jar` from `build/libs/` into your server's `plugins/` folder
2. Start the server once so `plugins/DonutRecreation/config.yml` is generated
3. Stop, edit the config (see below), start again
4. Make sure the players who should review reports are OP — the commands all check for OP

The plugin shadows PaperLib and PacketEvents into its own jar, so you don't need to install
PacketEvents separately.

## Building from source

```sh
./gradlew build
```

Final jar lands in `build/libs/DonutRecreation.jar`. Java 21 toolchain is required (the
build is pinned to `JavaVersion.VERSION_21`).

---

## Configuration

Everything lives in `plugins/DonutRecreation/config.yml`. The defaults work, but here are
the knobs you'll actually want to touch.

### `hider:` — anti-freecam
```yaml
hider:
  enabled: true
  hide-below-y: 0          # everything below this Y gets rewritten on the wire
  world-min-y: -64         # bottom of the world

  # How aggressively the floor opens up around revealed players
  reveal-initial-radius: 3
  reveal-movement-radius: 2
  flood-fill-budget: 80000

  geode-hide-enabled: true   # hide geode shapes too (anti-amethyst-locator)
  decoy-amethyst-enabled: true   # sprinkle fake amethyst into the noise

  bypass-permission: "donutrecreation.hider.bypass"
  verbose-logging: false
```

If your players are reporting lag spikes, lower `flood-fill-budget` and
`max-revealed-chunks-per-player`. If freecam users are still seeing real ores, raise
`hide-below-y`.

### `punishments:` — `/offand` reasons
```yaml
punishments:
  Krypton:
    BanTime: lifetime
    ResetData: true
  "ESP / Bariton":
    BanTime: 1mo
    ResetData: true
  Macroing:
    BanTime: 7d
    ResetData: false
```

`BanTime` accepts: `lifetime` / `permanent` / `forever`, or duration strings like
`30m`, `2h`, `7d`, `2w`, `1mo`, `1y`. `ResetData: true` wipes inventory, enderchest,
XP, hunger, potion effects and untyped statistics on kick.

### Alt-evasion
```yaml
alt-ban-policy: new-accounts-only      # strict | new-accounts-only | flag-only
alt-ban-account-min-age-hours: 168     # 7 days
```

### `sus:` — review GUI
```yaml
sus:
  alert-sound: "ENTITY_EXPERIENCE_ORB_PICKUP"
  cooldown-ms: 3000        # rate-limit /sus to once per 3s per reporter
```

### `messages:`
All staff-facing strings are in here, including the `&8>` chat prefix. Standard `&`-style
colour codes work, plus hex `&x&R&R&G&G&B&B`.

---

## Permissions

| Node                              | Default | What it does                                            |
|-----------------------------------|---------|---------------------------------------------------------|
| `donutrecreation.alerts`          | op      | Receives `/sus`, alt-evasion and freecam staff alerts   |
| `donutrecreation.bypass`          | op      | Bypasses anti-freecam checks                            |
| `donutrecreation.hider.bypass`    | op      | Sees the real world below the hider's `hide-below-y`    |
| `donutrecreation.*`               | false   | Grants every node above                                 |

The commands themselves (`/sus`, `/offand`, `/spawn`) gate on `sender.isOp()`, not on a
permission node — set OP or use a permissions plugin that grants OP to a group.

---

## Data files

The plugin writes a single YAML database at `plugins/DonutRecreation/playerdata.db`. It
contains:
- **`bans:`** — every ban issued through `/offand` plus alt-evasion auto-bans, with
  reason, expiry, captured IP and an `evader` flag
- **`profiles:`** — per-UUID first-seen timestamp, last-seen timestamp, join count,
  client brand fingerprint, and the most recent IPs they joined from
- **`ips:`** — reverse index from IP → list of UUIDs that have joined from it (capped)

It's flushed asynchronously every ~10 seconds and on plugin disable. Safe to back up
while the server is running. Safe to delete if you want a clean slate (you'll lose ban
history).

---

## Bugs / questions / "why is this happening"

Ping **!Lucy (`playfab.dll`)** on Discord. Open a GitHub issue if you can produce a clean
repro.

When reporting:
- Paper version (`/version Paper`)
- The relevant chunk of `config.yml`
- Server log around the event (especially any stacktrace involving `com.notlucy.*` or
  `com.github.retrooper.packetevents.*`)
- For false-positive `/sus` flags or wrong-feeling alt bans, include the player's UUID
  and what was in their `playerdata.db` profile

---

## License

See `LICENSE`. This is a recreation/learning project — the original Donut SMP team owns
their own implementation; nothing here is copied from their codebase.
