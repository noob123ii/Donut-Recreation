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
Chest GUI listing all flagged players. Click a head to spectate that player. No category
layer — goes straight to the player list. Behaviour tracker flags sustained elytra flights,
mining bursts, macro-like repetition, and anti-cheat plugin flags in the background.

Anti-cheat detection notifications are broadcast to staff in the format:
`[Matrix] PlayerName: hack reason (N flags)`

### `/offend <player> <reason>` (alias `/punish`)
Bans and optionally wipes player data. Reasons defined in `config.yml` under `punishments:`.
Each ban gets a unique **Ban ID** (e.g. `BAN-1234`) that never repeats. Kick messages show
time remaining instead of an expiry date. Bans stored in `playerdata.db` for alt-detection.
OP-only.

### `/unban <player>`
Unbans a player and clears their evader flag. OP-only.

### `/unwipe <player>`
Unwipes a player — removes their ban and resets their data. OP-only.

### `/spawnfake <stash|spawner|player|bedrockspawner>`
Spawns ephemeral ghost-blocks and NPCs. Requires staffmode.
- `stash` — 6x6x4 hollow stone room with chests, visible only to you, reverts in 5 min
- `spawner` — ghost spawner where you're looking
- `player` — 10-second NPC standing in front of you
- `bedrockspawner` — spawner setup at y=63 with a fake player

### `/staffmode`
Toggles staff mode. Blocks non-whitelisted commands while active. Required for `/spawnfake`.
Action bar shows green "Staffmode: Enabled" or red "Staffmode: Disabled" on toggle.

### `/staffmode showtps`
Toggles a live TPS/ping display in the action bar for staff.

### `/donut reload`
Reloads config without restart. Works from both console and in-game. OP-only.

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

**Geode hiding:** Fake amethyst geodes generated underground with realistic Minecraft layers:
smooth basalt shell, calcite ring, amethyst block/budding amethyst layer (8% budding chance),
and a deepslate core with amethyst clusters and buds. Generated in ~8% of chunks between
Y=-40 and Y=10. Geode data persists across restarts.

**Spawner masking:** Spawners above the floor are replaced with deepslate and cannot be broken.

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

- `strict` — auto-ban the new account for 1 month (requires 5+ banned alts on same IP)
- `new-accounts-only` — auto-ban only if the account is younger than `alt-ban-account-min-age-hours` or has never joined from that IP before (default)
- `flag-only` — never auto-ban, only flag

Below the threshold, alts are flagged and staff are notified.

---

## Punishment System

Punishments are configured in `config.yml` under `punishments:`. Each reason supports:
- `BanTime` — ban duration (e.g. `1d`, `1mo`, `lifetime`)
- `MuteTime` — mute duration via LuckPerms (e.g. `10m`, `1h`)
- `ResetData` — wipe inventory, ender chest, and stats on punish

Ban records are stored with a unique Ban ID that can be used to link accounts.
Time is shown as remaining duration (e.g. "2d 5h 30m") rather than an expiry date.

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
- `alt-ban-threshold` — minimum banned alts on same IP before auto-ban (default 5)

---

## Data Files

Single YAML database at `plugins/DonutRecreation/playerdata.db`:
- `bans:` — all bans with Ban ID, reason, time remaining, IP, evader flag
- `profiles:` — per-UUID join history, client brand, IPs
- `ips:` — reverse index from IP to UUIDs

Geode data cached in `plugins/DonutRecreation/geode-cache/`:
- `scanned.dat` — which chunks have been scanned
- `geodes.dat` — stored geode positions

Staff mode state persisted in `plugins/DonutRecreation/staffdata.yml`.

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
