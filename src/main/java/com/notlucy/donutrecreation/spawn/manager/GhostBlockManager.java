package com.notlucy.donutrecreation.spawn.manager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class GhostBlockManager {

  public static final class GhostGroup {
    public final UUID viewerId;
    public final List<Location> locations;
    public final List<GhostBlock> ghostBlocks;
    public final long expiresAtTick;
    public Runnable onRevert;
    public boolean revertOnInteract;

    public GhostGroup(UUID viewerId, List<Location> locations, List<GhostBlock> ghostBlocks,
        long expiresAtTick) {
      this.viewerId = viewerId;
      this.locations = locations;
      this.ghostBlocks = ghostBlocks;
      this.expiresAtTick = expiresAtTick;
    }
  }

  public static final class GhostBlock {
    public final Location location;
    public final BlockData data;

    public GhostBlock(Location location, BlockData data) {
      this.location = location.clone();
      this.data = data;
    }
  }

  private final Plugin plugin;
  private final ConcurrentMap<Long, GhostGroup> groups = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, ConcurrentMap<Long, List<GhostBlock>>> perPlayerChunks = new ConcurrentHashMap<>();
  private final AtomicLong nextId = new AtomicLong();

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Plugin is shared by Bukkit.")
  public GhostBlockManager(Plugin plugin) {
    this.plugin = plugin;
  }

  public boolean hasGhostBlockAt(UUID playerId, int x, int y, int z) {
    ConcurrentMap<Long, List<GhostBlock>> playerChunks = perPlayerChunks.get(playerId);
    if (playerChunks == null) return false;
    long key = chunkKey(x >> 4, z >> 4);
    List<GhostBlock> ghosts = playerChunks.get(key);
    if (ghosts == null) return false;
    for (GhostBlock g : ghosts) {
      if (g.location.getBlockX() == x && g.location.getBlockY() == y && g.location.getBlockZ() == z) {
        return true;
      }
    }
    return false;
  }

  public long broadcast(List<GhostBlock> ghosts, long ttlTicks, int radius, Runnable onRevert) {
    if (ghosts == null || ghosts.isEmpty()) return -1L;
    long firstId = -1L;
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      long id = send(viewer, ghosts, ttlTicks, onRevert);
      if (firstId == -1L) firstId = id;
    }
    return firstId;
  }

  public long send(Player viewer, List<GhostBlock> ghosts, long ttlTicks, Runnable onRevert) {
    if (viewer == null || ghosts == null || ghosts.isEmpty()) {
      return -1L;
    }
    List<Location> sentLocations = new ArrayList<>(ghosts.size());
    Map<Long, Map<Location, BlockData>> batches = new HashMap<>();
    UUID viewerId = viewer.getUniqueId();
    ConcurrentMap<Long, List<GhostBlock>> playerChunks =
        perPlayerChunks.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
    for (GhostBlock ghost : ghosts) {
      try {
        Location loc = ghost.location;
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        long key = chunkKey(cx, cz);
        batches.computeIfAbsent(key, ignored -> new HashMap<>()).put(loc, ghost.data);
        sentLocations.add(ghost.location);
        playerChunks.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
            .add(ghost);
      } catch (Throwable ignored) {
      }
    }
    for (Map<Location, BlockData> batch : batches.values()) {
      try {
        viewer.sendMultiBlockChange(batch);
      } catch (Throwable ignored) {
      }
    }
    long id = nextId.incrementAndGet();
    GhostGroup group = new GhostGroup(viewerId, sentLocations, ghosts,
        Bukkit.getCurrentTick() + ttlTicks);
    group.onRevert = onRevert;
    groups.put(id, group);
    Bukkit.getScheduler().runTaskLater(plugin, () -> revert(id), Math.max(1L, ttlTicks));
    return id;
  }

  public void revert(long groupId) {
    GhostGroup group = groups.remove(groupId);
    if (group == null) {
      return;
    }
    Player viewer = Bukkit.getPlayer(group.viewerId);
    if (viewer != null && viewer.isOnline()) {
      Map<Location, BlockData> batch = new HashMap<>();
      for (Location loc : group.locations) {
        try {
          batch.put(loc, loc.getBlock().getBlockData());
        } catch (Throwable ignored) {
        }
      }
      if (!batch.isEmpty()) {
        try {
          viewer.sendMultiBlockChange(batch);
        } catch (Throwable ignored) {
        }
      }
    }
    removeGroupFromPerPlayerChunks(group);
    if (group.onRevert != null) {
      try {
        group.onRevert.run();
      } catch (Throwable ignored) {
      }
    }
  }

  public void revertAll() {
    for (Long id : new ArrayList<>(groups.keySet())) {
      revert(id);
    }
  }

  public void revertAllFor(UUID viewerId) {
    perPlayerChunks.remove(viewerId);
    for (Long id : new ArrayList<>(groups.keySet())) {
      GhostGroup group = groups.get(id);
      if (group != null && group.viewerId.equals(viewerId)) {
        revert(id);
      }
    }
  }

  public void setRevertOnInteract(long groupId, boolean revertOnInteract) {
    GhostGroup group = groups.get(groupId);
    if (group != null) {
      group.revertOnInteract = revertOnInteract;
    }
  }

  public boolean tryRevertOnInteract(Player player, Location clicked) {
    if (clicked == null) {
      return false;
    }
    for (Long id : new ArrayList<>(groups.keySet())) {
      GhostGroup group = groups.get(id);
      if (group == null || !group.revertOnInteract
          || !group.viewerId.equals(player.getUniqueId())) {
        continue;
      }
      for (Location loc : group.locations) {
        if (loc.equals(clicked)) {
          revert(id);
          return true;
        }
      }
    }
    return false;
  }

  public boolean isGhostBlock(Location loc) {
    if (loc == null) return false;
    for (GhostGroup group : groups.values()) {
      for (Location gl : group.locations) {
        if (gl.equals(loc)) return true;
      }
    }
    return false;
  }

  public boolean isGhostBlockAt(Location loc) {
    if (loc == null) return false;
    for (GhostGroup group : groups.values()) {
      for (Location gl : group.locations) {
        if (gl.getBlockX() == loc.getBlockX()
            && gl.getBlockY() == loc.getBlockY()
            && gl.getBlockZ() == loc.getBlockZ()) {
          return true;
        }
      }
    }
    return false;
  }

  public void resendForChunk(UUID viewerId, Player viewer, int chunkX, int chunkZ) {
    if (viewer == null || !viewer.isOnline()) return;
    ConcurrentMap<Long, List<GhostBlock>> playerChunks = perPlayerChunks.get(viewerId);
    if (playerChunks == null) return;
    long key = chunkKey(chunkX, chunkZ);
    List<GhostBlock> ghosts = playerChunks.get(key);
    if (ghosts == null || ghosts.isEmpty()) return;
    Map<Location, BlockData> batch = new HashMap<>(ghosts.size());
    for (GhostBlock ghost : ghosts) {
      if (ghost.location.getWorld() != null
          && ghost.location.getWorld().equals(viewer.getWorld())) {
        batch.put(ghost.location, ghost.data);
      }
    }
    if (!batch.isEmpty()) {
      try {
        viewer.sendMultiBlockChange(batch);
      } catch (Throwable ignored) {
      }
    }
  }

  public void resendAllForPlayer(UUID viewerId, Player viewer) {
    if (viewer == null || !viewer.isOnline()) return;
    ConcurrentMap<Long, List<GhostBlock>> playerChunks = perPlayerChunks.get(viewerId);
    if (playerChunks == null || playerChunks.isEmpty()) return;
    for (Map.Entry<Long, List<GhostBlock>> entry : playerChunks.entrySet()) {
      List<GhostBlock> ghosts = entry.getValue();
      if (ghosts == null || ghosts.isEmpty()) continue;
      Map<Location, BlockData> batch = new HashMap<>(ghosts.size());
      for (GhostBlock ghost : ghosts) {
        if (ghost.location.getWorld() != null
            && ghost.location.getWorld().equals(viewer.getWorld())) {
          batch.put(ghost.location, ghost.data);
        }
      }
      if (!batch.isEmpty()) {
        try {
          viewer.sendMultiBlockChange(batch);
        } catch (Throwable ignored) {
        }
      }
    }
  }

  private void removeGroupFromPerPlayerChunks(GhostGroup group) {
    ConcurrentMap<Long, List<GhostBlock>> playerChunks = perPlayerChunks.get(group.viewerId);
    if (playerChunks == null) return;
    for (GhostBlock ghost : group.ghostBlocks) {
      long key = chunkKey(ghost.location.getBlockX() >> 4, ghost.location.getBlockZ() >> 4);
      List<GhostBlock> list = playerChunks.get(key);
      if (list != null) {
        list.removeIf(g -> g.location.equals(ghost.location));
        if (list.isEmpty()) {
          playerChunks.remove(key);
        }
      }
    }
    if (playerChunks.isEmpty()) {
      perPlayerChunks.remove(group.viewerId);
    }
  }

  private static long chunkKey(int x, int z) {
    return ((long) x << 32) ^ (z & 0xffffffffL);
  }

  private static int keyX(long key) {
    return (int) (key >> 32);
  }

  private static int keyZ(long key) {
    return (int) key;
  }
}