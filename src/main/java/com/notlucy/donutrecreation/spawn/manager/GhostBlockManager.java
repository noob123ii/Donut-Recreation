package com.notlucy.donutrecreation.spawn.manager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Tracks ephemeral, per-viewer ghost blocks and reverts them after a TTL.
 *
 * <p>A ghost block is a client-only visual change pushed via
 * {@link Player#sendBlockChange(Location, BlockData)}. The server-side block is unchanged.
 * On expiry — or on plugin disable / player quit — this manager re-sends the real block
 * data from the world to restore the client's view.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class GhostBlockManager {

  /**
   * One scheduled batch of ghost blocks bound to a single viewer.
   */
  public static final class GhostGroup {
    public final UUID viewerId;
    public final List<Location> locations;
    public final long expiresAtTick;
    public Runnable onRevert;
    public boolean revertOnInteract;

    public GhostGroup(UUID viewerId, List<Location> locations, long expiresAtTick) {
      this.viewerId = viewerId;
      this.locations = locations;
      this.expiresAtTick = expiresAtTick;
    }
  }

  /**
   * A single ghost block at a fixed location with the desired client-side appearance.
   */
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
  private final AtomicLong nextId = new AtomicLong();

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Plugin is shared by Bukkit.")
  public GhostBlockManager(Plugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Sends the supplied ghost blocks to {@code viewer} and schedules them to be reverted
   * after {@code ttlTicks}. Returns an opaque group id which can be passed to
   * {@link #revert(long)} to revert early.
   */
  public long send(Player viewer, List<GhostBlock> ghosts, long ttlTicks, Runnable onRevert) {
    if (viewer == null || ghosts == null || ghosts.isEmpty()) {
      return -1L;
    }
    List<Location> sentLocations = new ArrayList<>(ghosts.size());
    for (GhostBlock ghost : ghosts) {
      try {
        viewer.sendBlockChange(ghost.location, ghost.data);
        sentLocations.add(ghost.location);
      } catch (Throwable ignored) {
      }
    }
    long id = nextId.incrementAndGet();
    GhostGroup group = new GhostGroup(viewer.getUniqueId(), sentLocations,
        Bukkit.getCurrentTick() + ttlTicks);
    group.onRevert = onRevert;
    groups.put(id, group);
    Bukkit.getScheduler().runTaskLater(plugin, () -> revert(id), Math.max(1L, ttlTicks));
    return id;
  }

  /** Reverts a single group early. No-op if already reverted. */
  public void revert(long groupId) {
    GhostGroup group = groups.remove(groupId);
    if (group == null) {
      return;
    }
    Player viewer = Bukkit.getPlayer(group.viewerId);
    if (viewer != null && viewer.isOnline()) {
      for (Location loc : group.locations) {
        try {
          viewer.sendBlockChange(loc, loc.getBlock().getBlockData());
        } catch (Throwable ignored) {
        }
      }
    }
    if (group.onRevert != null) {
      try {
        group.onRevert.run();
      } catch (Throwable ignored) {
      }
    }
  }

  /** Reverts every active group. Used on plugin disable. */
  public void revertAll() {
    for (Long id : new ArrayList<>(groups.keySet())) {
      revert(id);
    }
  }

  /** Reverts every group belonging to the given viewer. Used on quit. */
  public void revertAllFor(UUID viewerId) {
    for (Long id : new ArrayList<>(groups.keySet())) {
      GhostGroup group = groups.get(id);
      if (group != null && group.viewerId.equals(viewerId)) {
        revert(id);
      }
    }
  }

  /** Marks a group so it reverts when its blocks are interacted with. */
  public void setRevertOnInteract(long groupId, boolean revertOnInteract) {
    GhostGroup group = groups.get(groupId);
    if (group != null) {
      group.revertOnInteract = revertOnInteract;
    }
  }

  /**
   * If {@code player} has an active interactable ghost group that contains
   * {@code clicked}, reverts that group and returns true.
   */
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
}
