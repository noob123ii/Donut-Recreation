# Donut Recreation

PaperMC plugin that recreates Donut SMP's anti-cheat toolkit on your own server.

Anti-freecam / anti-xray base hider with two-tier masking, geode hiding, a `/acsus` review GUI,
punishment system with alt-evasion detection, and `/spawnfake` decoys. Client-side trickery done
via [PacketEvents](https://github.com/retrooper/packetevents) — the real world on disk is
never touched.

Tested on **Paper 1.21.4**. Requires **Java 21**.

---

## Commands

### `/acsus [player] [reason]`
Chest GUI listing flagged players. Click a head to spectate that player. The behaviour
tracker flags sustained elytra flights, mining bursts, and macro-like repetition in the
background.

### `/offend <player> <reason>` (alias `/punish`)
Bans and optionally wipes player data. Reasons defined in `config.yml` under `punishments:`.
Bans stored in `playerdata.db` for alt-detection. OP-only.

### `/unban <player>`
Unbans a player and clears their evader flag. OP-only.

### `/spawnfake <stash|spawner|player|bedrockspawner>`
Spawns ephemeral ghost-blocks and NPCs. Requires staffmode.
- `stash` — 6x6x4 hollow stone room with chests, visible only to you, reverts in 5 min
- `spawner` — ghost spawner where you're looking
- `player` — 10-second NPC standing in front of you
- `bedrockspawner` — spawner setup at y=63 with a fake player

### `/staffmode`
Toggles staff mode. Blocks non-whitelisted commands while active. Required for `/spawnfake`.

### `/donut reload`
Reloads config without restart. OP-only.

### `/donut chunk generate <border>`
Generates a chunk border. OP-only.

---

## Base Hider

Rewrites chunk data on the wire so freecam/x-ray clients see noise instead of your base.
Not a traditional ore-obfuscator — masks everything below a configurable Y-level.

**Two-tier barrier:**
- Lower (`hide-below-y`): everything below masked as procedural deepslate/tuff/stone noise.
  Revealed only when a player is physically in that chunk with line-of-sight (cave flood-fill).
- Upper (`barrier-upper-y`): the band between lower and upper is masked while the player
  is above the upper threshold. Set equal to hide-below-y to disable.

**Geode hiding:** Real amethyst clusters, budding amethyst, calcite and smooth basalt are
masked when geode chunks aren't revealed. Prevents cheat clients from triangulating bases
via geode shapes.

**Tile-entity masking:** Chests, shulkers, hoppers, spawners below the barrier sent as
AIR until the viewer is within render distance.

**Sound & particle dampers:** Certain sounds/particles that leak hidden chunk data are
suppressed or re-spatialised.

**Salt rotation:** Noise pattern is salted per-player and rotates periodically (default
12,000 ticks). Prevents clients from building a static map of the fake floor.

**Bypass:** Staff with `donutrecreation.hider.bypass` see the real world.

---

## Alt-Evasion

On every join, if the player shares an IP with someone currently banned:

- `strict` — auto-ban the new account for 1 month
- `new-accounts-only` — auto-ban only if the account is younger than `alt-ban-account-min-age-hours` or has never joined from that IP before (default)
- `flag-only` — never auto-ban, only flag

---

## Installation

1. Drop `DonutRecreation.jar` from `build/libs/` into `plugins/`
2. Start the server once to generate `config.yml`
3. Stop, edit the config, start again
4. Make sure staff players are OP

The plugin shadows PaperLib and PacketEvents into its own jar.

## Building

```
./gradlew shadowJar
```

Output: `build/libs/DonutRecreation.jar`

---

## Configuration

`plugins/DonutRecreation/config.yml` — see the file for all options. Key ones:

- `hider.hide-below-y` / `hider.barrier-upper-y` — barrier thresholds
- `hider.flood-fill-budget` — lower for less CPU, raise for better cave reveal
- `hider.max-revealed-chunks-per-player` — memory vs coverage tradeoff
- `punishments:` — reason/duration/data-wipe per offence type
- `alt-ban-policy` — strict / new-accounts-only / flag-only

---

## Data Files

Single YAML database at `plugins/DonutRecreation/playerdata.db`:
- `bans:` — all bans with reason, expiry, IP, evader flag
- `profiles:` — per-UUID join history, client brand, IPs
- `ips:` — reverse index from IP to UUIDs

Flushed async every ~10 seconds and on disable. Safe to delete for a clean slate.

---

## Troubleshooting

**Chunks below floor don't load until relog** — raise `max-reveal-hide-per-recompute`.

**Geodes look partially covered** — check that `hide-below-y` is set correctly. Geode blocks
below it mask as deepslate, above as stone.

**Lag spikes in caves** — lower `flood-fill-budget` and `max-revealed-chunks-per-player`.

---

## Bugs

Ping **!Lucy** on Discord. Open a GitHub issue with:
- Paper version (`/version Paper`)
- Relevant `config.yml` section
- Server log around the event

---

## License

See `LICENSE`. Recreation/learning project — not copied from the original Donut SMP codebase.
