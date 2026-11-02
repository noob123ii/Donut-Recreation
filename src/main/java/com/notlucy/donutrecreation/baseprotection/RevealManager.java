package com.notlucy.donutrecreation.baseprotection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
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
  private final ConcurrentMap<UUID, Set<Long>> revealedChunks       = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> revealedBarrierChunks  = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<UUID>> hiddenEntityIds = new ConcurrentHashMap<>();

  private final ConcurrentMap<UUID, Long> lastEntityScanTick = new ConcurrentHashMap<>();

  private final ConcurrentMap<Long, Set<Long>>     geodePositionsByChunk      = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>>     revealedGeodeChunks    = new ConcurrentHashMap<>();
  private final Set<Long>                          scannedGeodeChunks     = ConcurrentHashMap.newKeySet();

  private final ConcurrentMap<UUID, UUID> playerWorldUids = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, World.Environment> playerEnvironments = new ConcurrentHashMap<>();
  private final ConcurrentLinkedDeque<Long>        geodeInsertionOrder = new ConcurrentLinkedDeque<>();

  private final ConcurrentMap<UUID, Set<Long>> deliveredChunkKeys = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, ConcurrentMap<Long, LongOpenHashSet>> visibleCaveBlocks =
      new ConcurrentHashMap<>();

  private final ConcurrentMap<UUID, Long> surfacedSinceTick = new ConcurrentHashMap<>();

  private com.notlucy.donutrecreation.spawn.manager.GhostBlockManager ghostBlockManager;

  private final ConcurrentMap<UUID, Long>     lastCaveFloodTick       = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Long>     lastCaveFloodOrigin     = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> cachedCaveFlood        = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, long[]>   lastRevealRefresh  = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Integer>  playerSalts          = new ConcurrentHashMap<>();

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
  private final BlockData  aboveFloorAmethystMask;
  private final boolean    verbose;
  private final int        floodThrottleTicks;
  private final int        floodReuseTicks;
  private final int        entityScanChunkRadius;
  private final int        extraRevealRadius;
  private final int        recomputeMinTicks;
  private final int        maxRevealedChunks;
  private final int        maxGeodeChunks;
  private final int        maxRevealPerRecompute;
  private final int        maxHidePerRecompute;
  private final int        caveVisibleDistance;
  private final int        caveFillBudget;
  private final int        chunkRevealRadius;
  private final BlockData[] maskPalette;
  private final int        spawnerMaskAboveY;
  private final int        spawnerRevealDistance;
  private final long       spawnerRevealDistanceSq;
  private final ConcurrentMap<Long, Set<Long>> spawnerPositionsByChunk;
  private final ConcurrentMap<UUID, Set<Long>> revealedSpawnerPositions;

  public RevealManager(DonutRecreation plugin) {
    this.plugin = plugin;
    var cfg = plugin.getConfig();
    this.floorY            = cfg.getInt("hider.hide-below-y", 0);
    this.upperY            = Math.max(this.floorY, cfg.getInt("hider.barrier-upper-y", 12));
    this.upperRevealRadius = Math.max(1, cfg.getInt("hider.upper-reveal-radius", 1));
    this.worldMinY         = cfg.getInt("hider.world-min-y", -64);
    this.initialRadius     = cfg.getInt("hider.reveal-initial-radius", 3);
    this.movementRadius    = cfg.getInt("hider.reveal-movement-radius", 2);

    this.floodBudget = cfg.getInt("hider.flood-fill-budget",
        cfg.getInt("hider.cave-flood-budget", 1_000_000));
    this.floodRadius  = cfg.getInt("hider.flood-fill-block-radius",
        cfg.getInt("hider.cave-flood-block-radius", 256));
    this.floodRadiusSq = floodRadius * floodRadius;

    this.stickyRadius = cfg.getInt("hider.sticky-radius", 10);
    this.maskPalette = new BlockData[]{
        Material.DEEPSLATE.createBlockData()
    };

    this.extraRevealRadius        = Math.max(0, cfg.getInt("hider.reveal-edge-extra-radius", 1));
    this.recomputeMinTicks        = Math.max(0, cfg.getInt("hider.recompute-min-ticks", 2));
    this.maxRevealedChunks        = Math.max(64, cfg.getInt("hider.max-revealed-chunks-per-player", 4096));
    this.maxGeodeChunks           = Math.max(256, cfg.getInt("hider.max-geode-chunks", 16384));
    this.maxRevealPerRecompute    = Math.max(1, cfg.getInt("hider.max-reveal-per-recompute", 64));
    this.maxHidePerRecompute      = Math.max(1, cfg.getInt("hider.max-hide-per-recompute", 16));
    this.caveVisibleDistance      = Math.max(16, cfg.getInt("hider.cave-visible-distance", 48));
    this.caveFillBudget           = Math.max(4096, cfg.getInt("hider.cave-fill-budget", 80000));
    this.chunkRevealRadius        = Math.max(0, Math.min(4, cfg.getInt("hider.chunk-reveal-radius", 1)));

    this.spawnerMaskAboveY = cfg.getInt("hider.spawner-mask-above-y", 120);
    this.spawnerRevealDistance = Math.max(1, cfg.getInt("hider.spawner-reveal-distance", 20));
    this.spawnerRevealDistanceSq = (long) this.spawnerRevealDistance * this.spawnerRevealDistance;
    this.spawnerPositionsByChunk = new ConcurrentHashMap<>();
    this.revealedSpawnerPositions = new ConcurrentHashMap<>();

    this.plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
      @org.bukkit.event.EventHandler
      public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        indexSpawners(event.getChunk());
      }
    }, plugin);

    this.geodeOn      = cfg.getBoolean("hider.geode-hide-enabled", true);
    this.geodeRadius  = cfg.getInt("hider.geode-reveal-radius", 8);
    this.geodeRadiusSq = (long) geodeRadius * geodeRadius;
    this.fakeGeodeAmethystPerChunk = Math.max(0,
        cfg.getInt("hider.fake-geode-amethyst-per-chunk", 24));
    this.aboveFloorAmethystMask = Material.DEEPSLATE.createBlockData();
    this.verbose      = cfg.getBoolean("hider.verbose-logging", false);
    this.floodThrottleTicks   = cfg.getInt("hider.flood-fill-throttle-ticks", 10);
    this.floodReuseTicks      = Math.max(20, cfg.getInt("hider.flood-fill-reuse-ticks", 80));
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
  public int spawnerMaskAboveY() { return spawnerMaskAboveY; }
  public long spawnerRevealDistanceSq() { return spawnerRevealDistanceSq; }
  public int spawnerRevealDistance() { return spawnerRevealDistance; }
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

  public boolean hasAnyGhostBlocks(java.util.UUID playerId) {
    return ghostBlockManager != null && ghostBlockManager.hasGhostBlocksFor(playerId);
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
    long blockX = (x + (1 << 23)) & 0xFFFFFFL;
    long blockZ = (z + (1 << 23)) & 0xFFFFFFL;
    long blockY = (y + (1 << 11)) & 0xFFFL;
    return (blockX << 36) | (blockZ << 12) | blockY;
  }
  public static int unpackX(long p) { return (int) ((p >>> 36) & 0xFFFFFFL) - (1 << 23); }
  public static int unpackZ(long p) { return (int) ((p >>> 12) & 0xFFFFFFL) - (1 << 23); }
  public static int unpackY(long p) { return (int) (p & 0xFFFL) - (1 << 11); }

  public boolean isChunkRevealed(Player player, int chunkX, int chunkZ) {
    Set<Long> set = revealedChunks.get(player.getUniqueId());
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
  }

  public boolean isCaveBlockVisible(Player player, int x, int y, int z) {
    if (y >= upperY) return false;
    ConcurrentMap<Long, LongOpenHashSet> byChunk = visibleCaveBlocks.get(player.getUniqueId());
    if (byChunk == null) return false;
    LongOpenHashSet blocks = byChunk.get(chunkKey(x >> 4, z >> 4));
    return blocks != null && blocks.contains(packPos(x, y, z));
  }

  public LongOpenHashSet visibleCaveSet(Player player, int chunkX, int chunkZ) {
    ConcurrentMap<Long, LongOpenHashSet> byChunk = visibleCaveBlocks.get(player.getUniqueId());
    return byChunk == null ? null : byChunk.get(chunkKey(chunkX, chunkZ));
  }

  public boolean isCaveChunkVisible(Player player, int chunkX, int chunkZ) {
    ConcurrentMap<Long, LongOpenHashSet> byChunk = visibleCaveBlocks.get(player.getUniqueId());
    if (byChunk == null) return false;
    LongOpenHashSet blocks = byChunk.get(chunkKey(chunkX, chunkZ));
    return blocks != null && !blocks.isEmpty();
  }

  public boolean isBarrierBandRevealed(Player player, int chunkX, int chunkZ) {
    if (upperY <= floorY) return true;
    if (isChunkRevealed(player, chunkX, chunkZ)) return true;
    Set<Long> set = revealedBarrierChunks.get(player.getUniqueId());
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
  }

  /**
   * Returns whether a block position is currently visible to the viewer under
   * the lower and upper mask rules.
   */
  public boolean isPositionVisible(Player player, int x, int y, int z) {
    if (player == null) return false;
    if (y >= upperY) return true;
    if (isCaveBlockVisible(player, x, y, z)) return true;
    if (y < floorY) {
      return isChunkRevealed(player, x >> 4, z >> 4);
    }
    return isBarrierBandRevealed(player, x >> 4, z >> 4);
  }

  /** Returns whether any part of a chunk column is currently visible. */
  public boolean isChunkVisible(Player player, int chunkX, int chunkZ) {
    return isCaveChunkVisible(player, chunkX, chunkZ)
        || (upperY > floorY && isBarrierBandRevealed(player, chunkX, chunkZ));
  }

  public void recordGeodePositions(long chunkKey, Set<Long> positions) {
    scannedGeodeChunks.add(chunkKey);
    if (positions == null || positions.isEmpty()) {
      geodePositionsByChunk.remove(chunkKey);
      return;
    }
    Set<Long> backing = ConcurrentHashMap.newKeySet(positions.size());
    backing.addAll(positions);
    Set<Long> prior = geodePositionsByChunk.put(chunkKey, backing);
    if (prior == null) {
      geodeInsertionOrder.add(chunkKey);
      evictGeodeOverflow();
    }
  }

  private void evictGeodeOverflow() {
    while (geodePositionsByChunk.size() > maxGeodeChunks) {
      Long oldest = geodeInsertionOrder.pollFirst();
      if (oldest == null) return;
      geodePositionsByChunk.remove(oldest);
      scannedGeodeChunks.remove(oldest);
    }
  }

  public boolean wasGeodeScanned(long chunkKey) { return scannedGeodeChunks.contains(chunkKey); }
  public Set<Long> geodePositions(long chunkKey) { return geodePositionsByChunk.get(chunkKey); }

  public void rememberAmethystBlock(int x, int y, int z) {
    long chunkKeyValue = chunkKey(x >> 4, z >> 4);
    geodePositionsByChunk.computeIfAbsent(chunkKeyValue, ignoredKey -> ConcurrentHashMap.newKeySet()).add(packPos(x, y, z));
    scannedGeodeChunks.add(chunkKeyValue);
  }

  public void forgetAmethystBlock(int x, int y, int z) {
    Set<Long> set = geodePositionsByChunk.get(chunkKey(x >> 4, z >> 4));
    if (set != null) set.remove(packPos(x, y, z));
  }

  public boolean isGeodeVisibleTo(Player player, int chunkX, int chunkZ) {
    return !geodeOn;
  }

  public void forceGeodeVisible(Player player, int chunkX, int chunkZ) {
    long key = chunkKey(chunkX, chunkZ);
    Set<Long> set = revealedGeodeChunks.computeIfAbsent(player.getUniqueId(), ignoredKey -> ConcurrentHashMap.newKeySet());
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

  public void refreshRevealState(Player player) {
    if (!player.isOnline()) return;
    UUID playerId  = player.getUniqueId();
    Location loc = player.getLocation();
    int playerChunkX = loc.getBlockX() >> 4;
    int playerChunkZ = loc.getBlockZ() >> 4;
    int playerY  = loc.getBlockY();

    if (isRevealThrottled(playerId, playerChunkX, playerChunkZ, playerY)) {
      refreshGeodeVisibility(player);
      refreshSpawnerVisibility(player);
      refreshEntityVisibility(player);
      return;
    }

    revealCurrentChunk(player, playerChunkX, playerChunkZ, playerY);
    refreshVisibleCave(player);
    refreshBarrierBand(player, playerId, playerChunkX, playerChunkZ, playerY);
    refreshGeodeVisibility(player);
    refreshSpawnerVisibility(player);
    refreshEntityVisibility(player);
  }

  private void revealCurrentChunk(Player player, int chunkX, int chunkZ, int playerY) {
    if (playerY > floorY) {
      return;
    }
    revealChunkArea(player, chunkX, chunkZ);
  }

  private void revealMiningArea(Player player) {
    Location location = player.getLocation();
    if (location.getBlockY() > floorY + 1) {
      return;
    }
    revealChunkArea(player, location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  private void revealChunkArea(Player player, int chunkX, int chunkZ) {
    Set<Long> chunks = revealedChunks.computeIfAbsent(
        player.getUniqueId(), ignoredKey -> ConcurrentHashMap.newKeySet());
    for (int offsetX = -chunkRevealRadius; offsetX <= chunkRevealRadius; offsetX++) {
      for (int offsetZ = -chunkRevealRadius; offsetZ <= chunkRevealRadius; offsetZ++) {
        int targetX = chunkX + offsetX;
        int targetZ = chunkZ + offsetZ;
        long key = chunkKey(targetX, targetZ);
        if (chunks.add(key)) {
          revealUndergroundChunks(player, targetX, targetZ);
          notifyChunkRevealed(player, targetX, targetZ);
        }
      }
    }
  }

  public void resetTransientReveals(Player player) {
    if (player == null) return;
    resetTransientReveals(player.getUniqueId());
  }

  public void resetTransientReveals(UUID playerId) {
    if (playerId == null) return;
    revealedChunks.remove(playerId);
    revealedBarrierChunks.remove(playerId);
    revealedGeodeChunks.remove(playerId);
    visibleCaveBlocks.remove(playerId);
    deliveredChunkKeys.remove(playerId);
  }

  public void refreshVisibleCave(Player player) {
    if (!player.isOnline()) return;
    Location location = player.getLocation();
    if (location.getBlockY() >= upperY) return;
    World world = location.getWorld();
    if (world == null) return;

    ConcurrentMap<Long, LongOpenHashSet> byChunk =
        visibleCaveBlocks.computeIfAbsent(player.getUniqueId(),
            ignoredKey -> new ConcurrentHashMap<>());
    Map<Long, LongOpenHashSet> additions = new HashMap<>();

    int startX = location.getBlockX();
    int startY = Math.min(location.getBlockY(), upperY - 1);
    int startZ = location.getBlockZ();
    int minY = Math.max(worldMinY, world.getMinHeight());
    if (startY < minY) return;

    LongOpenHashSet visited = new LongOpenHashSet(Math.max(64, caveFillBudget));
    LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    long startKey = packPos(startX, startY, startZ);
    queue.enqueue(startKey);
    visited.add(startKey);
    int budget = caveFillBudget;
    int radiusSq = caveVisibleDistance * caveVisibleDistance;

    while (!queue.isEmpty() && budget-- > 0) {
      long packed = queue.dequeueLong();
      int x = unpackX(packed);
      int y = unpackY(packed);
      int z = unpackZ(packed);
      addVisibleCaveBlock(additions, x, y, z);

      for (int direction = 0; direction < 6; direction++) {
        int neighborX = x + NX[direction];
        int neighborY = y + NY[direction];
        int neighborZ = z + NZ[direction];
        if (neighborY >= upperY || neighborY < minY) continue;
        int deltaX = neighborX - startX;
        int deltaY = neighborY - startY;
        int deltaZ = neighborZ - startZ;
        if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > radiusSq) continue;
        long neighborKey = packPos(neighborX, neighborY, neighborZ);
        if (!visited.add(neighborKey)) continue;
        if (!world.isChunkLoaded(neighborX >> 4, neighborZ >> 4)) continue;
        Material type = world.getType(neighborX, neighborY, neighborZ);
        if (isPassableBlock(type)) {
          queue.enqueue(neighborKey);
        } else {
          addVisibleCaveBlock(additions, neighborX, neighborY, neighborZ);
        }
      }
    }

    mergeVisibleCave(player, byChunk, additions);
  }

  private static void addVisibleCaveBlock(
      Map<Long, LongOpenHashSet> additions, int x, int y, int z) {
    additions.computeIfAbsent(chunkKey(x >> 4, z >> 4), ignoredKey -> new LongOpenHashSet())
        .add(packPos(x, y, z));
  }

  private void mergeVisibleCave(Player player,
      ConcurrentMap<Long, LongOpenHashSet> byChunk,
      Map<Long, LongOpenHashSet> additions) {
    for (Map.Entry<Long, LongOpenHashSet> entry : additions.entrySet()) {
      long chunkKeyValue = entry.getKey();
      LongOpenHashSet newBlocks = entry.getValue();
      LongOpenHashSet oldBlocks = byChunk.get(chunkKeyValue);
      if (oldBlocks != null && oldBlocks.containsAll(newBlocks)) {
        continue;
      }
      LongOpenHashSet merged = oldBlocks == null
          ? new LongOpenHashSet(newBlocks)
          : new LongOpenHashSet(oldBlocks);
      merged.addAll(newBlocks);
      byChunk.put(chunkKeyValue, merged);
      if (wasChunkDeliveredTo(player.getUniqueId(),
          keyX(chunkKeyValue), keyZ(chunkKeyValue))) {
        sendVisibleCaveBlocks(player, chunkKeyValue, oldBlocks, newBlocks);
      }
    }
  }

  private void sendVisibleCaveBlocks(Player player, long chunkKeyValue,
      LongOpenHashSet oldBlocks, LongOpenHashSet newBlocks) {
    World world = player.getWorld();
    int chunkX = keyX(chunkKeyValue);
    int chunkZ = keyZ(chunkKeyValue);
    if (!world.isChunkLoaded(chunkX, chunkZ)) return;
    ChunkSnapshot snapshot = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot();
    Map<Location, BlockData> batch = new HashMap<>(4096);

    for (long packed : newBlocks) {
      if (oldBlocks != null && oldBlocks.contains(packed)) {
        continue;
      }
      int x = unpackX(packed);
      int y = unpackY(packed);
      int z = unpackZ(packed);
      BlockData data = snapshot.getBlockData(x & 15, y, z & 15);
      if (isGeodeMaterial(data.getMaterial())) {
        data = maskPalette[0];
      }
      batch.put(new Location(world, x, y, z), data);
      if (batch.size() >= 4096) {
        player.sendMultiBlockChange(batch);
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      player.sendMultiBlockChange(batch);
    }
  }


  private void refreshBarrierBand(Player player, UUID playerId, int playerChunkX, int playerChunkZ, int playerY) {
    if (upperY <= floorY) return;

    Set<Long> barrierChunkSet = revealedBarrierChunks.computeIfAbsent(playerId, ignoredKey -> ConcurrentHashMap.newKeySet());
    boolean nearBand = playerY < upperY + 2;
    if (!nearBand) {
      return;
    }

    Set<Long> desiredUpper = new HashSet<>();
    addChunkSquare(desiredUpper, playerChunkX, playerChunkZ, upperRevealRadius);
    Set<Long> revealedKeys = revealedChunks.get(playerId);
    if (revealedKeys != null && !revealedKeys.isEmpty()) {
      desiredUpper.addAll(revealedKeys);
    }

    Set<Long> chunksToReveal = new HashSet<>(desiredUpper);
    chunksToReveal.removeAll(barrierChunkSet);

    if (chunksToReveal.isEmpty()) return;

    for (long chunkKeyValue : chunksToReveal) {
      sendBarrierBand(player, keyX(chunkKeyValue), keyZ(chunkKeyValue), false);
      barrierChunkSet.add(chunkKeyValue);
      notifyChunkRevealed(player, keyX(chunkKeyValue), keyZ(chunkKeyValue));
    }
  }

  void sendBarrierBand(Player player, int chunkX, int chunkZ, boolean mask) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) return;
    if (!wasChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) return;
    int fromY = floorY, toY = upperY;
    if (fromY >= toY) return;

    ChunkSnapshot snapshot = mask ? null : world.getChunkAt(chunkX, chunkZ).getChunkSnapshot();
    int baseX    = chunkX << 4;
    int baseZ    = chunkZ << 4;
    BlockData maskBlock = maskPalette[0];

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      int worldX = baseX + x;
      for (int z = 0; z < 16; z++) {
        int worldZ = baseZ + z;
        for (int y = fromY; y < toY; y++) {
          BlockData data;
          if (mask) {
            data = maskBlock;
          } else {
            data = snapshot.getBlockData(x, y, z);
            if (isGeodeMaterial(data.getMaterial())) {
              data = maskBlock;
            }
            if (data.equals(maskBlock)) {
              continue;
            }
          }
          batch.put(new Location(world, worldX, y, worldZ), data);
          if (++n >= batchSize) { player.sendMultiBlockChange(batch); batch.clear(); n = 0; }
        }
      }
    }
    if (!batch.isEmpty()) player.sendMultiBlockChange(batch);
  }

  public void refreshGeodeVisibility(Player player) {
    if (!geodeOn) return;
    UUID viewerId = player.getUniqueId();
    Location playerLocation = player.getLocation();
    int playerChunkX = playerLocation.getBlockX() >> 4;
    int playerChunkZ = playerLocation.getBlockZ() >> 4;
    int chunkRange = Math.max(1, (geodeRadius + 15) >> 4) + 1;

    Set<Long> desiredChunks = new HashSet<>();
    for (int offsetX = -chunkRange; offsetX <= chunkRange; offsetX++) {
      for (int offsetZ = -chunkRange; offsetZ <= chunkRange; offsetZ++) {
        long chunkKeyValue = chunkKey(playerChunkX + offsetX, playerChunkZ + offsetZ);
        Set<Long> positions = geodePositionsByChunk.get(chunkKeyValue);
        if (positions == null || positions.isEmpty()) continue;
        if (isGeodeVisibleTo(player, playerChunkX + offsetX, playerChunkZ + offsetZ)) desiredChunks.add(chunkKeyValue);
      }
    }

    Set<Long> geodeChunkSet = revealedGeodeChunks.computeIfAbsent(viewerId, ignoredKey -> ConcurrentHashMap.newKeySet());
    Set<Long> chunksToReveal = new HashSet<>(desiredChunks); chunksToReveal.removeAll(geodeChunkSet);
    Set<Long> chunksToHide   = new HashSet<>(geodeChunkSet); chunksToHide.removeAll(desiredChunks);
    if (chunksToReveal.isEmpty() && chunksToHide.isEmpty()) return;

    for (long chunkKeyValue : chunksToReveal) { sendRealAmethyst(player, chunkKeyValue);  geodeChunkSet.add(chunkKeyValue); }
    for (long chunkKeyValue : chunksToHide)   { sendFakeAmethyst(player, chunkKeyValue);  geodeChunkSet.remove(chunkKeyValue); }

    if (verbose && (!chunksToReveal.isEmpty() || !chunksToHide.isEmpty())) {
      LogData.get().info("[geode] " + player.getName()
          + " +" + chunksToReveal.size() + " -" + chunksToHide.size());
    }
  }

  private void indexSpawners(Chunk chunk) {
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    int maxY = Math.min(chunk.getWorld().getMaxHeight() - 1, 4095);
    Set<Long> found = null;
    for (int x = 0; x < 16; x++) {
      int worldX = (chunkX << 4) + x;
      for (int z = 0; z < 16; z++) {
        int worldZ = (chunkZ << 4) + z;
        for (int y = spawnerMaskAboveY + 1; y <= maxY; y++) {
          if (chunk.getBlock(x, y, z).getType() == Material.SPAWNER) {
            if (found == null) found = new HashSet<>();
            found.add(packPos(worldX, y, worldZ));
          }
        }
      }
    }
    if (found != null && !found.isEmpty()) {
      spawnerPositionsByChunk.put(chunkKey(chunkX, chunkZ), found);
    }
  }

  private void refreshSpawnerVisibility(Player player) {
    if (spawnerMaskAboveY >= 4095) return;
    World world = player.getWorld();
    Location playerLocation = player.getLocation();
    int playerBlockX = playerLocation.getBlockX();
    int playerBlockZ = playerLocation.getBlockZ();
    int playerBlockY = playerLocation.getBlockY();
    int chunkRange = (spawnerRevealDistance >> 4) + 2;
    UUID playerId = player.getUniqueId();
    Set<Long> nearbySpawnerSet = revealedSpawnerPositions
        .computeIfAbsent(playerId, ignoredKey -> ConcurrentHashMap.newKeySet());
    Set<Long> visibleSpawners = new HashSet<>();
    Map<Location, BlockData> blockUpdates = new HashMap<>();
    for (int offsetX = -chunkRange; offsetX <= chunkRange; offsetX++) {
      for (int offsetZ = -chunkRange; offsetZ <= chunkRange; offsetZ++) {
        Set<Long> spawnerPositions = spawnerPositionsByChunk.get(
            chunkKey((playerBlockX >> 4) + offsetX, (playerBlockZ >> 4) + offsetZ));
        if (spawnerPositions == null || spawnerPositions.isEmpty()) continue;
        for (long packedPosition : spawnerPositions) {
          int spawnerX = unpackX(packedPosition);
          int spawnerY = unpackY(packedPosition);
          int spawnerZ = unpackZ(packedPosition);
          long deltaX = spawnerX - playerBlockX;
          long deltaY = spawnerY - playerBlockY;
          long deltaZ = spawnerZ - playerBlockZ;
          boolean nearby = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
              <= spawnerRevealDistanceSq;
          if (nearby && world.isChunkLoaded(spawnerX >> 4, spawnerZ >> 4)
              && world.getBlockAt(spawnerX, spawnerY, spawnerZ).getType() == Material.SPAWNER) {
            visibleSpawners.add(packedPosition);
            if (nearbySpawnerSet.add(packedPosition)) {
              blockUpdates.put(new Location(world, spawnerX, spawnerY, spawnerZ),
                  Material.SPAWNER.createBlockData());
            }
          }
        }
      }
    }
    for (long packedPosition : nearbySpawnerSet.toArray(new Long[0])) {
      if (visibleSpawners.contains(packedPosition)) {
        continue;
      }
      nearbySpawnerSet.remove(packedPosition);
      int spawnerX = unpackX(packedPosition);
      int spawnerY = unpackY(packedPosition);
      int spawnerZ = unpackZ(packedPosition);
      if (world.isChunkLoaded(spawnerX >> 4, spawnerZ >> 4)
          && world.getBlockAt(spawnerX, spawnerY, spawnerZ).getType() == Material.SPAWNER) {
        blockUpdates.put(new Location(world, spawnerX, spawnerY, spawnerZ),
            Material.STONE.createBlockData());
      }
    }
    if (!blockUpdates.isEmpty()) {
      try {
        player.sendMultiBlockChange(blockUpdates);
      } catch (Throwable ignored) {
      }
    }
  }

  private void sendRealAmethyst(Player player, long chunkKey) {
    Set<Long> positions = geodePositionsByChunk.get(chunkKey);
    if (positions == null || positions.isEmpty()) return;
    int chunkX = keyX(chunkKey), chunkZ = keyZ(chunkKey);
    if (!wasChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) return;
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
        forgetAmethystBlock(unpackX(p), unpackY(p), unpackZ(p));
      }
    }
  }

  private void sendFakeAmethyst(Player player, long chunkKey) {
    Set<Long> positions = geodePositionsByChunk.get(chunkKey);
    if (positions == null || positions.isEmpty()) return;
    int chunkX = keyX(chunkKey), chunkZ = keyZ(chunkKey);
    if (!wasChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) return;
    World world = player.getWorld();
    Map<Location, BlockData> changes = new HashMap<>(positions.size());
    BlockData belowMask = maskPalette[0];
    BlockData aboveMask = aboveFloorAmethystMask;
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

  public void refreshEntityVisibility(Player player) {
    if (!player.isOnline()) return;
    UUID viewerId = player.getUniqueId();

    long nowTick = serverTick();
    Long lastScan = lastEntityScanTick.get(viewerId);
    if (lastScan != null && nowTick - lastScan < 5) return;
    lastEntityScanTick.put(viewerId, nowTick);
    Set<UUID> hidden = hiddenEntityIds.computeIfAbsent(viewerId, ignoredKey -> ConcurrentHashMap.newKeySet());
    Location playerLoc = player.getLocation();

    int playerChunkX = playerLoc.getBlockX() >> 4;
    int playerChunkZ = playerLoc.getBlockZ() >> 4;
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

      if (Math.abs(ecx - playerChunkX) > radius || Math.abs(ecz - playerChunkZ) > radius) {
        if (hidden.remove(eid)) player.showEntity(plugin, e);
        continue;
      }

      boolean shouldHide = shouldHideEntity(e, eLoc, player, proximitySq);

      if (shouldHide) {
        if (hidden.add(eid)) player.hideEntity(plugin, e);
      } else {
        if (hidden.remove(eid)) player.showEntity(plugin, e);
      }
    }
  }

  private boolean shouldHideEntity(
      Entity e, Location eLoc, Player viewer, double proximitySq) {
    boolean visible = isPositionVisible(viewer,
        eLoc.getBlockX(), eLoc.getBlockY(), eLoc.getBlockZ());
    if (visible) {
      return false;
    }
    if (e instanceof org.bukkit.entity.Item || e instanceof org.bukkit.entity.ItemFrame) {
      return distSq(eLoc, viewer.getLocation()) > proximitySq;
    }
    return true;
  }

  private static double distSq(Location a, Location b) {
    double dx = a.getX() - b.getX();
    double dy = a.getY() - b.getY();
    double dz = a.getZ() - b.getZ();
    return dx * dx + dy * dy + dz * dz;
  }

  public void clearHiddenEntityCache(UUID playerId) {
    hiddenEntityIds.remove(playerId);
  }

  public void revealUndergroundChunks(Player player, int chunkX, int chunkZ) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) return;
    if (!wasChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) return;

    ChunkSnapshot snapshot = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot();
    int minY  = Math.max(worldMinY, world.getMinHeight());
    int maxY  = floorY;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    BlockData maskBlock = maskPalette[0];
    UUID playerId = player.getUniqueId();
    boolean checkGhosts = hasAnyGhostBlocks(playerId);

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      int worldX = baseX + x;
      for (int z = 0; z < 16; z++) {
        int worldZ = baseZ + z;
        for (int y = minY; y < maxY; y++) {
          if (checkGhosts && ghostBlockManager.hasGhostBlockAt(playerId, worldX, y, worldZ)) {
            continue;
          }
          BlockData real = snapshot.getBlockData(x, y, z);
          if (isGeodeMaterial(real.getMaterial())) {
            real = maskBlock;
          }
          if (real.equals(maskBlock)) {
            continue;
          }
          batch.put(new Location(world, worldX, y, worldZ), real);
          if (++n >= batchSize) { player.sendMultiBlockChange(batch); batch.clear(); n = 0; }
        }
      }
    }
    if (!batch.isEmpty()) player.sendMultiBlockChange(batch);
  }

  public void maskUndergroundChunks(Player player, int chunkX, int chunkZ) {
    World world = player.getWorld();
    if (!world.isChunkLoaded(chunkX, chunkZ)) return;
    if (!wasChunkDeliveredTo(player.getUniqueId(), chunkX, chunkZ)) return;

    int minY  = Math.max(worldMinY, world.getMinHeight());
    int maxY  = floorY;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    BlockData maskBlock = maskPalette[0];
    UUID playerId = player.getUniqueId();
    boolean checkGhosts = hasAnyGhostBlocks(playerId);

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      int worldX = baseX + x;
      for (int z = 0; z < 16; z++) {
        int worldZ = baseZ + z;
        for (int y = minY; y < maxY; y++) {
          if (checkGhosts && ghostBlockManager.hasGhostBlockAt(playerId, worldX, y, worldZ)) {
            continue;
          }
          batch.put(new Location(world, worldX, y, worldZ), maskBlock);
          if (++n >= batchSize) { player.sendMultiBlockChange(batch); batch.clear(); n = 0; }
        }
      }
    }
    if (!batch.isEmpty()) player.sendMultiBlockChange(batch);
  }

  public void revealAround(Player player, int centerX, int centerZ, int radius) {
    revealMiningArea(player);
    refreshVisibleCave(player);
  }

  public void revealBarrierBandOnJoin(Player player, int centerX, int centerZ) {
    if (upperY <= floorY) return;
    /* Do not reveal the band when the player joins above it. */
    if (player.getLocation().getBlockY() >= upperY + 2) return;
    UUID playerId = player.getUniqueId();
    Set<Long> barrierChunkSet = revealedBarrierChunks.computeIfAbsent(playerId, ignoredKey -> ConcurrentHashMap.newKeySet());
    int radius = upperRevealRadius;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        int chunkX = centerX + dx, chunkZ = centerZ + dz;
        if (barrierChunkSet.add(chunkKey(chunkX, chunkZ))) sendBarrierBand(player, chunkX, chunkZ, false);
      }
    }
  }

  private Set<Long> floodFillCave(World world, int startX, int startY, int startZ) {
    LongOpenHashSet chunks = new LongOpenHashSet();
    int minY = Math.max(worldMinY, world.getMinHeight());
    int maxY = floorY - 1;
    if (startY < minY) startY = minY;
    if (startY > maxY) startY = maxY;

    LongOpenHashSet visited = new LongOpenHashSet(Math.max(64, floodBudget));
    LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    long startKey = packPos(startX, startY, startZ);
    queue.enqueue(startKey);
    visited.add(startKey);
    int budget = floodBudget;
    int radiusSq = floodRadiusSq;

    while (!queue.isEmpty() && budget-- > 0) {
      long packed = queue.dequeueLong();
      int x = unpackX(packed);
      int y = unpackY(packed);
      int z = unpackZ(packed);
      chunks.add(chunkKey(x >> 4, z >> 4));
      for (int direction = 0; direction < 6; direction++) {
        int neighborX = x + NX[direction];
        int neighborY = y + NY[direction];
        int neighborZ = z + NZ[direction];
        if (neighborY < minY || neighborY > maxY) continue;
        int deltaX = neighborX - startX;
        int deltaZ = neighborZ - startZ;
        if (deltaX * deltaX + deltaZ * deltaZ > radiusSq) continue;
        long neighborKey = packPos(neighborX, neighborY, neighborZ);
        if (!visited.add(neighborKey)) continue;
        if (!world.isChunkLoaded(neighborX >> 4, neighborZ >> 4)) continue;
        if (isPassableBlock(world.getType(neighborX, neighborY, neighborZ))) {
          queue.enqueue(neighborKey);
        }
      }
    }
    if (verbose && budget <= 0) {
      LogData.get().info("[hider] flood ran out of budget at " + chunks.size() + " chunks");
    }
    return chunks;
  }

  private boolean raycastTo(Player player, int chunkX, int chunkZ, double targetY, boolean verboseLos) {
    Location eye = player.getEyeLocation();
    World world = eye.getWorld();
    if (world == null) return false;

    int playerChunkX = eye.getBlockX() >> 4, playerChunkZ = eye.getBlockZ() >> 4;
    if (chunkX == playerChunkX && chunkZ == playerChunkZ) return true;

    double originX = eye.getX(), originY = eye.getY(), originZ = eye.getZ();
    double targetX = (chunkX << 4) + 8.5, targetZ = (chunkZ << 4) + 8.5;
    double rayX = targetX - originX, rayY = targetY - originY, rayZ = targetZ - originZ;
    double distSq = rayX * rayX + rayY * rayY + rayZ * rayZ;
    if (distSq < 0.0001) return true;

    int blockX = (int) Math.floor(originX), blockY = (int) Math.floor(originY), blockZ = (int) Math.floor(originZ);
    int stepX = rayX > 0 ? 1 : (rayX < 0 ? -1 : 0);
    int stepY = rayY > 0 ? 1 : (rayY < 0 ? -1 : 0);
    int stepZ = rayZ > 0 ? 1 : (rayZ < 0 ? -1 : 0);

    double tDeltaX = stepX != 0 ? 1.0 / Math.abs(rayX) : Double.MAX_VALUE;
    double tDeltaY = stepY != 0 ? 1.0 / Math.abs(rayY) : Double.MAX_VALUE;
    double tDeltaZ = stepZ != 0 ? 1.0 / Math.abs(rayZ) : Double.MAX_VALUE;
    double tMaxX = stepX != 0 ? ((stepX > 0 ? Math.floor(originX) + 1.0 : Math.ceil(originX) - 1.0) - originX) / rayX : Double.MAX_VALUE;
    double tMaxY = stepY != 0 ? ((stepY > 0 ? Math.floor(originY) + 1.0 : Math.ceil(originY) - 1.0) - originY) / rayY : Double.MAX_VALUE;
    double tMaxZ = stepZ != 0 ? ((stepZ > 0 ? Math.floor(originZ) + 1.0 : Math.ceil(originZ) - 1.0) - originZ) / rayZ : Double.MAX_VALUE;

    int maxSteps = (int) (Math.sqrt(distSq) * 2.0) + 32;
    int floorBound = (int) Math.floor(targetY);
    for (int step = 0; step < maxSteps; step++) {
      if (step > 0) {
        if (stepY < 0 && blockY <= floorBound) return true;
        if (stepY > 0 && blockY >= floorBound) return true;
        if (blockX >> 4 == chunkX && blockZ >> 4 == chunkZ) return true;
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) return true;
        if (world.getBlockAt(blockX, blockY, blockZ).getType().isOccluding()) {
          if (verboseLos && verbose) LogData.get().info("[hider-los] " + player.getName()
              + " blocked -> " + chunkX + "," + chunkZ
              + " at " + blockX + "," + blockY + "," + blockZ + " (step " + step + ")");
          return false;
        }
      }
      if (tMaxX < tMaxY && tMaxX < tMaxZ) { blockX += stepX; tMaxX += tDeltaX; }
      else if (tMaxY < tMaxZ) { blockY += stepY; tMaxY += tDeltaY; }
      else { blockZ += stepZ; tMaxZ += tDeltaZ; }
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
    int playerChunkX = eye.getBlockX() >> 4, playerChunkZ = eye.getBlockZ() >> 4;
    if (chunkX == playerChunkX && chunkZ == playerChunkZ) return true;

    org.bukkit.util.Vector dir = eye.getDirection();
    if (dir == null) return false;

    double targetX = (chunkX << 4) + 8.5 - eye.getX();
    double ty = 0;
    double targetZ = (chunkZ << 4) + 8.5 - eye.getZ();
    org.bukkit.util.Vector toChunk = new org.bukkit.util.Vector(targetX, ty, targetZ).normalize();

    double dot = dir.getX() * toChunk.getX() + dir.getY() * toChunk.getY() + dir.getZ() * toChunk.getZ();
    return dot > 0.707;
  }

  private static boolean isPassableBlock(Material m) {
    return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR
        || !m.isOccluding();
  }

  private void addChunkSquare(Set<Long> set, int chunkX, int chunkZ, int radius) {
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++)
        set.add(chunkKey(chunkX + dx, chunkZ + dz));
  }

  public void invalidateRevealThrottle(UUID playerId) {
    if (playerId != null) lastRevealRefresh.remove(playerId);
  }

  public void invalidateCaveFloodCache(UUID playerId) {
    if (playerId != null) { lastCaveFloodTick.remove(playerId); cachedCaveFlood.remove(playerId); lastCaveFloodOrigin.remove(playerId); }
  }

  public void clearRevealedChunks(UUID playerId) {
    if (playerId != null) {
      revealedChunks.remove(playerId);
      revealedBarrierChunks.remove(playerId);
      revealedGeodeChunks.remove(playerId);
      revealedSpawnerPositions.remove(playerId);
      visibleCaveBlocks.remove(playerId);
    }
  }

  public void showHiddenEntities(Player player) {
    if (player == null) return;
    Set<UUID> hidden = hiddenEntityIds.remove(player.getUniqueId());
    if (hidden == null || hidden.isEmpty()) return;
    for (UUID eid : hidden) {
      org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(eid);
      if (e != null) player.showEntity(plugin, e);
    }
  }

  public void removePlayer(Player player) {
    UUID playerId = player.getUniqueId();
    revealedChunks.remove(playerId);
    revealedBarrierChunks.remove(playerId);
    revealedGeodeChunks.remove(playerId);
    revealedSpawnerPositions.remove(playerId);
    hiddenEntityIds.remove(playerId);
    lastCaveFloodTick.remove(playerId);
    lastCaveFloodOrigin.remove(playerId);
    cachedCaveFlood.remove(playerId);
    lastRevealRefresh.remove(playerId);
    playerSalts.remove(playerId);
    deliveredChunkKeys.remove(playerId);
    surfacedSinceTick.remove(playerId);
    lastEntityScanTick.remove(playerId);
    playerWorldUids.remove(playerId);
    playerEnvironments.remove(playerId);

  }

  public void recordChunkDelivered(UUID playerId, int chunkX, int chunkZ) {
    if (playerId == null) return;
    deliveredChunkKeys.computeIfAbsent(playerId, ignoredKey -> ConcurrentHashMap.newKeySet())
        .add(chunkKey(chunkX, chunkZ));

    Set<Long> revealedSet  = revealedChunks.get(playerId);
    boolean alreadyRevealed = revealedSet != null && revealedSet.contains(chunkKey(chunkX, chunkZ));
    Set<Long> upperSet     = revealedBarrierChunks.get(playerId);
    boolean alreadyUpper    = upperSet != null && upperSet.contains(chunkKey(chunkX, chunkZ));

    if (alreadyRevealed || alreadyUpper) {
      final int fromChunkX = chunkX, fromChunkZ = chunkZ;

      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        if (alreadyRevealed && player.getLocation().getBlockY() <= floorY) {
          revealUndergroundChunks(player, fromChunkX, fromChunkZ);
        }
        if (alreadyUpper) {
          sendBarrierBand(player, fromChunkX, fromChunkZ, false);
        }
        refreshGeodeVisibility(player);
        if (ghostBlockManager != null) {
          ghostBlockManager.resendForChunk(playerId, player, fromChunkX, fromChunkZ);
        }
      }, 1L);
    }
  }

  public void recordChunkUnloaded(UUID playerId, int chunkX, int chunkZ) {
    if (playerId == null) return;
    long chunkKeyValue = chunkKey(chunkX, chunkZ);
    Set<Long> delivered = deliveredChunkKeys.get(playerId);
    if (delivered != null) {
      delivered.remove(chunkKeyValue);
    }
    Set<Long> revealed = revealedChunks.get(playerId);
    if (revealed != null) {
      revealed.remove(chunkKeyValue);
    }
    Set<Long> barrier = revealedBarrierChunks.get(playerId);
    if (barrier != null) {
      barrier.remove(chunkKeyValue);
    }
  }

  public boolean wasChunkDeliveredTo(UUID playerId, int chunkX, int chunkZ) {
    if (playerId == null) return false;
    Set<Long> set = deliveredChunkKeys.get(playerId);
    return set != null && set.contains(chunkKey(chunkX, chunkZ));
  }

  public int getPlayerSalt(UUID playerId) {
    if (playerId == null) return 0;
    Integer s = playerSalts.get(playerId);
    if (s != null) return s;
    int fresh = ThreadLocalRandom.current().nextInt();
    Integer prior = playerSalts.putIfAbsent(playerId, fresh);
    return prior == null ? fresh : prior;
  }

  public void rotatePlayerSalt(UUID playerId) {
    if (playerId == null) return;
    playerSalts.put(playerId, ThreadLocalRandom.current().nextInt());
  }

  public int extraRevealJitter(UUID playerId) {
    if (playerId == null) return 0;
    long mix = playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits();
    return ((int) (mix ^ (mix >>> 32))) & 1;
  }

  public boolean isPositionHidden(Player viewer, double x, double y, double z) {
    if (viewer == null) return false;
    return !isPositionVisible(viewer,
        (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
  }

  private boolean isRevealThrottled(UUID playerId, int playerChunkX, int playerChunkZ, int playerY) {
    long now = serverTick();
    long[] stamp = lastRevealRefresh.computeIfAbsent(playerId, ignoredKey -> new long[4]);

    if (stamp[0] != 0
        && now - stamp[0] < 5
        && stamp[1] == playerChunkX && stamp[2] == playerChunkZ
        && Math.abs(stamp[3] - playerY) <= 2) return true;
    stamp[0] = now; stamp[1] = playerChunkX; stamp[2] = playerChunkZ; stamp[3] = playerY;
    return false;
  }

  private void evictFarRevealedChunks(Player player, Set<Long> currentlyRevealed, int playerChunkX, int playerChunkZ) {
    int over = currentlyRevealed.size() - maxRevealedChunks;
    if (over <= 0) return;
    java.util.PriorityQueue<long[]> farthest = new java.util.PriorityQueue<>(
        over + 1, (a, b) -> Integer.compare((int) b[1], (int) a[1]));
    for (long key : currentlyRevealed) {
      int dist = Math.max(Math.abs(keyX(key) - playerChunkX), Math.abs(keyZ(key) - playerChunkZ));
      farthest.add(new long[]{key, dist});
      if (farthest.size() > over) farthest.poll();
    }
    for (long[] entry : farthest) {
      long key = entry[0];
      currentlyRevealed.remove(key);
      maskUndergroundChunks(player, keyX(key), keyZ(key));
    }
    if (verbose && !farthest.isEmpty()) {
      LogData.get().info("[hider] cap evicted " + farthest.size() + " chunks for " + player.getName());
    }
  }

  private Set<Long> getCachedCaveFlood(UUID playerId, World world, int x, int y, int z) {
    long now = serverTick();
    long origin = chunkKey(x >> 4, z >> 4);
    Long lastOrigin = lastCaveFloodOrigin.get(playerId);
    if (lastOrigin != null && lastOrigin.longValue() == origin) {
      Long last = lastCaveFloodTick.get(playerId);
      Set<Long> cached = cachedCaveFlood.get(playerId);
      if (last != null && now - last < floodReuseTicks && cached != null) {
        return cached;
      }
    }
    Set<Long> fresh = floodFillCave(world, x, y, z);
    lastCaveFloodTick.put(playerId, now);
    lastCaveFloodOrigin.put(playerId, origin);
    cachedCaveFlood.put(playerId, fresh);
    return fresh;
  }

  private static volatile boolean currentTickSupported = true;

  private static boolean isGeodeMaterial(Material material) {
    return material == Material.AMETHYST_BLOCK
        || material == Material.BUDDING_AMETHYST
        || material == Material.AMETHYST_CLUSTER
        || material == Material.SMALL_AMETHYST_BUD
        || material == Material.MEDIUM_AMETHYST_BUD
        || material == Material.LARGE_AMETHYST_BUD
        || material == Material.CALCITE
        || material == Material.SMOOTH_BASALT;
  }

  private static long serverTick() {
    if (currentTickSupported) {
      try { return Bukkit.getCurrentTick(); }
      catch (NoSuchMethodError ignored) { currentTickSupported = false; }
    }
    return System.nanoTime() / 50_000_000L;
  }

  public void handleChunkUnload(int chunkX, int chunkZ) {
    long chunkKeyValue = chunkKey(chunkX, chunkZ);
    for (Set<Long> set : revealedGeodeChunks.values())  set.remove(chunkKeyValue);
    for (Set<Long> set : deliveredChunkKeys.values()) set.remove(chunkKeyValue);
    for (ConcurrentMap<Long, LongOpenHashSet> byChunk : visibleCaveBlocks.values()) {
      byChunk.remove(chunkKeyValue);
    }
  }

  public void handleChunkUnload(org.bukkit.Chunk chunk) {
    long chunkKeyValue = chunkKey(chunk.getX(), chunk.getZ());
    World world = chunk.getWorld();
    for (Map.Entry<UUID, Set<Long>> entry : deliveredChunkKeys.entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player != null && player.getWorld().equals(world)) {
        entry.getValue().remove(chunkKeyValue);
      }
    }
    for (Map.Entry<UUID, Set<Long>> entry : revealedGeodeChunks.entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player != null && player.getWorld().equals(world)) {
        entry.getValue().remove(chunkKeyValue);
      }
    }
    for (Map.Entry<UUID, Set<Long>> entry : revealedChunks.entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player != null && player.getWorld().equals(world)) {
        entry.getValue().remove(chunkKeyValue);
      }
    }
    for (Map.Entry<UUID, Set<Long>> entry : revealedBarrierChunks.entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player != null && player.getWorld().equals(world)) {
        entry.getValue().remove(chunkKeyValue);
      }
    }
    for (Map.Entry<UUID, ConcurrentMap<Long, LongOpenHashSet>> entry
        : visibleCaveBlocks.entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player != null && player.getWorld().equals(world)) {
        entry.getValue().remove(chunkKeyValue);
      }
    }
  }

  public UUID getPlayerWorldUid(Player player) {
    return playerWorldUids.computeIfAbsent(player.getUniqueId(), ignoredKey -> player.getWorld().getUID());
  }

  public World.Environment getPlayerEnvironment(Player player) {
    return playerEnvironments.computeIfAbsent(player.getUniqueId(), ignoredKey -> player.getWorld().getEnvironment());
  }

  public void trackPlayerWorld(UUID playerId, World world) {
    if (world != null) {
      playerWorldUids.put(playerId, world.getUID());
      playerEnvironments.put(playerId, world.getEnvironment());
    } else {
      playerWorldUids.remove(playerId);
      playerEnvironments.remove(playerId);
    }
  }

  public void clearDeliveredChunkCache(UUID playerId) {
    deliveredChunkKeys.remove(playerId);
  }

  public void saveGeodeData() {
    try {
      java.io.File folder = new java.io.File(plugin.getDataFolder(), "geode-cache");
      folder.mkdirs();
      java.io.File scanFile = new java.io.File(folder, "scanned-v3.dat");
      java.io.File geodeFile = new java.io.File(folder, "geodes-v3.dat");

      java.util.List<String> scannedLines = new java.util.ArrayList<>();
      for (long chunkKeyValue : scannedGeodeChunks) {
        scannedLines.add(Long.toHexString(chunkKeyValue));
      }
      java.nio.file.Files.write(scanFile.toPath(), scannedLines);

      java.util.List<String> geodeLines = new java.util.ArrayList<>();
      for (var entry : geodePositionsByChunk.entrySet()) {
        long chunkKeyValue = entry.getKey();
        Set<Long> positions = entry.getValue();
        if (positions == null || positions.isEmpty()) continue;
        StringBuilder sb = new StringBuilder();
        sb.append(Long.toHexString(chunkKeyValue));
        for (long pos : positions) {
          sb.append(':').append(Long.toHexString(pos));
        }
        geodeLines.add(sb.toString());
      }
      java.nio.file.Files.write(geodeFile.toPath(), geodeLines);
      LogData.get().info("[geode-cache] saved " + scannedGeodeChunks.size()
          + " scanned chunks, " + geodePositionsByChunk.size() + " with positions");
    } catch (Throwable e) {
      LogData.get().warning("[geode-cache] save failed: " + e);
    }
  }

  public void loadGeodeData() {
    try {
      java.io.File folder = new java.io.File(plugin.getDataFolder(), "geode-cache");
      java.io.File scanFile = new java.io.File(folder, "scanned-v3.dat");
      java.io.File geodeFile = new java.io.File(folder, "geodes-v3.dat");

      if (scanFile.exists()) {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(scanFile.toPath());
        for (String line : lines) {
          line = line.trim();
          if (!line.isEmpty()) {
            scannedGeodeChunks.add(Long.parseUnsignedLong(line, 16));
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
          long chunkKeyValue = Long.parseUnsignedLong(parts[0], 16);
          Set<Long> positions = ConcurrentHashMap.newKeySet();
          for (int i = 1; i < parts.length; i++) {
            positions.add(Long.parseUnsignedLong(parts[i], 16));
          }
          geodePositionsByChunk.put(chunkKeyValue, positions);
          scannedGeodeChunks.add(chunkKeyValue);
          geodeInsertionOrder.add(chunkKeyValue);
        }
      }

      LogData.get().info("[geode-cache] loaded " + scannedGeodeChunks.size()
          + " scanned chunks, " + geodePositionsByChunk.size() + " with positions");
    } catch (Throwable e) {
      LogData.get().warning("[geode-cache] load failed: " + e);
    }
  }
}
