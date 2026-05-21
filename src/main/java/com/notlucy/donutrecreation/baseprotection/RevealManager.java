package com.notlucy.donutrecreation.baseprotection;

import com.notlucy.donutrecreation.DonutRecreation;
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
  private final ConcurrentMap<UUID, Set<UUID>> hiddenEntities = new ConcurrentHashMap<>();
  private final Set<UUID> bypassed = ConcurrentHashMap.newKeySet();

  private final ConcurrentMap<Long, Set<Long>> geodeByChunk = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> revealedGeodes = new ConcurrentHashMap<>();
  private final Set<Long> scannedGeodes = ConcurrentHashMap.newKeySet();
  private final ConcurrentLinkedDeque<Long> geodeInsertionOrder = new ConcurrentLinkedDeque<>();

  private final ConcurrentMap<UUID, Long> lastFloodTick = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Set<Long>> cachedFlood = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, long[]> lastRecomputeStamp = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Integer> playerSalt = new ConcurrentHashMap<>();

  private final int floorY;
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
  private final BlockData[] fakeFloorPalette;

  public RevealManager(DonutRecreation plugin) {
    this.plugin = plugin;
    var cfg = plugin.getConfig();
    this.floorY = cfg.getInt("hider.hide-below-y", 0);
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

    this.geodeOn = cfg.getBoolean("hider.geode-hide-enabled", true);
    this.geodeRadius = cfg.getInt("hider.geode-reveal-radius", 8);
    this.geodeRadiusSq = (long) geodeRadius * geodeRadius;
    this.fakeAmethyst = Material.STONE.createBlockData();
    this.verbose = cfg.getBoolean("hider.verbose-logging", false);
    this.floodThrottleTicks = cfg.getInt("hider.flood-fill-throttle-ticks", 10);
    this.entityScanChunkRadius = cfg.getInt("hider.entity-scan-chunk-radius", 8);

    plugin.getLogger().info("[hider] up; floor=" + floorY
        + " r=" + initialRadius + "/" + movementRadius
        + " sticky=" + stickyRadius
        + " flood=" + floodBudget + "/" + floodRadius
        + " geode=" + geodeOn + "/" + geodeRadius
        + (verbose ? " verbose" : ""));
  }

  public int hideBelowY() {
    return floorY;
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
    // If the player is themselves below the hide floor, always treat their own chunk
    // and the immediate 3x3 ring around them as revealed. They've already entered the
    // protected zone, so refusing to load the chunk they're standing in only produces
    // the visual "blocks won't render" / "deepslate at feet" artifact when they cross
    // chunk boundaries faster than the recompute scheduler can run (e.g. elytra).
    var loc = player.getLocation();
    if (loc.getY() < floorY) {
      int pcx = loc.getBlockX() >> 4;
      int pcz = loc.getBlockZ() >> 4;
      if (Math.abs(chunkX - pcx) <= 1 && Math.abs(chunkZ - pcz) <= 1) {
        return true;
      }
    }
    Set<Long> set = revealed.get(player.getUniqueId());
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
      recomputeGeodeForPlayer(player);
      return;
    }

    int jitter = extraRevealJitter(id);
    Set<Long> desired = new HashSet<>();
    if (loc.getBlockY() <= floorY) {
      addSquare(desired, pcx, pcz, initialRadius + extraRevealRadius + jitter);
      Set<Long> caves = throttledFlood(id, player.getWorld(),
          loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
      int edge = 1 + extraRevealRadius + jitter;
      for (long key : caves) {
        int cx = keyX(key);
        int cz = keyZ(key);
        for (int dx = -edge; dx <= edge; dx++) {
          for (int dz = -edge; dz <= edge; dz++) {
            desired.add(chunkKey(cx + dx, cz + dz));
          }
        }
      }
    }

    Set<Long> current = revealed.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());

    Set<Long> toReveal = new HashSet<>(desired);
    toReveal.removeAll(current);
    Set<Long> toHide = new HashSet<>(current);
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

    if (!toReveal.isEmpty() || !toHide.isEmpty()) {
      current.addAll(toReveal);
      current.removeAll(toHide);
      for (long k : toReveal) {
        sendUnderworldBlocks(player, keyX(k), keyZ(k));
      }
      for (long k : toHide) {
        hideUnderworldBlocks(player, keyX(k), keyZ(k));
      }
      if (verbose) {
        plugin.getLogger().info("[hider] " + player.getName()
            + " +" + toReveal.size() + " -" + toHide.size());
      }
    }

    enforceRevealedCap(current, pcx, pcz);

    recomputeGeodeForPlayer(player);
    updateEntityVisibility(player);
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
    current.addAll(toReveal);
    current.removeAll(toHide);

    for (long ck : toReveal) {
      sendRealAmethyst(player, ck);
    }
    for (long ck : toHide) {
      sendFakeAmethyst(player, ck);
    }
    if (verbose && !toReveal.isEmpty()) {
      plugin.getLogger().info("[geode] " + player.getName()
          + " +" + toReveal.size() + " -" + toHide.size());
    }
  }

  private void sendRealAmethyst(Player player, long chunkKey) {
    Set<Long> positions = geodeByChunk.get(chunkKey);
    if (positions == null || positions.isEmpty()) {
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
    World world = player.getWorld();
    Map<Location, BlockData> changes = new HashMap<>(positions.size());
    BlockData fake = fakeAmethyst;
    for (long packed : positions) {
      changes.put(new Location(world, unpackX(packed), unpackY(packed), unpackZ(packed)), fake);
    }
    player.sendMultiBlockChange(changes);
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

      boolean shouldHide = above
          ? below
          : below && !isRevealed(player, l.getBlockX() >> 4, l.getBlockZ() >> 4);

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
    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
    int minY = Math.max(worldMinY, world.getMinHeight());
    int maxY = floorY;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;

    final int batchSize = 4096;
    Map<Location, BlockData> batch = new HashMap<>(batchSize);
    int n = 0;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = minY; y < maxY; y++) {
          batch.put(new Location(world, baseX + x, y, baseZ + z),
              chunk.getBlock(x, y, z).getBlockData());
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
      plugin.getLogger().info("[hider] flood ran out of budget at " + chunks.size() + " chunks");
    }
    return chunks;
  }

  private static long voxelKey(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38)
        | ((long) (z & 0x3FFFFFF) << 12)
        | (y & 0xFFF);
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

  public void removePlayer(Player player) {
    UUID id = player.getUniqueId();
    revealed.remove(id);
    revealedGeodes.remove(id);
    hiddenEntities.remove(id);
    bypassed.remove(id);
    lastFloodTick.remove(id);
    cachedFlood.remove(id);
    lastRecomputeStamp.remove(id);
    playerSalt.remove(id);
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
    long[] stamp = lastRecomputeStamp.get(id);
    if (stamp != null
        && now - stamp[0] < recomputeMinTicks
        && stamp[1] == pcx && stamp[2] == pcz
        && Math.abs(stamp[3] - py) <= 1) {
      return true;
    }
    long[] fresh = stamp == null ? new long[4] : stamp;
    fresh[0] = now;
    fresh[1] = pcx;
    fresh[2] = pcz;
    fresh[3] = py;
    lastRecomputeStamp.put(id, fresh);
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
        return cached;
      }
    }
    Set<Long> fresh = floodFillCave(world, x, y, z);
    lastFloodTick.put(id, now);
    cachedFlood.put(id, fresh);
    return fresh;
  }

  private static long currentTick() {
    try {
      return Bukkit.getCurrentTick();
    } catch (NoSuchMethodError ignored) {
      return System.nanoTime() / 50_000_000L;
    }
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
  }
}
