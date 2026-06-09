# Donut Recreation

A PaperMC plugin that recreates the Donut SMP anti-cheat toolkit on your own server:
an **anti-freecam / anti-xray base hider** with two-tier masking, **geode hiding**, a
`/sus` review GUI, a configurable `/offend` punishment system with alt-evasion
detection, and `/spawn` decoys (fake stashes, fake spawners, fake players, fake bedrock
spawners). All client-side trickery is done via [PacketEvents](https://github.com/retrooper/packetevents)
— the real world on disk is never touched.

> **Tested on:** Paper 1.21.4. Other 1.21.x builds will likely work but aren't guaranteed.
> Requires **Java 21**.

---

## What it actually does

### `/sus [player] [reason]` — review queue
- Opens a chest GUI listing every player who has been flagged (manually via `/sus <name>`,
  or automatically by the behaviour tracker)
- Click a head → you're moved into spectator mode following that player, and their flag
  gets cleared from the queue
- Refresh / paginate buttons live on the bottom row
- The behaviour tracker watches for sustained elytra flights, mining bursts (possible
  base-finding), and macro-like repetition and pushes flags in the background

### `/offend <player> <reason>` (alias `/punish`)
- Bans + optionally wipes the target's data
- Reasons live in `config.yml` under `punishments:` — each one configures its own ban
  duration and whether to reset the player's inventory / enderchest / xp / stats
- Stores the ban in `playerdata.db` so the alt-detection layer can use it
- OP-only

### `/unoffend <player>` — unban
- Removes an active ban from a player and clears their `evader` flag
- OP-only

### `/spawn <decoy>` — base honeypots
- `fakestash` — 6×6×4 hollow stone room with chests, only visible to you, reverts in 5 min
- `fakespawner` — single ghost spawner where you're looking
- `fakeplayer` — 10-second NPC standing in front of you (PacketEvents)
- `fakebedrockspawner` — finds the nearest standable 1×2 slot at y=63 and sets up a
  spawner in front, an amethyst bud behind, and a fake player standing between them
- All blocks are **ghost blocks** — clients see them, the server doesn't

### `/settings` — per-player settings GUI
- Opens a settings GUI for the player ( toggles, preferences, etc.)

### `/donut reload` — admin utility
- Reloads the plugin configuration without a restart

### Base Protection / Hider
The hider rewrites chunk data on the wire in real-time so freecam / x-ray clients see
noise instead of your actual base. It is **not** a traditional ore-obfuscator — it masks
**everything** below a configurable Y-level.

**Two-tier barrier system**
- **Lower barrier** (`hide-below-y`): everything below this Y is masked as procedural
  deepslate / tuff / stone noise. It is only revealed when a player is physically inside
  that chunk and has line-of-sight (air-connected cave flood-fill).
- **Upper barrier** (`barrier-upper-y`): the band `[hide-below-y, barrier-upper-y)` is
  masked while the player is at or above `barrier-upper-y`. Once the player drops below
  it, a radius around them is revealed. Set equal to `hide-below-y` to disable the upper
  band entirely.

**Geode hiding**
- Real amethyst clusters, budding amethyst, calcite and smooth basalt are scanned per chunk
- When a geode chunk is not revealed to the player, all amethyst-family blocks are replaced
  with the same mask material used by the lower/upper barrier (deepslate below `hide-below-y`,
  stone above). Fake decoy amethyst clusters are still sprinkled into the noise.
- This prevents cheat clients from using geode shapes to triangulate bases.

**Tile-entity masking**
- Chests, shulkers, hoppers, spawners and other containers below the barrier are sent as
  AIR until the viewer is within `tile-entity-render-distance` blocks.
- While below the lower barrier, tile entities up to `tile-entity-mask-above-range` blocks
  above `hide-below-y` are also masked.

**Light data scrubbing**
- Block light and sky light arrays for masked sections are zeroed, and the light masks are
  cleared + set to empty. This prevents cheat clients from detecting hidden chunks via
  light-level anomalies.

**Sound & particle dampers**
- Certain sounds and particles that can leak hidden chunk data (elytra, world events) are
  suppressed or re-spatialed so they don't expose what's below the floor.

**Salt rotation**
- The noise pattern is salted per-player and rotates periodically (default every 12,000
  ticks). This prevents clients from building a static map of the fake floor.

**Bypass**
- Staff with `donutrecreation.hider.bypass` always see the real world.
- Creative / Spectator players can opt into a solid-radius reveal so chunks load while
  noclipping through solid rock.

### Alt-evasion (`AltBanListener`)
On every join, if the joining player shares an IP with somebody who's currently banned,
one of three policies fires:
- **`strict`** — auto-ban the new account for 1 month
- **`new-accounts-only`** — auto-ban only if the account is younger than
  `alt-ban-account-min-age-hours` or has never joined from that IP before; otherwise
  just sus-flag and notify staff (**default**)
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
./gradlew shadowJar
```

Final fat jar (with PacketEvents and PaperLib shaded) lands in
`build/libs/DonutRecreation.jar`. Java 21 toolchain is required (the build is pinned to
`JavaVersion.VERSION_21`).

---

## Configuration

Everything lives in `plugins/DonutRecreation/config.yml`. The defaults work, but here are
the knobs you'll actually want to touch.

### `logging:`
```yaml
logging:
  enabled: true
  fine: false          # set to true for very verbose hider / packet debug logs
```

### `hider:` — anti-freecam / base hider
```yaml
hider:
  enabled: true

  # Two-tier barrier
  hide-below-y: 0          # lower barrier: blocks below this Y are masked
  barrier-upper-y: 10      # upper barrier: band [hide-below-y, barrier-upper-y) is masked
                            # set equal to hide-below-y to disable the upper band
  upper-reveal-radius: 4
  world-min-y: -64

  # Creative / Spectator players reveal a solid radius instead of cave flood-fill
  creative-spectator-radius-reveal: true

  # Reveal radius around the player
  reveal-initial-radius: 3
  reveal-movement-radius: 2
  reveal-edge-extra-radius: 1
  sticky-radius: 10          # revealed chunks "stick" within this radius
  max-revealed-chunks-per-player: 4096

  # Tile-entity (chest/shulker/hopper/spawner/...) masking
  tile-entity-mask-enabled: true
  tile-entity-render-distance: 10
  tile-entity-mask-above-range: 100

  # Recompute cadence
  recompute-period-ticks: 10
  recompute-min-ticks: 2

  # Cave flood-fill (air-connected expansion)
  flood-fill-budget: 80000
  flood-fill-block-radius: 96
  flood-fill-throttle-ticks: 10

  # Entity visibility scanning
  entity-scan-chunk-radius: 8

  # Geode hiding
  geode-hide-enabled: true
  geode-reveal-radius: 8
  max-geode-chunks: 16384

  # Spawn blocking below the barrier
  block-natural-spawns-below-hide: true
  block-all-spawns-below-hide: true

  # Anti-xray decoy amethyst clusters sprinkled into the noise
  decoy-amethyst-enabled: true
  decoy-amethyst-rate-bits: 11

  # Salt rotation (shuffles the noise pattern periodically)
  salt-rotate-period-ticks: 12000
  salt-rotate-stagger-ticks: 5

  # Void fall protection (Y <= -65)
  void-redirect-server: ""       # BungeeCord server to redirect to; empty = teleport
  void-spawn-world: "world"
  void-spawn-x: 0
  void-spawn-y: 100
  void-spawn-z: 0
  void-spawn-yaw: 0
  void-spawn-pitch: 0

  # Misc
  verbose-logging: false
  bypass-permission: "donutrecreation.hider.bypass"
```

**Performance tuning**
- If players report lag spikes while flying underground, lower `flood-fill-budget` and
  `max-revealed-chunks-per-player`. You can also lower `recompute-period-ticks`.
- If freecam users are still seeing real ores, raise `hide-below-y`.
- If chunks below the floor don't load until you relog, check that `max-reveal-hide-per-recompute`
  isn't throttling too aggressively.

### `punishments:` — `/offend` reasons
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
  gui-title: "&8Sus ??"
  alert-sound: "ENTITY_EXPERIENCE_ORB_PICKUP"
  cooldown-ms: 3000        # rate-limit /sus to once per 3s per reporter
```

### `messages:`
All staff-facing strings are in here, including the `&8>` chat prefix. Standard `&`-style
colour codes work, plus hex `&x&R&R&G&G&B&B`.

---

## Commands

| Command | Args | Description | Op required |
|---------|------|-------------|-------------|
| `/sus` | `[player] [reason]` | Open review GUI / flag a player | yes |
| `/offend` | `<player> <reason>` | Punish a player (`/punish` alias) | yes |
| `/unoffend` | `<player>` | Unban a player | yes |
| `/donut` | `reload` | Reload plugin config | yes |
| `/spawn` | `<fakestash\|fakespawner\|fakeplayer\|fakebedrockspawner>` | Spawn a decoy | yes |
| `/settings` | — | Open per-player settings GUI | no |

---

## Permissions

| Node | Default | What it does |
|------|---------|--------------|
| `donutrecreation.alerts` | op | Receives `/sus`, alt-evasion and freecam staff alerts |
| `donutrecreation.bypass` | op | Bypasses anti-freecam checks |
| `donutrecreation.hider.bypass` | op | Sees the real world below the hider's `hide-below-y` |
| `donutrecreation.*` | false | Grants every node above |

The commands themselves (`/sus`, `/offend`, `/spawn`) gate on `sender.isOp()`, not on a
permission node — set OP or use a permissions plugin that grants OP to a group.

---

## Data files

The plugin writes a single YAML database at `plugins/DonutRecreation/playerdata.db`. It
contains:
- **`bans:`** — every ban issued through `/offend` plus alt-evasion auto-bans, with
  reason, expiry, captured IP and an `evader` flag
- **`profiles:`** — per-UUID first-seen timestamp, last-seen timestamp, join count,
  client brand fingerprint, and the most recent IPs they joined from
- **`ips:`** — reverse index from IP → list of UUIDs that have joined from it (capped)

It's flushed asynchronously every ~10 seconds and on plugin disable. Safe to back up
while the server is running. Safe to delete if you want a clean slate (you'll lose ban
history).

---

## Troubleshooting

### "Chunks below the floor don't load until I relog"
This was a bug where throttled chunk updates (`max-reveal-hide-per-recompute`) could
mark a chunk as "revealed" in state before the multi-block-change packet was actually
sent. The fix ensures state only updates after the packet is dispatched. If you still see
this, raise `max-reveal-hide-per-recompute` in your config.

### "Geodes look partially covered / ghost blocks inside geodes"
This happens when the fake amethyst mask (sent when hiding a geode) didn't match the
chunk-level mask material. The fix ensures geode blocks below `hide-below-y` are masked
as deepslate (matching the lower barrier), and blocks above are masked as stone.

### "Fine logging is enabled but I don't see debug output"
The Java logger was filtering out `FINE` level messages. The fix explicitly sets the
logger level to `FINE` when `logging.fine: true` is set in config.

### "Elytra sounds don't play underground"
The sound damper was using scaled coordinates. The fix uses actual world coordinates for
radius checks so self-sounds (within 8 blocks) are allowed through.

### Lag spikes when flying through caves
Lower `flood-fill-budget` and `max-revealed-chunks-per-player`. The flood-fill is the most
expensive part of the hider — it expands through air blocks to find connected caves.

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
