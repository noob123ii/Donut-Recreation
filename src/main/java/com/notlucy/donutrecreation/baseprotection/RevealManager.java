package com.notlucy.donutrecreation.baseprotection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class RevealManager {

  private static final int[] NX = {1, -1, 0, 0, 0, 0};
  private static final int[] NY = {0, 0, 1, -1, 0, 0};
  private static final int[] NZ = {0, 0, 0, 0, 1, -1};

  private final DonutRecreation plugin;
  private final ConcurrentMap<UUID, Set<Long>> revealed       = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> revealedUpper  = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<UUID>> hiddenEntities = new ConcurrentHashMap<>();
  private final Set<UUID> bypassed = ConcurrentHashMap.newKeySet();

  private final ConcurrentMap<UUID, Long> lastEntityScanTick = new ConcurrentHashMap<>();

  private final ConcurrentMap<Long, Set<Long>>     geodeByChunk      = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>>     revealedGeodes    = new ConcurrentHashMap<>();
  private final Set<Long>                          scannedGeodes     = ConcurrentHashMap.newKeySet();

  private final ConcurrentMap<UUID, UUID> playerWorldUids = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, World.Environment> playerEnvironments = new ConcurrentHashMap<>();
  private final ConcurrentLinkedDeque<Long>        geodeInsertionOrder = new ConcurrentLinkedDeque<>();

  private final ConcurrentMap<UUID, Set<Long>> deliveredChunks = new ConcurrentHashMap<>();

  private final ConcurrentMap<UUID, Long> surfacedSinceTick = new ConcurrentHashMap<>();

  private com.notlucy.donutrecreation.spawn.manager.GhostBlockManager ghostBlockManager;

  private final ConcurrentMap<UUID, Long>     lastFloodTick       = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> cachedFlood        = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, long[]>   lastRecomputeStamp  = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Integer>  playerSalt          = new ConcurrentHashMap<>();

  private final int     floorY;
  private final int     upperY;
  private final int     upperRevealRadius;
  private final int     worldMinY;
  private final int     initialRadius;
  private final int     movementRadius;
  private final int     floodBudget;
  private final int     floodRadius;
  private final int     floodRadiusSq;
  private final int     stickyRadius;

  private final boolean    geodeOn;
  private final int        geodeRadius;
  private final long       geodeRadiusSq;
  private final int        fakeGeodeAmethystPerChunk;
  private final BlockData  fakeAmethyst;
  private final boolean    verbose;
  private final int        floodThrottleTicks;
  private final int        entityScanChunkRadius;
  private final int        extraRevealRadius;
  private final int        recomputeMinTicks;
  private final int        maxRevealedChunks;
  private final int        maxGeodeChunks;

  private final int        maxRevealHidePerRecompute;
  private final BlockData[] fakeFloorPalette;

  public RevealManager(DonutRecreation plugin) {
    this.plugin = plugin;
    var cfg = plugin.getConfig();
    this.floorY            = cfg.getInt("hider.hide-below-y", 0);
    this.upperY            = Math.max(this.floorY, cfg.getInt("hider.barrier-upper-y", 10));
    this.upperRevealRadius = Math.max(1, cfg.getInt("hider.upper-reveal-radius", 1));
    this.worldMinY         = cfg.getInt("hider.world-min-y", -64);
    this.initialRadius     = cfg.getInt("hider.reveal-initial-radius", 3);
    this.movementRadius    = cfg.getInt("hider.reveal-movement-radius", 2);

    this.floodBudget = cfg.getInt("hider.flood-fill-budget",
        cfg.getInt("hider.cave-flood-budget", 80_000));
    this.floodRadius  = cfg.getInt("hider.flood-fill-block-radius",
        cfg.getInt("hider.cave-flood-block-radius", 96));
    this.floodRadiusSq = floodRadius * floodRadius;

    this.stickyRadius = cfg.getInt("hider.sticky-radius", 10);
    this.fakeFloorPalette = new BlockData[]{Material.DEEPSLATE.createBlockData()};

    this.extraRevealRadius        = Math.max(0, cfg.getInt("hider.reveal-edge-extra-radius", 1));
    this.recomputeMinTicks        = Math.max(0, cfg.getInt("hider.recompute-min-ticks", 2));
    this.maxRevealedChunks        = Math.max(64, cfg.getInt("hider.max-revealed-chunks-per-player", 4096));
    this.maxGeodeChunks           = Math.max(256, cfg.getInt("hider.max-geode-chunks", 16384));

    this.maxRevealHidePerRecompute = Math.max(1, cfg.getInt("hider.max-reveal-hide-per-recompute", 64));

    this.geodeOn      = cfg.getBoolean("hider.geode-hide-enabled", true);
    this.geodeRadius  = cfg.getInt("hider.geode-reveal-radius", 8);
    this.geodeRadiusSq = (long) geodeRadius * geodeRadius;
    this.fakeGeodeAmethystPerChunk = Math.max(1,
        cfg.getInt("hider.fake-geode-amethyst-per-chunk", 24));
    this.fakeAmethyst = Material.STONE.createBlockData();
    this.verbose      = cfg.getBoolean("hider.verbose-logging", false);
    this.floodThrottleTicks   = cfg.getInt("hider.flood-fill-throttle-ticks", 10);
    this.entityScanChunkRadius = cfg.getInt("hider.entity-scan-chunk-radius", 8);

    LogData.get().info("[hider] up; floor=" + floorY
        + " r=" + initialRadius + "/" + movementRadius
        + " sticky=" + stickyRadius
        + " flood=" + floodBudget + "/" + floodRadius
        + " geode=" + geodeOn + "/" + geodeRadius
        + (verbose ? " verbose" : ""));
  }

  public int hideBelowY()   { return floorY; }
  public int upperBarrierY() { return upperY; }
  public int worldMinY()    { return worldMinY; }
  public int initialRadius() { return initialRadius; }
  public int movementRadius() { return movementRadius; }
  public boolean geodeHideEnabled() { return geodeOn; }
  public int fakeGeodeAmethystPerChunk() { return fakeGeodeAmethystPerChunk; }
  public boolean verboseLogging()   { return verbose; }
  public DonutRecreation plugin()   { return plugin; }

  public boolean hasGhostBlockAt(java.util.UUID playerId, int x, int y, int z) {
    return ghostBlockManager != null && ghostBlockManager.hasGhostBlockAt(playerId, x, y, z);
  }

  public static long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
  }
  public static int keyX(long key) { return (int) (key >> 32); }
  public static int keyZ(long key) { return (int) key; }

  public static long worldChunkKey(UUID worldUid, int chunkX, int chunkZ) {

    int worldHash = worldUid.hashCode();
    return (((long) worldHash) << 48) | (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
  }

  public static long packPos(int x, int y, int z) {
    long bx = (x + (1 << 23)) & 0xFFFFFFL;
    long bz = (z + (1 << 23)) & 0xFFFFFFL;
    long by = (y + (1 << 11)) & 0xFFFL;
    return (bx << 36) | (bz << 12) | by;
  }
  public static int unpackX(long p) { return (int) ((p >>> 36) & 0xFFFFFFL) - (1 << 23); }
  public static int unpackZ(long p) { return (int) ((p >>> 12) & 0xFFFFFFL) - (1 << 23); }
  public static int unpackY(long p) { return (int) (p & 0xFFFL) - (1 << 11); }

  public void markRuntimeBypass(UUID id) {
    bypassed.add(id);
  }

  public void clearRuntimeBypass(UUID id) {
    if (id != null) bypassed.remove(id);
  }

  public boolean isRevealed(Player player, int chunkX, int chunkZ) {
    Set<Long> set = revealed.get(player.getUniqueId());
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
  }

  public boolean isUpperRevealed(Player player, int chunkX, int chunkZ) {
    if (upperY <= floorY) return true;
    int py = player.getLocation().getBlockY();
    if (py >= upperY) return true;
    Set<Long> set = revealedUpper.get(player.getUniqueId());
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
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
      if (oldest == null) return;
      geodeByChunk.remove(oldest);
      scannedGeodes.remove(oldest);
    }
  }

  public boolean wasGeodeScanned(long chunkKey) { return scannedGeodes.contains(chunkKey); }
  public Set<Long> geodePositions(long chunkKey) { return geodeByChunk.get(chunkKey); }

  public void recordAmethystAt(int x, int y, int z) {
    long ck = chunkKey(x >> 4, z >> 4);
    geodeByChunk.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet()).add(packPos(x, y, z));
    scannedGeodes.add(ck);
  }

  public void forgetAmethystAt(int x, int y, int z) {
    Set<Long> set = geodeByChunk.get(chunkKey(x >> 4, z >> 4));
    if (set != null) set.remove(packPos(x, y, z));
  }

  public boolean isGeodeRevealedFor(Player player, int chunkX, int chunkZ) {
    if (!geodeOn) return true;
    Set<Long> positions = geodeByChunk.get(chunkKey(chunkX, chunkZ));
    if (positions == null || positions.isEmpty()) return true;
    long ck = chunkKey(chunkX, chunkZ);
    Set<Long> forced = revealedGeodes.get(player.getUniqueId());
    if (forced != null && forced.contains(ck)) return true;
    Location loc = player.getLocation();
    int px = loc.getBlockX(), py = loc.getBlockY(), pz = loc.getBlockZ();
    long rsq = geodeRadiusSq;
    for (long packed : positions) {
      long dx = unpackX(packed) - px;
      long dy = unpackY(packed) - py;
      long dz = unpackZ(packed) - pz;
      if (dx * dx + dy * dy + dz * dz <= rsq) return true;
    }
    return false;
  }

  public void forceRevealGeodeChunk(Player player, int chunkX, int chunkZ) {
    long key = chunkKey(chunkX, chunkZ);
    Set<Long> set = revealedGeodes.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
    if (set.add(key)) {
      sendRealAmethyst(player, key);
    }
  }

  public void setGhostBlockManager(
      com.notlucy.donutrecreation.spawn.manager.GhostBlockManager gbm) {
    this.ghostBlockManager = gbm;
  }

  private ChunkRevealListener chunkRevealCallback;

  @FunctionalInterface
  public interface ChunkRevealListener {
    void onChunkRevealed(Player player, int chunkX, int chunkZ);
  }

  public void setChunkRevealCallback(ChunkRevealListener callback) {
    this.chunkRevealCallback = callback;
  }

  private void notifyChunkRevealed(Player player, int chunkX, int chunkZ) {
    if (chunkRevealCallback != null) {
      chunkRevealCallback.onChunkRevealed(player, chunkX, chunkZ);
    }
  }

  public void recomputeForPlayer(Player player) {
    if (!player.isOnline()) return;
    UUID id  = player.getUniqueId();
    Location loc = player.getLocation();
    int pcx = loc.getBlockX() >> 4;
    int pcz = loc.getBlockZ() >> 4;
    int py  = loc.getBlockY();

    if (shouldSkipRecompute(id, pcx, pcz, py)) {
      recomputeGeodeForPlayer(player);
      updateEntityVisibility(player);
      return;
    }

    Set<Long> current = revealed.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());

    if (py >= upperY) {
      surfacedSinceTick.remove(id);
      recomputeUpperBand(player, id, pcx, pcz, py);
      recomputeGeodeForPlayer(player);
      updateEntityVisibility(player);
      return;
    }

    surfacedSinceTick.remove(id);

    Set<Long> desired = new HashSet<>();
    boolean nearFloor = py <= floorY;
    if (nearFloor) {
      addSquare(desired, pcx, pcz, movementRadius);
      int floodStartY = Math.min(py, floorY - 1);
      desired.addAll(throttledFlood(id, player.getWorld(),
          pcx << 4, floodStartY, pcz << 4));
    }

    Set<Long> toReveal = new HashSet<>(desired);
    toReveal.removeAll(current);

    if (verbose) {
      LogData.get().info("[hider] " + player.getName()
          + " recompute: pcx=" + pcx + " pcz=" + pcz + " py=" + py
          + " desired=" + desired.size() + " current=" + current.size()
          + " +reveal=" + toReveal.size());
    }

    if (toReveal.isEmpty()) {
      recomputeUpperBand(player, id, pcx, pcz, py);
      recomputeGeodeForPlayer(player);
      updateEntityVisibility(player);
      return;
    }

    int sent = 0;
    for (long k : toReveal) {
      if (sent >= maxRevealHidePerRecompute) break;
      if (verbose && sent < 5) {
        LogData.get().info("[hider] " + player.getName()
            + " revealing chunk " + keyX(k) + "," + keyZ(k));
      }

      current.add(k);
      sendUnderworldBlocks(player, keyX(k), keyZ(k));
      notifyChunkRevealed(player, keyX(k), keyZ(k));
      sent++;
    }

    if (verbose && sent > 0) {
      LogData.get().info("[hider] " + player.getName() + " +" + sent);
    }

    enforceRevealedCap(player, current, pcx, pcz);
    recomputeUpperBand(player, id, pcx, pcz, py);
    recomputeGeodeForPlayer(player);
    updateEntityVisibility(player);
  }

  private void recomputeUpperBand(Player player, UUID id, int pcx, int pcz, int py) {
    if (upperY <= floorY) return;

    Set<Long> desiredUpper = new HashSet<>();
    boolean nearFloor = py < floorY + 2;
    boolean nearUpper = py >= floorY && py < upperY + 2;
    if (nearFloor || nearUpper) {
      addSquare(desiredUpper, pcx, pcz, upperRevealRadius);
    }

    Set<Long> current = revealedUpper.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
    Set<Long> toReveal = new HashSet<>(desiredUpper);
    toReveal.removeAll(current);

    if (toReveal.isEmpty()) return;

    for (long k : toReveal) {
      sendUpperBand(player, keyX(k), keyZ(k), false);
      current.add(k);
      notifyChunkRevealed(player, keyX(k), keyZ(k));
    }
  }

  void sendUpperBand(Player player, int chunkX, int chunkZ, boolean mask) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) return;
    if (!isChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) return;
    int lo = floorY, hi = upperY;
    if (lo >= hi) return;

    Chunk chunk  = world.getChunkAt(chunkX, chunkZ);
    int baseX    = chunkX << 4;
    int baseZ    = chunkZ << 4;
    BlockData[] palette  = fakeFloorPalette;
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
          BlockData data = mask
              ? palette[Math.floorMod(scrambleHash(wx ^ salt, y, wz ^ salt), paletteLen)]
              : chunk.getBlock(x, y, z).getBlockData();
          batch.put(new Location(world, wx, y, wz), data);
          if (++n >= batchSize) { player.sendMultiBlockChange(batch); batch.clear(); n = 0; }
        }
      }
    }
    if (!batch.isEmpty()) player.sendMultiBlockChange(batch);
  }

  public void recomputeGeodeForPlayer(Player player) {
    if (!geodeOn) return;
    UUID viewerId = player.getUniqueId();
    Location loc  = player.getLocation();
    int pcx = loc.getBlockX() >> 4;
    int pcz = loc.getBlockZ() >> 4;
    int range = Math.max(1, (geodeRadius + 15) >> 4) + 1;

    Set<Long> desired = new HashSet<>();
    for (int dx = -range; dx <= range; dx++) {
      for (int dz = -range; dz <= range; dz++) {
        long ck = chunkKey(pcx + dx, pcz + dz);
        Set<Long> positions = geodeByChunk.get(ck);
        if (positions == null || positions.isEmpty()) continue;
        if (isGeodeRevealedFor(player, pcx + dx, pcz + dz)) desired.add(ck);
      }
    }

    Set<Long> current = revealedGeodes.computeIfAbsent(viewerId, k -> ConcurrentHashMap.newKeySet());
    Set<Long> toReveal = new HashSet<>(desired); toReveal.removeAll(current);
    Set<Long> toHide   = new HashSet<>(current); toHide.removeAll(desired);
    if (toReveal.isEmpty() && toHide.isEmpty()) return;

    for (long ck : toReveal) { sendRealAmethyst(player, ck);  current.add(ck); }
    for (long ck : toHide)   { sendFakeAmethyst(player, ck);  current.remove(ck); }

    if (verbose && (!toReveal.isEmpty() || !toHide.isEmpty())) {
      LogData.get().info("[geode] " + player.getName()
          + " +" + toReveal.size() + " -" + toHide.size());
    }
  }

  private void sendRealAmethyst(Player player, long chunkKey) {
    Set<Long> positions = geodeByChunk.get(chunkKey);
    if (positions == null || positions.isEmpty()) return;
    int cx = keyX(chunkKey), cz = keyZ(chunkKey);
    if (!isChunkDeliveredTo(player.getUniqueId(), cx, cz)) return;
    World world = player.getWorld();
    Map<Location, BlockData> changes = new HashMap<>(positions.size());
    long[] stale = null;
    int staleLen = 0;
    for (long packed : positions) {
      int x = unpackX(packed), y = unpackY(packed), z = unpackZ(packed);
      if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
      if (ghostBlockManager != null
          && ghostBlockManager.hasGhostBlockAt(player.getUniqueId(), x, y, z)) {
        continue;
      }
      Block live = world.getBlockAt(x, y, z);
      changes.put(new Location(world, x, y, z), live.getBlockData());
      if (!isAmethyst(live.getType())) {
        if (stale == null) stale = new long[8];
        else if (staleLen == stale.length) {
          long[] grown = new long[stale.length << 1];
          System.arraycopy(stale, 0, grown, 0, staleLen);
          stale = grown;
        }
        stale[staleLen++] = packed;
      }
    }
    if (!changes.isEmpty()) player.sendMultiBlockChange(changes);
    if (stale != null) {
      for (int i = 0; i < staleLen; i++) {
        long p = stale[i];
        forgetAmethystAt(unpackX(p), unpackY(p), unpackZ(p));
      }
    }
  }

  private void sendFakeAmethyst(Player player, long chunkKey) {
    Set<Long> positions = geodeByChunk.get(chunkKey);
    if (positions == null || positions.isEmpty()) return;
    int cx = keyX(chunkKey), cz = keyZ(chunkKey);
    if (!isChunkDeliveredTo(player.getUniqueId(), cx, cz)) return;
    World world = player.getWorld();
    Map<Location, BlockData> changes = new HashMap<>(positions.size());
    BlockData belowMask = fakeFloorPalette[0];
    BlockData aboveMask = fakeAmethyst;
    for (long packed : positions) {
      int x = unpackX(packed);
      int y = unpackY(packed);
      int z = unpackZ(packed);
      if (ghostBlockManager != null
          && ghostBlockManager.hasGhostBlockAt(player.getUniqueId(), x, y, z)) {
        continue;
      }
      changes.put(new Location(world, x, y, z),
          (y < floorY) ? belowMask : aboveMask);
    }
    if (!changes.isEmpty()) player.sendMultiBlockChange(changes);
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
    if (!player.isOnline()) return;
    UUID viewerId = player.getUniqueId();

    long nowTick = currentTick();
    Long lastScan = lastEntityScanTick.get(viewerId);
    if (lastScan != null && nowTick - lastScan < 5) return;
    lastEntityScanTick.put(viewerId, nowTick);
    Set<UUID> hidden = hiddenEntities.computeIfAbsent(viewerId, k -> ConcurrentHashMap.newKeySet());
    Location playerLoc = player.getLocation();

    boolean viewerUnderground = playerLoc.getBlockY() < upperY;
    int pcx = playerLoc.getBlockX() >> 4;
    int pcz = playerLoc.getBlockZ() >> 4;
    int radius = entityScanChunkRadius;
    double blockRadius = radius * 16.0;
    double proximitySq = 30.0 * 30.0;

    Iterable<Entity> nearby;
    try {
      nearby = player.getWorld().getNearbyEntities(playerLoc,
          blockRadius, Math.max(blockRadius, 64.0), blockRadius);
    } catch (Exception e) {
      nearby = player.getWorld().getEntities();
    }

    for (Entity e : nearby) {
      UUID eid = e.getUniqueId();
      if (eid.equals(viewerId)) continue;

      Location eLoc = e.getLocation();
      int ecx = eLoc.getBlockX() >> 4;
      int ecz = eLoc.getBlockZ() >> 4;

      if (Math.abs(ecx - pcx) > radius || Math.abs(ecz - pcz) > radius) {
        if (hidden.remove(eid)) player.showEntity(plugin, e);
        continue;
      }

      boolean shouldHide = shouldHideEntity(e, eLoc, player, playerLoc, viewerUnderground, proximitySq);

      if (shouldHide) {
        if (hidden.add(eid)) player.hideEntity(plugin, e);
      } else {
        if (hidden.remove(eid)) player.showEntity(plugin, e);
      }
    }
  }

  private boolean shouldHideEntity(Entity e, Location eLoc, Player viewer, Location viewerLoc,
      boolean viewerUnderground, double proximitySq) {
    boolean underground = eLoc.getY() < upperY;

    if (!underground) return false;
    if (!viewerUnderground) return e instanceof Player;

    int ecx = eLoc.getBlockX() >> 4;
    int ecz = eLoc.getBlockZ() >> 4;

    if (e instanceof org.bukkit.entity.Item) {
      return !isRevealed(viewer, ecx, ecz) && distSq(eLoc, viewerLoc) > proximitySq;
    }

    if (e instanceof org.bukkit.entity.ItemFrame) {
      return distSq(eLoc, viewerLoc) > proximitySq;
    }

    return !isRevealed(viewer, ecx, ecz) && distSq(eLoc, viewerLoc) > proximitySq;
  }

  private static double distSq(Location a, Location b) {
    double dx = a.getX() - b.getX();
    double dy = a.getY() - b.getY();
    double dz = a.getZ() - b.getZ();
    return dx * dx + dy * dy + dz * dz;
  }

  public void clearEntityVisibility(UUID playerId) {
    hiddenEntities.remove(playerId);
  }

  public void sendUnderworldBlocks(Player player, int chunkX, int chunkZ) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) return;
    if (!isChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) return;

    Chunk chunk  = world.getChunkAt(chunkX, chunkZ);
    int minY  = Math.max(worldMinY, world.getMinHeight());
    int maxY  = floorY;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    BlockData[] palette   = fakeFloorPalette;
    int paletteLen = palette.length;
    int salt = saltFor(player.getUniqueId());
    UUID worldUid = world.getUID();
    long chunkKey = chunkKey(chunkX, chunkZ);
    Location playerLoc = player.getLocation();
    UUID playerId = player.getUniqueId();

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      int wx = baseX + x;
      for (int z = 0; z < 16; z++) {
        int wz = baseZ + z;
        for (int y = minY; y < maxY; y++) {
          if (ghostBlockManager != null && ghostBlockManager.hasGhostBlockAt(playerId, wx, y, wz)) {
            continue;
          }
          BlockData real = chunk.getBlock(x, y, z).getBlockData();
          Material type = real.getMaterial();

          if ((type == Material.VOID_AIR || type == Material.AIR) && y <= minY + 2) {
            int idx = Math.floorMod(scrambleHash(wx ^ salt, y, wz ^ salt), paletteLen);
            real = palette[idx];
          }

          batch.put(new Location(world, wx, y, wz), real);
          if (++n >= batchSize) { player.sendMultiBlockChange(batch); batch.clear(); n = 0; }
        }
      }
    }
    if (!batch.isEmpty()) player.sendMultiBlockChange(batch);
  }

  public void hideUnderworldBlocks(Player player, int chunkX, int chunkZ) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) return;
    if (!isChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) return;

    int minY  = Math.max(worldMinY, world.getMinHeight());
    int maxY  = floorY;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    BlockData[] palette   = fakeFloorPalette;
    int paletteLen = palette.length;
    int salt = saltFor(player.getUniqueId());
    UUID playerId = player.getUniqueId();

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      int wx = baseX + x;
      for (int z = 0; z < 16; z++) {
        int wz = baseZ + z;
        for (int y = minY; y < maxY; y++) {
          if (ghostBlockManager != null && ghostBlockManager.hasGhostBlockAt(playerId, wx, y, wz)) {
            continue;
          }
          int idx = Math.floorMod(scrambleHash(wx ^ salt, y, wz ^ salt), paletteLen);
          batch.put(new Location(world, wx, y, wz), palette[idx]);
          if (++n >= batchSize) { player.sendMultiBlockChange(batch); batch.clear(); n = 0; }
        }
      }
    }
    if (!batch.isEmpty()) player.sendMultiBlockChange(batch);
  }

  public void revealAndSend(Player player, int centerX, int centerZ, int radius) {
    Set<Long> set = revealed.computeIfAbsent(
        player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        int cx = centerX + dx, cz = centerZ + dz;
        if (set.add(chunkKey(cx, cz))) sendUnderworldBlocks(player, cx, cz);
      }
    }
  }

  public void revealUpperBandForJoin(Player player, int centerX, int centerZ) {
    if (upperY <= floorY) return;
    UUID id = player.getUniqueId();
    Set<Long> current = revealedUpper.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
    int radius = upperRevealRadius;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        int cx = centerX + dx, cz = centerZ + dz;
        if (current.add(chunkKey(cx, cz))) sendUpperBand(player, cx, cz, false);
      }
    }
  }

  private Set<Long> floodFillCave(World world, int sx, int sy, int sz) {
    Set<Long> chunks = new HashSet<>();
    int minY = Math.max(worldMinY, world.getMinHeight());
    int maxY = floorY - 1;
    if (sy < minY) sy = minY;
    if (sy > maxY) sy = maxY;

    Set<Long> visited = new HashSet<>();
    Deque<int[]> queue = new ArrayDeque<>();
    queue.add(new int[]{sx, sy, sz});
    visited.add(voxelKey(sx, sy, sz));
    int budget = floodBudget;
    int rsq = floodRadiusSq;

    while (!queue.isEmpty() && budget-- > 0) {
      int[] cur = queue.poll();
      int x = cur[0], y = cur[1], z = cur[2];
      chunks.add(chunkKey(x >> 4, z >> 4));
      for (int n = 0; n < 6; n++) {
        int nx = x + NX[n], ny = y + NY[n], nz = z + NZ[n];
        if (ny < minY || ny > maxY) continue;
        int dxr = nx - sx, dzr = nz - sz;
        if (dxr * dxr + dzr * dzr > rsq) continue;
        if (!visited.add(voxelKey(nx, ny, nz))) continue;
        if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
        if (isPassable(world.getBlockAt(nx, ny, nz))) queue.add(new int[]{nx, ny, nz});
      }
    }
    if (verbose && budget <= 0) {
      LogData.get().info("[hider] flood ran out of budget at " + chunks.size() + " chunks");
    }
    return chunks;
  }

  private static long voxelKey(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
  }

  private boolean raycastTo(Player player, int chunkX, int chunkZ, double targetY, boolean verboseLos) {
    Location eye = player.getEyeLocation();
    World world = eye.getWorld();
    if (world == null) return false;

    int pcx = eye.getBlockX() >> 4, pcz = eye.getBlockZ() >> 4;
    if (chunkX == pcx && chunkZ == pcz) return true;

    double ox = eye.getX(), oy = eye.getY(), oz = eye.getZ();
    double tx = (chunkX << 4) + 8.5, tz = (chunkZ << 4) + 8.5;
    double rdx = tx - ox, rdy = targetY - oy, rdz = tz - oz;
    double distSq = rdx * rdx + rdy * rdy + rdz * rdz;
    if (distSq < 0.0001) return true;

    int bx = (int) Math.floor(ox), by = (int) Math.floor(oy), bz = (int) Math.floor(oz);
    int stepX = rdx > 0 ? 1 : (rdx < 0 ? -1 : 0);
    int stepY = rdy > 0 ? 1 : (rdy < 0 ? -1 : 0);
    int stepZ = rdz > 0 ? 1 : (rdz < 0 ? -1 : 0);

    double tDeltaX = stepX != 0 ? 1.0 / Math.abs(rdx) : Double.MAX_VALUE;
    double tDeltaY = stepY != 0 ? 1.0 / Math.abs(rdy) : Double.MAX_VALUE;
    double tDeltaZ = stepZ != 0 ? 1.0 / Math.abs(rdz) : Double.MAX_VALUE;
    double tMaxX = stepX != 0 ? ((stepX > 0 ? Math.floor(ox) + 1.0 : Math.ceil(ox) - 1.0) - ox) / rdx : Double.MAX_VALUE;
    double tMaxY = stepY != 0 ? ((stepY > 0 ? Math.floor(oy) + 1.0 : Math.ceil(oy) - 1.0) - oy) / rdy : Double.MAX_VALUE;
    double tMaxZ = stepZ != 0 ? ((stepZ > 0 ? Math.floor(oz) + 1.0 : Math.ceil(oz) - 1.0) - oz) / rdz : Double.MAX_VALUE;

    int maxSteps = (int) (Math.sqrt(distSq) * 2.0) + 32;
    int floorBound = (int) Math.floor(targetY);
    for (int step = 0; step < maxSteps; step++) {
      if (step > 0) {
        if (stepY < 0 && by <= floorBound) return true;
        if (stepY > 0 && by >= floorBound) return true;
        if (bx >> 4 == chunkX && bz >> 4 == chunkZ) return true;
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return true;
        if (world.getBlockAt(bx, by, bz).getType().isOccluding()) {
          if (verboseLos && verbose) LogData.get().info("[hider-los] " + player.getName()
              + " blocked -> " + chunkX + "," + chunkZ
              + " at " + bx + "," + by + "," + bz + " (step " + step + ")");
          return false;
        }
      }
      if (tMaxX < tMaxY && tMaxX < tMaxZ) { bx += stepX; tMaxX += tDeltaX; }
      else if (tMaxY < tMaxZ) { by += stepY; tMaxY += tDeltaY; }
      else { bz += stepZ; tMaxZ += tDeltaZ; }
    }
    return true;
  }

  private boolean hasLineOfSightToChunk(Player player, int chunkX, int chunkZ) {
    Location eye = player.getEyeLocation();
    return raycastTo(player, chunkX, chunkZ, eye.getY(), false);
  }

  private boolean hasLineOfSightToFloor(Player player, int chunkX, int chunkZ) {
    return raycastTo(player, chunkX, chunkZ, floorY - 0.5, true);
  }

  private boolean isPlayerLookingAtChunk(Player player, int chunkX, int chunkZ) {
    Location eye = player.getEyeLocation();
    int pcx = eye.getBlockX() >> 4, pcz = eye.getBlockZ() >> 4;
    if (chunkX == pcx && chunkZ == pcz) return true;

    org.bukkit.util.Vector dir = eye.getDirection();
    if (dir == null) return false;

    double tx = (chunkX << 4) + 8.5 - eye.getX();
    double ty = 0;
    double tz = (chunkZ << 4) + 8.5 - eye.getZ();
    org.bukkit.util.Vector toChunk = new org.bukkit.util.Vector(tx, ty, tz).normalize();

    double dot = dir.getX() * toChunk.getX() + dir.getY() * toChunk.getY() + dir.getZ() * toChunk.getZ();
    return dot > 0.707;
  }

  private static boolean isPassable(Block block) {
    Material m = block.getType();
    return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR
        || !m.isOccluding();
  }

  private void addSquare(Set<Long> set, int cx, int cz, int radius) {
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++)
        set.add(chunkKey(cx + dx, cz + dz));
  }

  public void invalidateRecompute(UUID id) {
    if (id != null) lastRecomputeStamp.remove(id);
  }

  public void invalidateFloodCache(UUID id) {
    if (id != null) { lastFloodTick.remove(id); cachedFlood.remove(id); }
  }

  public void clearRevealedForPlayer(UUID id) {
    if (id != null) {
      revealed.remove(id);
      revealedUpper.remove(id);
      revealedGeodes.remove(id);
    }
  }

  public void clearHiddenEntities(Player player) {
    if (player == null) return;
    Set<UUID> hidden = hiddenEntities.remove(player.getUniqueId());
    if (hidden == null || hidden.isEmpty()) return;
    for (UUID eid : hidden) {
      org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(eid);
      if (e != null) player.showEntity(plugin, e);
    }
  }

  public void removePlayer(Player player) {
    UUID id = player.getUniqueId();
    revealed.remove(id);
    revealedUpper.remove(id);
    revealedGeodes.remove(id);
    hiddenEntities.remove(id);
    bypassed.remove(id);
    lastFloodTick.remove(id);
    cachedFlood.remove(id);
    lastRecomputeStamp.remove(id);
    playerSalt.remove(id);
    deliveredChunks.remove(id);
    surfacedSinceTick.remove(id);
    lastEntityScanTick.remove(id);
    playerWorldUids.remove(id);
    playerEnvironments.remove(id);

  }

  public void markChunkDelivered(UUID id, int chunkX, int chunkZ) {
    if (id == null) return;
    deliveredChunks.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet())
        .add(chunkKey(chunkX, chunkZ));

    Set<Long> revealedSet  = revealed.get(id);
    boolean alreadyRevealed = revealedSet != null && revealedSet.contains(chunkKey(chunkX, chunkZ));
    Set<Long> upperSet     = revealedUpper.get(id);
    boolean alreadyUpper    = upperSet != null && upperSet.contains(chunkKey(chunkX, chunkZ));

    if (alreadyRevealed || alreadyUpper) {
      final int fcx = chunkX, fcz = chunkZ;

      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) return;
        if (alreadyRevealed) sendUnderworldBlocks(player, fcx, fcz);
        if (alreadyUpper)    sendUpperBand(player, fcx, fcz, false);
        recomputeGeodeForPlayer(player);
        if (ghostBlockManager != null) {
          ghostBlockManager.resendForChunk(id, player, fcx, fcz);
        }
      }, 1L);
    }
  }

  public void markChunkUnloaded(UUID id, int chunkX, int chunkZ) {
    if (id == null) return;
    Set<Long> set = deliveredChunks.get(id);
    if (set != null) set.remove(chunkKey(chunkX, chunkZ));
  }

  public boolean isChunkDeliveredTo(UUID id, int chunkX, int chunkZ) {
    if (id == null) return false;
    Set<Long> set = deliveredChunks.get(id);
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
  }

  public int saltFor(UUID id) {
    if (id == null) return 0;
    Integer s = playerSalt.get(id);
    if (s != null) return s;
    int fresh = ThreadLocalRandom.current().nextInt();
    Integer prior = playerSalt.putIfAbsent(id, fresh);
    return prior == null ? fresh : prior;
  }

  public void rotateSalt(UUID id) {
    if (id == null) return;
    playerSalt.put(id, ThreadLocalRandom.current().nextInt());
    Set<Long> set = revealed.remove(id);
    if (set != null) set.clear();
  }

  public int extraRevealJitter(UUID id) {
    if (id == null) return 0;
    long mix = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    return ((int) (mix ^ (mix >>> 32))) & 1;
  }

  public boolean shouldSuppressEntityFor(Player viewer, double x, double y, double z) {

    if (viewer == null || y >= upperY) return false;
    int cx = ((int) Math.floor(x)) >> 4;
    int cz = ((int) Math.floor(z)) >> 4;
    return !isRevealed(viewer, cx, cz);
  }

  private boolean shouldSkipRecompute(UUID id, int pcx, int pcz, int py) {
    long now = currentTick();
    long[] stamp = lastRecomputeStamp.computeIfAbsent(id, k -> new long[4]);

    if (stamp[0] != 0
        && now - stamp[0] < 5
        && stamp[1] == pcx && stamp[2] == pcz
        && Math.abs(stamp[3] - py) <= 2) return true;
    stamp[0] = now; stamp[1] = pcx; stamp[2] = pcz; stamp[3] = py;
    return false;
  }

  private void enforceRevealedCap(Player player, Set<Long> current, int pcx, int pcz) {
    int over = current.size() - maxRevealedChunks;
    if (over <= 0) return;
    java.util.PriorityQueue<long[]> farthest = new java.util.PriorityQueue<>(
        over + 1, (a, b) -> Integer.compare((int) b[1], (int) a[1]));
    for (long key : current) {
      int dist = Math.max(Math.abs(keyX(key) - pcx), Math.abs(keyZ(key) - pcz));
      farthest.add(new long[]{key, dist});
      if (farthest.size() > over) farthest.poll();
    }
    for (long[] entry : farthest) {
      long key = entry[0];
      current.remove(key);
      hideUnderworldBlocks(player, keyX(key), keyZ(key));
    }
    if (verbose && !farthest.isEmpty()) {
      LogData.get().info("[hider] cap evicted " + farthest.size() + " chunks for " + player.getName());
    }
  }

  private Set<Long> throttledFlood(UUID id, World world, int x, int y, int z) {
    long now = currentTick();
    Long last = lastFloodTick.get(id);
    if (last != null && now - last < floodThrottleTicks) {
      Set<Long> cached = cachedFlood.get(id);
      if (cached != null) return new HashSet<>(cached);
    }
    Set<Long> fresh = floodFillCave(world, x, y, z);
    lastFloodTick.put(id, now);
    cachedFlood.put(id, fresh);
    return fresh;
  }

  private static volatile boolean currentTickSupported = true;

  private static long currentTick() {
    if (currentTickSupported) {
      try { return Bukkit.getCurrentTick(); }
      catch (NoSuchMethodError ignored) { currentTickSupported = false; }
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
    for (Set<Long> set : revealedGeodes.values())  set.remove(ck);
    for (Set<Long> set : deliveredChunks.values()) set.remove(ck);
  }

  public void onChunkUnload(org.bukkit.Chunk chunk) {
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    onChunkUnload(chunkX, chunkZ);
  }

  public UUID getPlayerWorldUid(Player player) {
    return playerWorldUids.computeIfAbsent(player.getUniqueId(), k -> player.getWorld().getUID());
  }

  public World.Environment getPlayerEnvironment(Player player) {
    return playerEnvironments.computeIfAbsent(player.getUniqueId(), k -> player.getWorld().getEnvironment());
  }

  public void setPlayerWorld(UUID playerId, World world) {
    if (world != null) {
      playerWorldUids.put(playerId, world.getUID());
      playerEnvironments.put(playerId, world.getEnvironment());
    } else {
      playerWorldUids.remove(playerId);
      playerEnvironments.remove(playerId);
    }
  }

  public boolean isBypassed(UUID id) {
    return bypassed.contains(id);
  }

  public void clearDeliveredChunks(UUID playerId) {
    deliveredChunks.remove(playerId);
  }

  public void saveGeodeData() {
    try {
      java.io.File folder = new java.io.File(plugin.getDataFolder(), "geode-cache");
      folder.mkdirs();
      java.io.File scanFile = new java.io.File(folder, "scanned.dat");
      java.io.File geodeFile = new java.io.File(folder, "geodes.dat");

      java.util.List<String> scannedLines = new java.util.ArrayList<>();
      for (long ck : scannedGeodes) {
        scannedLines.add(Long.toHexString(ck));
      }
      java.nio.file.Files.write(scanFile.toPath(), scannedLines);

      java.util.List<String> geodeLines = new java.util.ArrayList<>();
      for (var entry : geodeByChunk.entrySet()) {
        long ck = entry.getKey();
        Set<Long> positions = entry.getValue();
        if (positions == null || positions.isEmpty()) continue;
        StringBuilder sb = new StringBuilder();
        sb.append(Long.toHexString(ck));
        for (long pos : positions) {
          sb.append(':').append(Long.toHexString(pos));
        }
        geodeLines.add(sb.toString());
      }
      java.nio.file.Files.write(geodeFile.toPath(), geodeLines);
      LogData.get().info("[geode-cache] saved " + scannedGeodes.size()
          + " scanned chunks, " + geodeByChunk.size() + " with positions");
    } catch (Throwable e) {
      LogData.get().warning("[geode-cache] save failed: " + e);
    }
  }

  public void loadGeodeData() {
    try {
      java.io.File folder = new java.io.File(plugin.getDataFolder(), "geode-cache");
      java.io.File scanFile = new java.io.File(folder, "scanned.dat");
      java.io.File geodeFile = new java.io.File(folder, "geodes.dat");

      if (scanFile.exists()) {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(scanFile.toPath());
        for (String line : lines) {
          line = line.trim();
          if (!line.isEmpty()) {
            scannedGeodes.add(Long.parseUnsignedLong(line, 16));
          }
        }
      }

      if (geodeFile.exists()) {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(geodeFile.toPath());
        for (String line : lines) {
          line = line.trim();
          if (line.isEmpty()) continue;
          String[] parts = line.split(":");
          if (parts.length < 2) continue;
          long ck = Long.parseUnsignedLong(parts[0], 16);
          Set<Long> positions = ConcurrentHashMap.newKeySet();
          for (int i = 1; i < parts.length; i++) {
            positions.add(Long.parseUnsignedLong(parts[i], 16));
          }
          geodeByChunk.put(ck, positions);
          scannedGeodes.add(ck);
          geodeInsertionOrder.add(ck);
        }
      }

      LogData.get().info("[geode-cache] loaded " + scannedGeodes.size()
          + " scanned chunks, " + geodeByChunk.size() + " with positions");
    } catch (Throwable e) {
      LogData.get().warning("[geode-cache] load failed: " + e);
    }
  }
}
