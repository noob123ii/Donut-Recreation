package com.notlucy.donutrecreation.baseprotection;

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.util.LogData;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class RevealManager {

  private static final int[] NX = {1, -1, 0, 0, 0, 0};
  private static final int[] NY = {0, 0, 1, -1, 0, 0};
  private static final int[] NZ = {0, 0, 0, 0, 1, -1};

  private final DonutRecreation plugin;
  private final ConcurrentMap<UUID, Set<Long>> revealed = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> revealedUpper = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<UUID>> hiddenEntities = new ConcurrentHashMap<>();
  private final Set<UUID> bypassed = ConcurrentHashMap.newKeySet();

  // Tile-entity (container) positions discovered per chunk, and the positions currently
  // rendered (real) for each viewer. Used to mask containers as AIR until the viewer is close.
  private final ConcurrentMap<Long, Set<Long>> tilesByChunk = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> shownTiles = new ConcurrentHashMap<>();
  private final BlockData airData = Material.AIR.createBlockData();

  private final ConcurrentMap<Long, Set<Long>> geodeByChunk = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> revealedGeodes = new ConcurrentHashMap<>();
  private final Set<Long> scannedGeodes = ConcurrentHashMap.newKeySet();
  private final ConcurrentLinkedDeque<Long> geodeInsertionOrder = new ConcurrentLinkedDeque<>();

  // Tracks which chunks have actually been delivered to each player's client.
  // The server's `world.isChunkLoaded` is not the same as the client having the
  // chunk: at high render distance / 1000 players the client may not yet have a
  // chunk that the server considers loaded. Sending a multi-block-change for a
  // chunk the client doesn't have causes the packet to be dropped silently and
  // the player ends up with no blocks rendered until they relog. PacketHider
  // updates this map on chunk-data and unload-chunk packet sends.
  private final ConcurrentMap<UUID, Set<Long>> deliveredChunks = new ConcurrentHashMap<>();

  private final ConcurrentMap<UUID, Long> lastFloodTick = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> cachedFlood = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, long[]> lastRecomputeStamp = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Integer> playerSalt = new ConcurrentHashMap<>();

  private final int floorY;
  private final int upperY;
  private final int upperRevealRadius;
  private final boolean creativeSpectatorRadius;
  private final boolean tileMaskEnabled;
  private final int tileRenderDist;
  private final long tileRenderDistSq;
  private final int tileAboveRange;
  private final int worldMinY;
  private final int initialRadius;
  private final int movementRadius;
  private final int floodBudget;
  private final int floodRadius;
  private final int floodRadiusSq;
  private final int stickyRadius;
  private final String bypassPerm;

  private final boolean geodeOn;
  private final int geodeRadius;
  private final long geodeRadiusSq;
  private final BlockData fakeAmethyst;
  private final boolean verbose;
  private final int floodThrottleTicks;
  private final int entityScanChunkRadius;
  private final int extraRevealRadius;
  private final int recomputeMinTicks;
  private final int maxRevealedChunks;
  private final int maxGeodeChunks;
  private final int maxRevealHidePerRecompute;
  private final BlockData[] fakeFloorPalette;

  public RevealManager(DonutRecreation plugin) {
    this.plugin = plugin;
    var cfg = plugin.getConfig();
    this.floorY = cfg.getInt("hider.hide-below-y", 0);
    this.upperY = Math.max(this.floorY, cfg.getInt("hider.barrier-upper-y", 10));
    this.upperRevealRadius = Math.max(1, cfg.getInt("hider.upper-reveal-radius", 4));
    this.creativeSpectatorRadius =
        cfg.getBoolean("hider.creative-spectator-radius-reveal", true);
    this.tileMaskEnabled = cfg.getBoolean("hider.tile-entity-mask-enabled", true);
    this.tileRenderDist = Math.max(0, cfg.getInt("hider.tile-entity-render-distance", 10));
    this.tileRenderDistSq = (long) tileRenderDist * tileRenderDist;
    this.tileAboveRange = Math.max(0, cfg.getInt("hider.tile-entity-mask-above-range", 100));
    this.worldMinY = cfg.getInt("hider.world-min-y", -64);
    this.initialRadius = cfg.getInt("hider.reveal-initial-radius", 3);
    this.movementRadius = cfg.getInt("hider.reveal-movement-radius", 2);

    this.floodBudget = cfg.getInt("hider.flood-fill-budget",
        cfg.getInt("hider.cave-flood-budget", 80_000));
    this.floodRadius = cfg.getInt("hider.flood-fill-block-radius",
        cfg.getInt("hider.cave-flood-block-radius", 96));
    this.floodRadiusSq = floodRadius * floodRadius;

    this.stickyRadius = cfg.getInt("hider.sticky-radius", 10);
    this.bypassPerm = cfg.getString(
        "hider.bypass-permission", "donutrecreation.hider.bypass");
    this.fakeFloorPalette = new BlockData[]{
        Material.DEEPSLATE.createBlockData(),
        Material.TUFF.createBlockData(),
        Material.COBBLED_DEEPSLATE.createBlockData(),
        Material.STONE.createBlockData()
    };
    this.extraRevealRadius = Math.max(0,
        cfg.getInt("hider.reveal-edge-extra-radius", 1));
    this.recomputeMinTicks = Math.max(0,
        cfg.getInt("hider.recompute-min-ticks", 2));
    this.maxRevealedChunks = Math.max(64,
        cfg.getInt("hider.max-revealed-chunks-per-player", 4096));
    this.maxGeodeChunks = Math.max(256,
        cfg.getInt("hider.max-geode-chunks", 16384));
    this.maxRevealHidePerRecompute = Math.max(1,
        cfg.getInt("hider.max-reveal-hide-per-recompute", 12));

    this.geodeOn = cfg.getBoolean("hider.geode-hide-enabled", true);
    this.geodeRadius = cfg.getInt("hider.geode-reveal-radius", 8);
    this.geodeRadiusSq = (long) geodeRadius * geodeRadius;
    this.fakeAmethyst = Material.STONE.createBlockData();
    this.verbose = cfg.getBoolean("hider.verbose-logging", false);
    this.floodThrottleTicks = cfg.getInt("hider.flood-fill-throttle-ticks", 10);
    this.entityScanChunkRadius = cfg.getInt("hider.entity-scan-chunk-radius", 8);

    LogData.get().info("[hider] up; floor=" + floorY
        + " r=" + initialRadius + "/" + movementRadius
        + " sticky=" + stickyRadius
        + " flood=" + floodBudget + "/" + floodRadius
        + " geode=" + geodeOn + "/" + geodeRadius
        + (verbose ? " verbose" : ""));
  }

  public int hideBelowY() {
    return floorY;
  }

  /** Upper barrier Y; the band [hideBelowY, upperBarrierY) is the first masked tier. */
  public int upperBarrierY() {
    return upperY;
  }

  public boolean tileMaskEnabled() {
    return tileMaskEnabled;
  }

  public int worldMinY() {
    return worldMinY;
  }

  public int initialRadius() {
    return initialRadius;
  }

  public int movementRadius() {
    return movementRadius;
  }

  public boolean geodeHideEnabled() {
    return geodeOn;
  }

  public boolean verboseLogging() {
    return verbose;
  }

  public DonutRecreation plugin() {
    return plugin;
  }

  public static long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
  }

  public static int keyX(long key) {
    return (int) (key >> 32);
  }

  public static int keyZ(long key) {
    return (int) key;
  }

  public static long packPos(int x, int y, int z) {
    long bx = (x + (1 << 23)) & 0xFFFFFFL;
    long bz = (z + (1 << 23)) & 0xFFFFFFL;
    long by = (y + (1 << 11)) & 0xFFFL;
    return (bx << 36) | (bz << 12) | by;
  }

  public static int unpackX(long p) {
    return (int) ((p >>> 36) & 0xFFFFFFL) - (1 << 23);
  }

  public static int unpackZ(long p) {
    return (int) ((p >>> 12) & 0xFFFFFFL) - (1 << 23);
  }

  public static int unpackY(long p) {
    return (int) (p & 0xFFFL) - (1 << 11);
  }

  public boolean isBypassed(Player player) {
    if (player == null) {
      return false;
    }
    if (bypassed.contains(player.getUniqueId())) {
      return true;
    }
    return bypassPerm != null && !bypassPerm.isEmpty()
        && player.hasPermission(bypassPerm);
  }

  public void markRuntimeBypass(UUID id) {
    if (id != null) {
      bypassed.add(id);
    }
  }

  public void clearRuntimeBypass(UUID id) {
    if (id != null) {
      bypassed.remove(id);
    }
  }

  public boolean isRevealed(Player player, int chunkX, int chunkZ) {
    if (isBypassed(player)) {
      return true;
    }
    Set<Long> set = revealed.get(player.getUniqueId());
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
  }

  /** Whether the upper band [hideBelowY, upperBarrierY) of this chunk is revealed for player. */
  public boolean isUpperRevealed(Player player, int chunkX, int chunkZ) {
    if (isBypassed(player)) {
      return true;
    }
    if (upperY <= floorY) {
      return true;
    }
    Set<Long> set = revealedUpper.get(player.getUniqueId());
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
  }

  // ---- Tile-entity (container) masking -------------------------------------------------

  /** Records the container tile positions found in a chunk (called from the chunk rewrite). */
  public void recordTiles(long chunkKey, Set<Long> positions) {
    if (!tileMaskEnabled) {
      return;
    }
    if (positions == null || positions.isEmpty()) {
      tilesByChunk.remove(chunkKey);
      return;
    }
    Set<Long> backing = ConcurrentHashMap.newKeySet(positions.size());
    backing.addAll(positions);
    tilesByChunk.put(chunkKey, backing);
  }

  public Set<Long> tilePositions(long chunkKey) {
    return tilesByChunk.get(chunkKey);
  }

  /**
   * Whether a tile entity at world-Y {@code tileY} falls inside the masking zone for a viewer
   * standing at {@code viewerY}: always below the lower barrier, and up to
   * {@code tile-entity-mask-above-range} blocks above it while the viewer is under the barrier.
   */
  public boolean tileInMaskZone(int viewerY, int tileY) {
    if (!tileMaskEnabled) {
      return false;
    }
    if (tileY < floorY) {
      return true;
    }
    return viewerY <= floorY - 1 && tileY <= floorY + tileAboveRange;
  }

  /** Whether the viewer is close enough (3D) to render a tile at the given block centre. */
  public boolean tileWithinRender(Player viewer, int x, int y, int z) {
    Location loc = viewer.getLocation();
    double dx = x + 0.5 - loc.getX();
    double dy = y + 0.5 - loc.getY();
    double dz = z + 0.5 - loc.getZ();
    return dx * dx + dy * dy + dz * dz <= tileRenderDistSq;
  }

  /**
   * Whether a tile at (x,y,z) must be masked as air for this viewer right now: it is in the
   * masking zone for the viewer's depth and the viewer is beyond the render distance.
   */
  public boolean shouldMaskTile(Player viewer, int x, int y, int z) {
    if (!tileMaskEnabled || isBypassed(viewer)) {
      return false;
    }
    int viewerY = viewer.getLocation().getBlockY();
    return tileInMaskZone(viewerY, y) && !tileWithinRender(viewer, x, y, z);
  }

  public void recordGeodeChunk(long chunkKey, Set<Long> positions) {
    scannedGeodes.add(chunkKey);
    if (positions == null || positions.isEmpty()) {
      geodeByChunk.remove(chunkKey);
      return;
    }
    Set<Long> backing = ConcurrentHashMap.newKeySet(positions.size());
    backing.addAll(positions);
    Set<Long> prior = geodeByChunk.put(chunkKey, backing);
    if (prior == null) {
      geodeInsertionOrder.add(chunkKey);
      evictGeodeOverflow();
    }
  }

  private void evictGeodeOverflow() {
    while (geodeByChunk.size() > maxGeodeChunks) {
      Long oldest = geodeInsertionOrder.pollFirst();
      if (oldest == null) {
        return;
      }
      geodeByChunk.remove(oldest);
      scannedGeodes.remove(oldest);
    }
  }

  public boolean wasGeodeScanned(long chunkKey) {
    return scannedGeodes.contains(chunkKey);
  }

  public Set<Long> geodePositions(long chunkKey) {
    return geodeByChunk.get(chunkKey);
  }

  public void recordAmethystAt(int x, int y, int z) {
    long ck = chunkKey(x >> 4, z >> 4);
    Set<Long> set = geodeByChunk.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet());
    set.add(packPos(x, y, z));
    scannedGeodes.add(ck);
  }

  public void forgetAmethystAt(int x, int y, int z) {
    Set<Long> set = geodeByChunk.get(chunkKey(x >> 4, z >> 4));
    if (set != null) {
      set.remove(packPos(x, y, z));
    }
  }

  public boolean isGeodeRevealedFor(Player player, int chunkX, int chunkZ) {
    if (!geodeOn || isBypassed(player)) {
      return true;
    }
    Set<Long> positions = geodeByChunk.get(chunkKey(chunkX, chunkZ));
    if (positions == null || positions.isEmpty()) {
      return true;
    }
    Location loc = player.getLocation();
    int px = loc.getBlockX();
    int py = loc.getBlockY();
    int pz = loc.getBlockZ();
    long rsq = geodeRadiusSq;
    for (long packed : positions) {
      long dx = unpackX(packed) - px;
      long dy = unpackY(packed) - py;
      long dz = unpackZ(packed) - pz;
      if (dx * dx + dy * dy + dz * dz <= rsq) {
        return true;
      }
    }
    return false;
  }

  public void recomputeForPlayer(Player player) {
    if (!player.isOnline()) {
      return;
    }
    UUID id = player.getUniqueId();

    if (isBypassed(player)) {
      Set<Long> previous = revealed.remove(id);
      if (previous != null) {
        previous.clear();
      }
      Set<Long> previousUpper = revealedUpper.remove(id);
      if (previousUpper != null) {
        previousUpper.clear();
      }
      shownTiles.remove(id);
      Set<UUID> hidden = hiddenEntities.remove(id);
      if (hidden != null && !hidden.isEmpty()) {
        for (Entity e : player.getWorld().getEntities()) {
          if (hidden.contains(e.getUniqueId())) {
            player.showEntity(plugin, e);
          }
        }
      }
      recomputeGeodeForPlayer(player);
      return;
    }

    Location loc = player.getLocation();
    int pcx = loc.getBlockX() >> 4;
    int pcz = loc.getBlockZ() >> 4;

    if (shouldSkipRecompute(id, pcx, pcz, loc.getBlockY())) {
      // Still run tile proximity each cycle so containers reveal/hide as the player walks
      // within a chunk (where the chunk-level recompute is throttled out).
      recomputeTiles(player, id, pcx, pcz);
      recomputeGeodeForPlayer(player);
      return;
    }

    boolean radiusMode = creativeSpectatorRadius;
    int jitter = extraRevealJitter(id);
    Set<Long> desired = new HashSet<>();
    int py = loc.getBlockY();
    if (py < floorY) {
      desired.add(chunkKey(pcx, pcz));
      if (radiusMode) {
        // Creative/Spectator: reveal a solid radius so chunks load even in solid rock,
        // where the air-connected cave flood-fill would otherwise reveal nothing.
        addSquare(desired, pcx, pcz,
            Math.max(upperRevealRadius, Math.max(initialRadius, movementRadius)));
      } else {
        Set<Long> caves = throttledFlood(id, player.getWorld(),
            loc.getBlockX(), py, loc.getBlockZ());
        int edge = 1 + extraRevealRadius + jitter;
        int checked = 0;
        int passed = 0;
        for (long key : caves) {
          int cx = keyX(key);
          int cz = keyZ(key);
          for (int dx = -edge; dx <= edge; dx++) {
            for (int dz = -edge; dz <= edge; dz++) {
              int tcx = cx + dx;
              int tcz = cz + dz;
              checked++;
              if (hasLineOfSightToFloor(player, tcx, tcz)) {
                passed++;
                desired.add(chunkKey(tcx, tcz));
              }
            }
          }
        }
        int finalChecked = checked;
        int finalPassed = passed;
        LogData.get().fine(() -> "[hider] los " + player.getName()
            + " caves=" + caves.size() + " checked=" + finalChecked
            + " passed=" + finalPassed);
      }
    } else {
      // Above the floor: do a proper LOS scan over a radius around the player.
      // The previous implementation gated on `py <= floorY + 6` AND a single
      // straight-down `canSeeFloor` check, which meant a player flying any
      // higher, or sitting in a water column above an opening, could never
      // reveal their own base. `hasLineOfSightToFloor` already aims at
      // floorY-1 (the cave layer) and walks the ray through non-occluding
      // blocks, so it works correctly through air and water.
      int radius = Math.max(initialRadius, movementRadius);
      if (radius > 0) {
        for (int dx = -radius; dx <= radius; dx++) {
          for (int dz = -radius; dz <= radius; dz++) {
            int tcx = pcx + dx;
            int tcz = pcz + dz;
            if (hasLineOfSightToFloor(player, tcx, tcz)) {
              desired.add(chunkKey(tcx, tcz));
            }
          }
        }
      }
    }

    Set<Long> current = revealed.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());

    Set<Long> toReveal = new HashSet<>(Math.max(8, desired.size()));
    toReveal.addAll(desired);
    toReveal.removeAll(current);
    Set<Long> toHide = new HashSet<>(Math.max(8, current.size()));
    toHide.addAll(current);
    toHide.removeAll(desired);

    if (stickyRadius > 0 && !toHide.isEmpty()) {
      int r = stickyRadius;
      Iterator<Long> it = toHide.iterator();
      while (it.hasNext()) {
        long k = it.next();
        if (Math.abs(keyX(k) - pcx) <= r && Math.abs(keyZ(k) - pcz) <= r) {
          it.remove();
        }
      }
    }

    int sent = 0;
    for (long k : toReveal) {
      if (sent >= maxRevealHidePerRecompute) {
        break;
      }
      sendUnderworldBlocks(player, keyX(k), keyZ(k));
      current.add(k);
      sent++;
    }
    for (long k : toHide) {
      if (sent >= maxRevealHidePerRecompute) {
        break;
      }
      hideUnderworldBlocks(player, keyX(k), keyZ(k));
      current.remove(k);
      sent++;
    }
    if (verbose && (!toReveal.isEmpty() || !toHide.isEmpty())) {
      LogData.get().info("[hider] " + player.getName()
          + " +" + toReveal.size() + " -" + toHide.size()
          + " sent=" + sent);
    }

    enforceRevealedCap(current, pcx, pcz);

    recomputeUpperBand(player, id, pcx, pcz, py);
    recomputeTiles(player, id, pcx, pcz);

    recomputeGeodeForPlayer(player);
    updateEntityVisibility(player);
  }

  private boolean isRadiusGamemode(Player player) {
    org.bukkit.GameMode gm = player.getGameMode();
    return gm == org.bukkit.GameMode.CREATIVE || gm == org.bukkit.GameMode.SPECTATOR;
  }

  /**
   * Reveals/hides the upper band [floorY, upperY) as a square radius around the viewer while
   * they are below the upper barrier; hides it again once they climb back above it.
   */
  private void recomputeUpperBand(Player player, UUID id, int pcx, int pcz, int py) {
    if (upperY <= floorY) {
      return;
    }
    Set<Long> desiredUpper = new HashSet<>();
    if (py < upperY) {
      addSquare(desiredUpper, pcx, pcz, upperRevealRadius);
    }
    Set<Long> current = revealedUpper.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
    Set<Long> toReveal = new HashSet<>(desiredUpper);
    toReveal.removeAll(current);
    Set<Long> toHide = new HashSet<>(current);
    toHide.removeAll(desiredUpper);

    if (stickyRadius > 0 && !toHide.isEmpty()) {
      int r = stickyRadius;
      Iterator<Long> it = toHide.iterator();
      while (it.hasNext()) {
        long k = it.next();
        if (Math.abs(keyX(k) - pcx) <= r && Math.abs(keyZ(k) - pcz) <= r) {
          it.remove();
        }
      }
    }

    if (toReveal.isEmpty() && toHide.isEmpty()) {
      return;
    }
    for (long k : toReveal) {
      sendUpperBand(player, keyX(k), keyZ(k), false);
      current.add(k);
    }
    for (long k : toHide) {
      sendUpperBand(player, keyX(k), keyZ(k), true);
      current.remove(k);
    }
    enforceRevealedCap(current, pcx, pcz);
  }

  /**
   * Sends real/fake block data for the upper band [floorY, upperY) of a chunk.
   * {@code mask=true} masks the band with the fake-floor palette; {@code mask=false} restores
   * the real blocks (tile-entity containers are masked separately by the packet layer).
   */
  private void sendUpperBand(Player player, int chunkX, int chunkZ, boolean mask) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }
    if (!isChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) {
      return;
    }
    int lo = floorY;
    int hi = upperY;
    if (lo >= hi) {
      return;
    }
    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    BlockData[] palette = fakeFloorPalette;
    int paletteLen = palette.length;
    int salt = saltFor(player.getUniqueId());

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      int wx = baseX + x;
      for (int z = 0; z < 16; z++) {
        int wz = baseZ + z;
        for (int y = lo; y < hi; y++) {
          BlockData data;
          if (mask) {
            int idx = Math.floorMod(scrambleHash(wx ^ salt, y, wz ^ salt), paletteLen);
            data = palette[idx];
          } else {
            data = chunk.getBlock(x, y, z).getBlockData();
          }
          batch.put(new Location(world, wx, y, wz), data);
          if (++n >= batchSize) {
            player.sendMultiBlockChange(batch);
            batch.clear();
            n = 0;
          }
        }
      }
    }
    if (!batch.isEmpty()) {
      player.sendMultiBlockChange(batch);
    }
  }

  /**
   * Proximity reveal for tile-entity containers: shows their real block once the viewer comes
   * within the render distance, and re-masks them as air when the viewer moves away. The
   * initial chunk/band packets already air-mask far tiles at the packet layer; this handles the
   * dynamic walk-up reveal that no chunk resend would otherwise cover.
   */
  private void recomputeTiles(Player player, UUID id, int pcx, int pcz) {
    if (!tileMaskEnabled) {
      return;
    }
    World world = player.getWorld();
    Set<Long> shown = shownTiles.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
    int viewerY = player.getLocation().getBlockY();
    int chunkRange = Math.max(1, (tileRenderDist >> 4) + 1);

    Map<Location, BlockData> reveals = new HashMap<>();
    for (int dx = -chunkRange; dx <= chunkRange; dx++) {
      for (int dz = -chunkRange; dz <= chunkRange; dz++) {
        int cx = pcx + dx;
        int cz = pcz + dz;
        if (!world.isChunkLoaded(cx, cz)) {
          continue;
        }
        Set<Long> tiles = tilesByChunk.get(chunkKey(cx, cz));
        if (tiles == null || tiles.isEmpty()) {
          continue;
        }
        for (long packed : tiles) {
          int x = unpackX(packed);
          int y = unpackY(packed);
          int z = unpackPosZ(packed);
          if (tileInMaskZone(viewerY, y) && tileWithinRender(player, x, y, z)) {
            if (shown.add(packed)) {
              reveals.put(new Location(world, x, y, z),
                  world.getBlockAt(x, y, z).getBlockData());
            }
          }
        }
      }
    }

    Map<Location, BlockData> hides = new HashMap<>();
    Iterator<Long> it = shown.iterator();
    while (it.hasNext()) {
      long packed = it.next();
      int x = unpackX(packed);
      int y = unpackY(packed);
      int z = unpackPosZ(packed);
      if (!(tileInMaskZone(viewerY, y) && tileWithinRender(player, x, y, z))) {
        hides.put(new Location(world, x, y, z), airData);
        it.remove();
      }
    }

    if (!reveals.isEmpty()) {
      player.sendMultiBlockChange(reveals);
    }
    if (!hides.isEmpty()) {
      player.sendMultiBlockChange(hides);
    }
  }

  private static int unpackPosZ(long p) {
    return unpackZ(p);
  }

  public void recomputeGeodeForPlayer(Player player) {
    if (!geodeOn) {
      return;
    }
    UUID viewerId = player.getUniqueId();
    if (isBypassed(player)) {
      Set<Long> previous = revealedGeodes.remove(viewerId);
      if (previous != null && !previous.isEmpty()) {
        for (long ck : previous) {
          sendRealAmethyst(player, ck);
        }
      }
      return;
    }

    Location loc = player.getLocation();
    int pcx = loc.getBlockX() >> 4;
    int pcz = loc.getBlockZ() >> 4;
    int range = Math.max(1, (geodeRadius + 15) >> 4) + 1;

    Set<Long> desired = new HashSet<>();
    for (int dx = -range; dx <= range; dx++) {
      for (int dz = -range; dz <= range; dz++) {
        int cx = pcx + dx;
        int cz = pcz + dz;
        long ck = chunkKey(cx, cz);
        Set<Long> positions = geodeByChunk.get(ck);
        if (positions == null || positions.isEmpty()) {
          continue;
        }
        if (isGeodeRevealedFor(player, cx, cz)) {
          desired.add(ck);
        }
      }
    }

    Set<Long> current = revealedGeodes.computeIfAbsent(
        viewerId, k -> ConcurrentHashMap.newKeySet());
    Set<Long> toReveal = new HashSet<>(desired);
    toReveal.removeAll(current);
    Set<Long> toHide = new HashSet<>(current);
    toHide.removeAll(desired);
    if (toReveal.isEmpty() && toHide.isEmpty()) {
      return;
    }
    for (long ck : toReveal) {
      sendRealAmethyst(player, ck);
      current.add(ck);
    }
    for (long ck : toHide) {
      sendFakeAmethyst(player, ck);
      current.remove(ck);
    }
    if (verbose && (!toReveal.isEmpty() || !toHide.isEmpty())) {
      LogData.get().info("[geode] " + player.getName()
          + " +" + toReveal.size() + " -" + toHide.size());
    }
  }

  private void sendRealAmethyst(Player player, long chunkKey) {
    Set<Long> positions = geodeByChunk.get(chunkKey);
    if (positions == null || positions.isEmpty()) {
      return;
    }
    int cx = keyX(chunkKey);
    int cz = keyZ(chunkKey);
    if (!isChunkDeliveredTo(player.getUniqueId(), cx, cz)) {
      return;
    }
    World world = player.getWorld();
    Map<Location, BlockData> changes = new HashMap<>(positions.size());

    long[] stale = null;
    int staleLen = 0;

    for (long packed : positions) {
      int x = unpackX(packed);
      int y = unpackY(packed);
      int z = unpackZ(packed);
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        continue;
      }
      Block live = world.getBlockAt(x, y, z);
      changes.put(new Location(world, x, y, z), live.getBlockData());
      if (!isAmethyst(live.getType())) {
        if (stale == null) {
          stale = new long[8];
        } else if (staleLen == stale.length) {
          long[] grown = new long[stale.length << 1];
          System.arraycopy(stale, 0, grown, 0, staleLen);
          stale = grown;
        }
        stale[staleLen++] = packed;
      }
    }
    if (!changes.isEmpty()) {
      player.sendMultiBlockChange(changes);
    }
    if (stale != null) {
      for (int i = 0; i < staleLen; i++) {
        long p = stale[i];
        forgetAmethystAt(unpackX(p), unpackY(p), unpackZ(p));
      }
    }
  }

  private void sendFakeAmethyst(Player player, long chunkKey) {
    Set<Long> positions = geodeByChunk.get(chunkKey);
    if (positions == null || positions.isEmpty()) {
      return;
    }
    int cx = keyX(chunkKey);
    int cz = keyZ(chunkKey);
    if (!isChunkDeliveredTo(player.getUniqueId(), cx, cz)) {
      return;
    }
    World world = player.getWorld();
    Map<Location, BlockData> changes = new HashMap<>(positions.size());
    BlockData belowMask = fakeFloorPalette[0];
    BlockData aboveMask = fakeAmethyst;
    for (long packed : positions) {
      int y = unpackY(packed);
      BlockData mask = (y < floorY) ? belowMask : aboveMask;
      changes.put(new Location(world, unpackX(packed), y, unpackZ(packed)), mask);
    }
    if (!changes.isEmpty()) {
      player.sendMultiBlockChange(changes);
    }
  }

  private static boolean isAmethyst(Material m) {
    return m == Material.AMETHYST_BLOCK
        || m == Material.BUDDING_AMETHYST
        || m == Material.AMETHYST_CLUSTER
        || m == Material.SMALL_AMETHYST_BUD
        || m == Material.MEDIUM_AMETHYST_BUD
        || m == Material.LARGE_AMETHYST_BUD;
  }

  public void updateEntityVisibility(Player player) {
    if (!player.isOnline() || isBypassed(player)) {
      return;
    }
    UUID viewerId = player.getUniqueId();
    Set<UUID> hidden = hiddenEntities.computeIfAbsent(
        viewerId, k -> ConcurrentHashMap.newKeySet());
    Location playerLoc = player.getLocation();
    boolean above = playerLoc.getY() >= floorY;
    int pcx = playerLoc.getBlockX() >> 4;
    int pcz = playerLoc.getBlockZ() >> 4;
    int radius = entityScanChunkRadius;
    double blockRadius = radius * 16.0;
    double proximitySq = 10.0 * 10.0;

    Iterable<Entity> nearby;
    try {
      nearby = player.getWorld().getNearbyEntities(playerLoc,
          blockRadius, Math.max(blockRadius, 64.0), blockRadius);
    } catch (Throwable ignored) {
      nearby = player.getWorld().getEntities();
    }
    for (Entity e : nearby) {
      UUID eid = e.getUniqueId();
      if (eid.equals(viewerId)) {
        continue;
      }
      Location l = e.getLocation();
      int ecx = l.getBlockX() >> 4;
      int ecz = l.getBlockZ() >> 4;
      if (Math.abs(ecx - pcx) > radius || Math.abs(ecz - pcz) > radius) {
        if (hidden.remove(eid)) {
          player.showEntity(plugin, e);
        }
        continue;
      }
      boolean below = l.getY() < floorY;
      boolean isPlayer = e instanceof Player;

      boolean shouldHide;
      if (isPlayer && below) {
        shouldHide = true;
      } else if (above) {
        shouldHide = below;
      } else {
        shouldHide = below && !isRevealed(player, l.getBlockX() >> 4, l.getBlockZ() >> 4);
      }

      if (!shouldHide && below) {
        double dx = l.getX() - playerLoc.getX();
        double dy = l.getY() - playerLoc.getY();
        double dz = l.getZ() - playerLoc.getZ();
        if (dx * dx + dy * dy + dz * dz > proximitySq) {
          shouldHide = true;
        }
      }

      if (shouldHide) {
        if (hidden.add(eid)) {
          player.hideEntity(plugin, e);
        }
      } else if (hidden.remove(eid)) {
        player.showEntity(plugin, e);
      }
    }
  }

  public void clearEntityVisibility(UUID playerId) {
    hiddenEntities.remove(playerId);
  }

  public void sendUnderworldBlocks(Player player, int chunkX, int chunkZ) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }
    // Skip the per-block patch if the client hasn't actually received this
    // chunk yet: the chunk packet itself will arrive later and PacketHider
    // will pass through real blocks because we'll be in the `revealed` set
    // by then. Sending an MBC now just gets silently dropped, which is the
    // root cause of the "caves don't load until I relog" bug.
    if (!isChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) {
      return;
    }
    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
    int minY = Math.max(worldMinY, world.getMinHeight());
    int maxY = floorY;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    BlockData[] palette = fakeFloorPalette;
    int paletteLen = palette.length;
    int salt = saltFor(player.getUniqueId());

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      int wx = baseX + x;
      for (int z = 0; z < 16; z++) {
        int wz = baseZ + z;
        for (int y = minY; y < maxY; y++) {
          BlockData real = chunk.getBlock(x, y, z).getBlockData();
          Material type = real.getMaterial();
          int idx = Math.floorMod(scrambleHash(wx ^ salt, y, wz ^ salt), paletteLen);
          if ((type == Material.VOID_AIR || type == Material.AIR) && y <= minY + 2) {
            real = palette[idx];
          }
          // Skip if the block already matches the mask the client currently sees
          if (real.equals(palette[idx])) {
            continue;
          }
          batch.put(new Location(world, wx, y, wz), real);
          if (++n >= batchSize) {
            player.sendMultiBlockChange(batch);
            batch.clear();
            n = 0;
          }
        }
      }
    }
    if (!batch.isEmpty()) {
      player.sendMultiBlockChange(batch);
    }
  }

  public void hideUnderworldBlocks(Player player, int chunkX, int chunkZ) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }
    if (!isChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) {
      return;
    }
    int minY = Math.max(worldMinY, world.getMinHeight());
    int maxY = floorY;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    BlockData[] palette = fakeFloorPalette;
    int paletteLen = palette.length;
    int salt = saltFor(player.getUniqueId());

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      int wx = baseX + x;
      for (int z = 0; z < 16; z++) {
        int wz = baseZ + z;
        for (int y = minY; y < maxY; y++) {
          int idx = Math.floorMod(scrambleHash(wx ^ salt, y, wz ^ salt), paletteLen);
          BlockData fake = palette[idx];
          batch.put(new Location(world, wx, y, wz), fake);
          if (++n >= batchSize) {
            player.sendMultiBlockChange(batch);
            batch.clear();
            n = 0;
          }
        }
      }
    }
    if (!batch.isEmpty()) {
      player.sendMultiBlockChange(batch);
    }
  }

  public void revealAndSend(Player player, int centerX, int centerZ, int radius) {
    Set<Long> set = revealed.computeIfAbsent(
        player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        int cx = centerX + dx;
        int cz = centerZ + dz;
        if (set.add(chunkKey(cx, cz))) {
          sendUnderworldBlocks(player, cx, cz);
        }
      }
    }
  }

  private Set<Long> floodFillCave(World world, int sx, int sy, int sz) {
    Set<Long> chunks = new HashSet<>();
    int minY = Math.max(worldMinY, world.getMinHeight());
    int maxY = floorY - 1;
    if (sy < minY || sy > maxY) {
      return chunks;
    }

    Set<Long> visited = new HashSet<>();
    Deque<int[]> queue = new ArrayDeque<>();
    queue.add(new int[]{sx, sy, sz});
    visited.add(voxelKey(sx, sy, sz));
    int budget = floodBudget;
    int rsq = floodRadiusSq;

    while (!queue.isEmpty() && budget-- > 0) {
      int[] cur = queue.poll();
      int x = cur[0];
      int y = cur[1];
      int z = cur[2];
      chunks.add(chunkKey(x >> 4, z >> 4));

      for (int n = 0; n < 6; n++) {
        int nx = x + NX[n];
        int ny = y + NY[n];
        int nz = z + NZ[n];
        if (ny < minY || ny > maxY) {
          continue;
        }
        int dxr = nx - sx;
        int dzr = nz - sz;
        if (dxr * dxr + dzr * dzr > rsq) {
          continue;
        }
        if (!visited.add(voxelKey(nx, ny, nz))) {
          continue;
        }
        if (!world.isChunkLoaded(nx >> 4, nz >> 4)) {
          continue;
        }
        if (isPassable(world.getBlockAt(nx, ny, nz))) {
          queue.add(new int[]{nx, ny, nz});
        }
      }
    }
    if (verbose && budget <= 0) {
      LogData.get().info("[hider] flood ran out of budget at " + chunks.size() + " chunks");
    }
    return chunks;
  }

  private static long voxelKey(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38)
        | ((long) (z & 0x3FFFFFF) << 12)
        | (y & 0xFFF);
  }

  private boolean hasLineOfSightToFloor(Player player, int chunkX, int chunkZ) {
    Location eye = player.getEyeLocation();
    World world = eye.getWorld();
    if (world == null) {
      return false;
    }
    int pcx = eye.getBlockX() >> 4;
    int pcz = eye.getBlockZ() >> 4;
    double targetX = (chunkX << 4) + 8.0;
    // Aim just under the floor: floorY itself is solid deepslate, so a ray that
    // ends there is blocked by the very block we're trying to reveal beneath.
    // Sampling the cave layer directly is far less restrictive and makes nearby
    // chunks load before the player is pressed right up against the floor.
    double targetY = Math.min(eye.getY(), floorY - 1);
    double targetZ = (chunkZ << 4) + 8.0;

    double dx = targetX - eye.getX();
    double dy = targetY - eye.getY();
    double dz = targetZ - eye.getZ();
    double distSq = dx * dx + dy * dy + dz * dz;
    if (distSq < 0.0001) {
      return true;
    }
    double dist = Math.sqrt(distSq);
    double step = 1.0;
    int steps = (int) (dist / step);
    if (steps <= 0) {
      return true;
    }
    double nx = dx / dist * step;
    double ny = dy / dist * step;
    double nz = dz / dist * step;

    double x = eye.getX();
    double y = eye.getY();
    double z = eye.getZ();
    for (int i = 0; i < steps; i++) {
      x += nx;
      y += ny;
      z += nz;
      int bx = (int) Math.floor(x);
      int by = (int) Math.floor(y);
      int bz = (int) Math.floor(z);
      Block stepBlock = world.getBlockAt(bx, by, bz);
      if (stepBlock.getType().isOccluding()) {
        if (verbose) {
          // Tag the wall block that aborted the LOS so the operator can map
          // which deepslate column is preventing reveal of an adjacent chunk.
          LogData.get().info("[hider-los] " + player.getName()
              + " blocked target=" + chunkX + "," + chunkZ
              + " wall=" + stepBlock.getType() + " at " + bx + "," + by + "," + bz
              + " (step " + i + "/" + steps + ")");
        }
        return false;
      }
    }
    return true;
  }

  private static boolean isPassable(Block block) {
    Material m = block.getType();
    if (m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR) {
      return true;
    }
    return !m.isOccluding();
  }

  private void addSquare(Set<Long> set, int cx, int cz, int radius) {
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        set.add(chunkKey(cx + dx, cz + dz));
      }
    }
  }

  /** Clears the recompute throttle stamp so the next recompute always runs (e.g. on gamemode
   * change, where the player's position is unchanged but their view must be rebuilt). */
  public void invalidateRecompute(UUID id) {
    if (id != null) {
      lastRecomputeStamp.remove(id);
    }
  }

  public void removePlayer(Player player) {
    UUID id = player.getUniqueId();
    revealed.remove(id);
    revealedUpper.remove(id);
    shownTiles.remove(id);
    revealedGeodes.remove(id);
    hiddenEntities.remove(id);
    bypassed.remove(id);
    lastFloodTick.remove(id);
    cachedFlood.remove(id);
    lastRecomputeStamp.remove(id);
    playerSalt.remove(id);
    deliveredChunks.remove(id);
  }

  /** Records that the client of {@code id} has received the full chunk packet for
   * (chunkX, chunkZ). Called from PacketHider after a CHUNK_DATA dispatch. */
  public void markChunkDelivered(UUID id, int chunkX, int chunkZ) {
    if (id == null) {
      return;
    }
    deliveredChunks.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet())
        .add(chunkKey(chunkX, chunkZ));
  }

  /** Removes the (chunkX, chunkZ) record on UNLOAD_CHUNK so we don't send block
   * changes for chunks the client has thrown away. */
  public void markChunkUnloaded(UUID id, int chunkX, int chunkZ) {
    if (id == null) {
      return;
    }
    Set<Long> set = deliveredChunks.get(id);
    if (set != null) {
      set.remove(chunkKey(chunkX, chunkZ));
    }
  }

  /** Whether the client of {@code id} currently has (chunkX, chunkZ) loaded. If
   * false, multi-block-change packets for that chunk would be dropped client
   * side; callers should skip them and rely on PacketHider rewriting the chunk
   * packet itself when it is eventually delivered. */
  public boolean isChunkDeliveredTo(UUID id, int chunkX, int chunkZ) {
    if (id == null) {
      return false;
    }
    Set<Long> set = deliveredChunks.get(id);
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
  }

  public int saltFor(UUID id) {
    if (id == null) {
      return 0;
    }
    Integer s = playerSalt.get(id);
    if (s != null) {
      return s;
    }
    int fresh = ThreadLocalRandom.current().nextInt();
    Integer prior = playerSalt.putIfAbsent(id, fresh);
    return prior == null ? fresh : prior;
  }

  public void rotateSalt(UUID id) {
    if (id == null) {
      return;
    }
    playerSalt.put(id, ThreadLocalRandom.current().nextInt());
    Set<Long> set = revealed.remove(id);
    if (set != null) {
      set.clear();
    }
  }

  public int extraRevealJitter(UUID id) {
    if (id == null) {
      return 0;
    }
    long mix = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    return ((int) (mix ^ (mix >>> 32))) & 1;
  }

  public boolean shouldSuppressEntityFor(Player viewer, double x, double y, double z) {
    if (viewer == null || isBypassed(viewer)) {
      return false;
    }
    if (y >= floorY) {
      return false;
    }
    int cx = ((int) Math.floor(x)) >> 4;
    int cz = ((int) Math.floor(z)) >> 4;
    return !isRevealed(viewer, cx, cz);
  }

  private boolean shouldSkipRecompute(UUID id, int pcx, int pcz, int py) {
    if (recomputeMinTicks <= 0) {
      return false;
    }
    long now = currentTick();
    long[] stamp = lastRecomputeStamp.computeIfAbsent(id, k -> new long[4]);
    if (stamp[0] != 0
        && now - stamp[0] < recomputeMinTicks
        && stamp[1] == pcx && stamp[2] == pcz
        && Math.abs(stamp[3] - py) <= 1) {
      return true;
    }
    stamp[0] = now;
    stamp[1] = pcx;
    stamp[2] = pcz;
    stamp[3] = py;
    return false;
  }

  private void enforceRevealedCap(Set<Long> current, int pcx, int pcz) {
    int over = current.size() - maxRevealedChunks;
    if (over <= 0) {
      return;
    }
    java.util.PriorityQueue<long[]> farthest = new java.util.PriorityQueue<>(
        over + 1, (a, b) -> Integer.compare((int) a[1], (int) b[1]));
    for (long key : current) {
      int dist = Math.max(Math.abs(keyX(key) - pcx), Math.abs(keyZ(key) - pcz));
      if (farthest.size() < over) {
        farthest.add(new long[]{key, dist});
      } else if (!farthest.isEmpty() && dist > farthest.peek()[1]) {
        farthest.poll();
        farthest.add(new long[]{key, dist});
      }
    }
    for (long[] entry : farthest) {
      current.remove(entry[0]);
    }
  }

  private Set<Long> throttledFlood(UUID id, World world, int x, int y, int z) {
    long now = currentTick();
    Long last = lastFloodTick.get(id);
    if (last != null && now - last < floodThrottleTicks) {
      Set<Long> cached = cachedFlood.get(id);
      if (cached != null) {
        return new HashSet<>(cached);
      }
    }
    Set<Long> fresh = floodFillCave(world, x, y, z);
    lastFloodTick.put(id, now);
    cachedFlood.put(id, fresh);
    return fresh;
  }

  private static volatile boolean currentTickSupported = true;

  private static long currentTick() {
    if (currentTickSupported) {
      try {
        return Bukkit.getCurrentTick();
      } catch (NoSuchMethodError ignored) {
        currentTickSupported = false;
      }
    }
    return System.nanoTime() / 50_000_000L;
  }

  private static int scrambleHash(int x, int y, int z) {
    int h = x * 0x9E3779B1;
    h ^= Integer.rotateLeft(z * 0x85EBCA77, 13);
    h ^= Integer.rotateLeft(y * 0xC2B2AE3D, 17);
    h ^= (h >>> 16);
    return h;
  }

  public void onChunkUnload(int chunkX, int chunkZ) {
    long ck = chunkKey(chunkX, chunkZ);
    scannedGeodes.remove(ck);
    geodeByChunk.remove(ck);
    tilesByChunk.remove(ck);
  }
}
