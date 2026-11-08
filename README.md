<p align="center">
  <h1 align="center">Donut Recreation</h1>
  <p align="center">A Paper plugin that brings Donut SMP-style anti-cheat tools, base hiding, and staff utilities to your own server.</p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper-1.20.4%2B-2ea44f?style=flat-square" alt="Paper 1.20.4+">
  <img src="https://img.shields.io/badge/Java-21%2B-ed8b00?style=flat-square" alt="Java 21+">
  <img src="https://img.shields.io/badge/PacketEvents-2.14.0-5b6ee1?style=flat-square" alt="PacketEvents 2.14.0">
</p>

---

Donut Recreation is a set of server-side tools built around packet-level hiding and moderation. The main idea is simple: stop freecam/x-ray style base finding without messing with the real world on disk. Chunk and block data is rewritten on the wire (via [PacketEvents](https://github.com/retrooper/packetevents)), so players see a masked view while the actual world stays untouched.

It also includes a suspicion review GUI (`/acsus`) that can pull in flags from common anti-cheats, plus punishments, alt-evasion checks, staff mode, and `/spawnfake` decoys.

> Tested on Paper 1.21.11 and 26.3. Targets `api-version` 1.20.4, so it should run on Paper 1.20.4+ with Java 21+.

<details>
<summary><b>What’s in the box</b></summary>

- **Base hider** — masks blocks below the configured floor and scrambles the surface while players are underground, so bases are hard to spot from below or with freecam.
- **Ore/geode hiding** — ore visibility is handled through packet checks, and amethyst/geode materials are masked until they should be visible.
- **Packet filtering** — rewrites chunk data, block changes, block entities, light/sign/debug-related packets, and filters a bunch of entity/world packets that can leak info.
- **`/acsus` review** — GUI for flagged players with spectate-on-click. Hooks into behavior tracking and known anti-cheat plugins (and can discover custom flag events).
- **Punishments** — `/offend`, `/unban`, `/unwipe`, configurable ban screen, Ban IDs, optional data wipes, and LuckPerms mute support.
- **Alt checks** — flags or auto-bans accounts that share an IP with banned players, depending on your policy.
- **Staff tools** — `/staffmode` (command whitelist + a few view helpers) and `/spawnfake` for ghost stashes/spawners/NPC decoys.

</details>

<details open>
<summary><b>Getting started</b></summary>

### Install

1. Build the plugin (see below) or grab `DonutRecreation.jar` from `build/libs/`.
2. Drop the jar into your server’s `plugins/` folder.
3. Start the server once to generate `config.yml`.
4. Stop the server, tweak the config, then start it again.
5. Make sure staff accounts have the permissions/OP access you expect.

The jar shades its PaperLib/PacketEvents dependencies, so you don’t need to install PacketEvents separately.

### Build

```bash
./gradlew shadowJar
```

On Windows:

```bat
gradlew.bat shadowJar
```

Output: `build/libs/DonutRecreation.jar`

### Local test server (this repo)

This checkout includes scripts for a local Paper test server in `server/` (gitignored):

```bat
start-server.bat
```

```powershell
.\start-server.ps1
```

More notes are in [`SERVER.md`](SERVER.md).

</details>

<details>
<summary><b>Commands</b></summary>

| Command | What it does | Access |
|---|---|---|
| `/acsus [player] [reason]` | Opens the suspicion GUI (or marks a player). Click a head to spectate. | Staff/alerts |
| `/offend <player> <reason>` | Punishes a player using a configured reason. Alias: `/punish`. | OP |
| `/unban <player>` | Removes a ban and clears the evader flag. | OP |
| `/unwipe <player>` | Unwipes/removes ban data for a player. | OP |
| `/spawnfake <stash\|spawner\|player\|bedrockspawner\|clear>` | Spawns/despawns ghost decoys and fake NPCs. Requires staff mode. | Staff mode |
| `/staffmode [hidename\|hideskin\|showtps]` | Toggles staff mode and related view options. | Staff |
| `/donut reload` | Reloads config. | OP |
| `/donut xray [rescan]` | Shows hider status and can re-apply the surface scramble across loaded chunks. | OP |
| `/donut chunk generate <border>` | Generates a chunk border. | OP |
| `/donut packets [clear]` | Shows/resets packet audit counters (if enabled). | OP |

Notes:
- `/acsus` is backed by behavior tracking and anti-cheat hooks (Vulcan, Matrix, GrimAC, NCP, AAC, Spartan, Verus, Intave, Polar, Karhu, Kauri, Reflex, Horizon, Themis, Medusa, Artemis, Dusk, Echo, Errata, plus jar-scanned/custom hooks).
- While spectating, the plugin keeps you close to the target so they can’t just run you out of range.
- `/spawnfake stash` loads templates from `plugins/DonutRecreation/stashes/` (`.yml`, and it can auto-convert `.litematic`/`.mcfunction` on load).

</details>

<details>
<summary><b>How the base hider works</b></summary>

The base hider rewrites outgoing packets — it doesn’t edit the world. The goal is to make underground/surface base finding with freecam or x-ray clients unreliable.

### Masking layers

- **Below `hider.hide-below-y`** — masked with procedural noise (deepslate/tuff/stone-style cover). Areas are revealed based on where the player actually is (cave/visibility logic), not just chunk ownership.
- **Barrier band (`hide-below-y` → `barrier-upper-y`)** — adds an upper masked band. Set both to the same value to disable the band.
- **Surface scramble (while underground)** — when a player drops below `hider.scramble-below-y` and stays in that locked state (until above `hider.scramble-until-above-y`), the plugin masks the surface band from `barrier-upper-y` up to `hider.surface-scramble-max-y`.

### Current surface scramble behavior

When the surface scramble is active, the plugin masks the surface band per column using the chunk’s heightmap:

- blocks **above the heightmap** (highest point in that column) become **air** if they aren’t already
- the **heightmap/top terrain block** is **replaced with stone** (hides the real surface/build top)
- blocks **below the heightmap** in that band are left as-is, so the area stays usable and less laggy

That hides surface builds and flattens the visible top layer without hollowing out the whole band. Normal mining/visibility rules still handle what players should see underground.

### Other hiding behavior

- **Geodes/amethyst** — amethyst-family blocks (buds, clusters, calcite, smooth basalt, etc.) are masked/handled so geodes don’t give away hidden areas. Fake geode decoys are off by default (`hider.fake-geode-amethyst-per-chunk`).
- **Spawners** — spawners can be masked above the configured floor and handled carefully around reveal distance.
- **Tile entities** — containers/spawners/etc. below protected areas can be sent as air until they’re legitimately visible.
- **Cave reveal** — nearby chunks/cave volume can be revealed progressively so normal mining still works, while distant/occluded areas stay masked.
- **Salt rotation** — per-player noise patterns rotate over time so clients can’t build a stable map of the fake terrain.
- **Re-masking** — chunks outside the active reveal area get masked again as players move away.

### Packet coverage (high level)

The plugin filters more than just `CHUNK_DATA`: light updates, block changes, multi-block changes, block entity data, block actions/break animations, sign updates, debug packets, and a range of entity/world packets that can leak positions (spawns, movement/sync, metadata, equipment, passengers, effects, damage/camera/look/bed-related packets, etc.). Sounds/particles/explosions can also be dampened or adjusted for hidden regions.

There’s also an optional packet audit (`packet-audit.*`) to log/classify outgoing packet types and optionally block risky unhandled ones. `/donut packets` shows the report.

</details>

<details>
<summary><b>Punishments & alt handling</b></summary>

### Punishments

Punishment reasons live under `punishments:` in `config.yml`. Each reason can define things like:

- `BanTime` (e.g. `1d`, `1mo`, `lifetime`)
- `MuteTime` (LuckPerms)
- `ResetData` (wipe inventory/ender chest/stats; can also reset supported economy balances)

Bans get a unique **Ban ID** (like `BAN-1234`) and are stored in `playerdata.db`. The ban screen is configurable with `ban-message` and supports placeholders like `%reason%`, `%duration%`, `%time_remaining%`, and `%ban_id%`.

### Alt-evasion

On join, the plugin checks whether the player shares an IP with banned accounts. Behavior is controlled by `alt-ban-policy`:

- `strict` — auto-ban new accounts after the threshold is met
- `new-accounts-only` — auto-ban only for young/new-to-that-IP accounts (default)
- `flag-only` — never auto-ban, just alert/flag

Tune with `alt-ban-threshold` and `alt-ban-account-min-age-hours`.

</details>

<details>
<summary><b>Configuration</b></summary>

Most behavior is configured in `plugins/DonutRecreation/config.yml`. A few keys you’ll probably touch first:

- `hider.hide-below-y` / `hider.barrier-upper-y` — main masking thresholds
- `hider.surface-scramble-enabled`, `hider.surface-scramble-max-y`, `hider.surface-scramble-radius` — surface hiding while underground
- `hider.scramble-below-y` / `hider.scramble-until-above-y` — lock/unlock hysteresis
- `hider.flood-fill-budget`, `hider.max-revealed-chunks-per-player` — cave reveal vs CPU/memory tradeoff
- `hider.max-reveal-per-recompute`, `hider.max-hide-per-recompute` — packet work limits per update
- `punishments.*` and `ban-message` — punishment reasons and ban screen
- `alt-ban-policy`, `alt-ban-threshold`, `alt-ban-account-min-age-hours` — alt handling
- `acsus.custom-anticheats` — custom anti-cheat event hooks
- `packet-audit.*` — outgoing packet auditing/blocking options

If you change chunk/hider behavior, restart the server and reconnect clients (don’t rely on `/reload` for chunk packet changes). `/donut xray rescan` can help re-apply the surface scramble to loaded chunks.

</details>

<details>
<summary><b>Data files</b></summary>

The plugin stores runtime data under `plugins/DonutRecreation/`, including:

- `config.yml` — main config
- `playerdata.db` — bans, player profiles, and IP history/index
- `geode-cache/` — geode scan/position cache
- `skins.yml` — captured skins for staff/fake player features
- `stashes/` — stash templates for `/spawnfake stash`
- `staffdata.yml` — staff mode state

Most data is flushed periodically and on shutdown. Deleting the data files gives you a clean slate (but you’ll lose bans/history/cache).

</details>

<details>
<summary><b>Troubleshooting</b></summary>

- **Chunks below the floor don’t show up until relog** — try raising `max-reveal-hide-per-recompute` (and check related reveal/hide budgets).
- **Geodes look partly visible/masked weirdly** — double-check `hide-below-y` and geode cache behavior; old cache data may need a clean rescan depending on changes.
- **Lag spikes in caves** — lower `flood-fill-budget` and/or `max-revealed-chunks-per-player`.
- **Surface hiding looks stale after config changes** — restart the server, reconnect, and use `/donut xray rescan` if needed.

</details>

---

## Bugs / feedback

If something breaks, open an issue and include:

- your Paper version (`/version Paper`)
- the relevant part of `config.yml`
- server logs around the issue

You can also ping **!Lucy** on Discord if that’s the support route you’re using.

## License

Copyright (C) 2026 Notlucy and Donut Recreation contributors.

This project is licensed under the **GNU General Public License v3.0** (or later). See [`LICENSE`](LICENSE) for the full text.

This is a recreation/learning project and isn’t copied from the original Donut SMP codebase. Shaded dependencies keep their own licenses.
