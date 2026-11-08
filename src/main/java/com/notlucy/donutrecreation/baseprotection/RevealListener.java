/*
 * Copyright (C) 2026 Notlucy and Donut Recreation contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.notlucy.donutrecreation.baseprotection;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.notlucy.donutrecreation.baseprotection.packet.PacketHider;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class RevealListener implements Listener {

  private final RevealManager revealManager;
  private final PacketHider hider;
  private int taskId = -1;

  private int period = 10;
  private int voidTriggerY = -60;
  private long voidCooldownMs = 1000;
  private final java.util.concurrent.ConcurrentMap<java.util.UUID, Long> lastVoidAttempt =
      new java.util.concurrent.ConcurrentHashMap<>();

  public RevealListener(RevealManager revealManager, PacketHider hider) {
    this.revealManager    = revealManager;
    this.hider = hider;
    var cfg = revealManager.plugin().getConfig();
    this.voidTriggerY  = cfg.getInt("hider.void-trigger-y", -64);
    this.voidCooldownMs = Math.max(0, cfg.getLong("hider.void-attempt-cooldown-ms", 1000));
  }

  public void start() {
    this.period = Math.max(1, revealManager.plugin().getConfig().getInt("hider.recompute-period-ticks", 10));
    taskId = Bukkit.getScheduler()
        .runTaskTimer(revealManager.plugin(), this::tick, 1L, (long) period)
        .getTaskId();
  }

  private void tick() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      revealManager.refreshRevealState(player);
    }
  }

  public void cancel() {
    if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    if (block.getType() == org.bukkit.Material.SPAWNER) {
      if (revealManager.plugin().isStaffModeActive(player.getUniqueId())) {
        event.setCancelled(true);
        player.sendMessage(revealManager.plugin().color("&cYou cannot break spawners while in staff mode."));
        return;
      }
    }
    int blockY = block.getY();
    int floor = revealManager.hideBelowY();
    if (blockY <= floor) {
      int bx = block.getX();
      int by = block.getY();
      int bz = block.getZ();
      revealManager.revealAtBlock(player, bx, by, bz);
      Bukkit.getScheduler().runTask(revealManager.plugin(), () -> {
        if (!player.isOnline()) {
          return;
        }
        /* Re-apply after the block is removed so the newly exposed layer and any
         * opened cave render reliably. */
        revealManager.revealAtBlock(player, bx, by, bz);
        revealManager.refreshRevealState(player);
      });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerInteract(PlayerInteractEvent event) {
    Block block = event.getClickedBlock();
    if (block == null || block.getY() != revealManager.hideBelowY() - 1) return;
    Player player = event.getPlayer();
    if (player.getLocation().getBlockY() < revealManager.hideBelowY()) {
      revealManager.revealAround(player, block.getX() >> 4, block.getZ() >> 4, revealManager.initialRadius());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerMove(PlayerMoveEvent event) {
    var from = event.getFrom();
    var destination   = event.getTo();
    if (destination == null) return;

    Player player = event.getPlayer();

    if (destination.getY() < voidTriggerY && from.getY() >= destination.getY()) { handleVoid(player); return; }

    int fromChunkX = from.getBlockX() >> 4, fromChunkZ = from.getBlockZ() >> 4;
    int toChunkX = destination.getBlockX()   >> 4, toChunkZ = destination.getBlockZ()   >> 4;
    boolean crossedChunk = fromChunkX != toChunkX || fromChunkZ != toChunkZ;
    int floorY = revealManager.hideBelowY();
    boolean crossedFloor = (from.getY() <= floorY) != (destination.getY() <= floorY);
    int upperY = revealManager.upperBarrierY();
    boolean crossedUpper   = (from.getY() < upperY) != (destination.getY() < upperY);
    boolean approachingUpper = !crossedUpper && destination.getY() >= upperY
        && destination.getY() <= upperY + 3 && destination.getY() < from.getY();
    boolean approachingFloor = !crossedFloor && destination.getY() >= revealManager.hideBelowY()
        && destination.getY() <= revealManager.hideBelowY() + 3 && destination.getY() < from.getY();
    int scrambleReleaseY = revealManager.scrambleUntilAboveY();
    int scrambleArmY = revealManager.scrambleBelowY();
    boolean crossedScramble = ((from.getY() <= scrambleReleaseY)
        != (destination.getY() <= scrambleReleaseY))
        || ((from.getY() <= scrambleArmY) != (destination.getY() <= scrambleArmY));

    boolean inProtectedZone = destination.getY() < revealManager.upperBarrierY() + 2;
    boolean movedBlock = from.getBlockX() != destination.getBlockX()
        || from.getBlockY() != destination.getBlockY()
        || from.getBlockZ() != destination.getBlockZ();

    if (!crossedChunk && !crossedFloor && !crossedUpper && !approachingUpper
        && !approachingFloor && !crossedScramble && !(inProtectedZone && movedBlock)) {
      revealManager.refreshEntityVisibility(player);
      return;
    }
    if (crossedFloor || crossedUpper || approachingUpper || approachingFloor || crossedScramble
        || (inProtectedZone && movedBlock)) {
      revealManager.invalidateRevealThrottle(player.getUniqueId());
      if (crossedFloor) {
        revealManager.showHiddenEntities(player);
      }
    }
    revealManager.refreshRevealState(player);
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  private void handleVoid(Player player) {
    long now = System.currentTimeMillis();
    Long last = lastVoidAttempt.get(player.getUniqueId());
    if (last != null && now - last < voidCooldownMs) return;
    lastVoidAttempt.put(player.getUniqueId(), now);

    int playerY = player.getLocation().getBlockY();
    revealManager.plugin().susFlagManager().flag(player, "Under Bedrock (y=" + playerY + ")", "underbedrock");
    var flag = revealManager.plugin().susFlagManager().getFlag(player.getUniqueId());
    int count = flag != null ? flag.count() : 1;
    String msg = revealManager.plugin().color("&c[acsus] &f" + player.getName()
        + " &7Under Bedrock (y=" + playerY + ") &c(" + count + " flags)");
    Bukkit.getOnlinePlayers().stream()
        .filter(staff -> staff.hasPermission("donutrecreation.*"))
        .forEach(op -> op.sendMessage(msg));
    revealManager.plugin().getLogger().info("[acsus] flagged " + player.getName()
        + ": Under Bedrock (y=" + playerY + ") (" + count + " flags)");

    var cfg = revealManager.plugin().getConfig();
    String redirect = cfg.getString("hider.void-redirect-server", "");
    if (redirect != null && !redirect.isEmpty()) { sendRedirect(player, redirect); return; }
    String worldName = cfg.getString("hider.void-spawn-world", "world");
    World world = worldName == null ? null : Bukkit.getWorld(worldName);
    if (world == null) world = player.getWorld();
    double x   = cfg.getDouble("hider.void-spawn-x", world.getSpawnLocation().getX());
    double y   = cfg.getDouble("hider.void-spawn-y", world.getSpawnLocation().getY());
    double z   = cfg.getDouble("hider.void-spawn-z", world.getSpawnLocation().getZ());
    float  yaw   = (float) cfg.getDouble("hider.void-spawn-yaw", 0);
    float  pitch = (float) cfg.getDouble("hider.void-spawn-pitch", 0);
    if (player.teleport(new Location(world, x, y, z, yaw, pitch))) {
      return;
    }
    Location loc = player.getLocation();
    int surfaceY = loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ(),
        HeightMap.MOTION_BLOCKING);
    player.teleport(new Location(loc.getWorld(), loc.getX(), surfaceY + 1.0, loc.getZ(),
        loc.getYaw(), loc.getPitch()));
  }

  private void sendRedirect(Player player, String server) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeUTF("Connect");
      dos.writeUTF(server);
      player.sendPluginMessage(revealManager.plugin(), "BungeeCord", baos.toByteArray());
    } catch (IOException e) {
      com.notlucy.donutrecreation.util.LogData.get().warning(
          "[void] failed destination send bungee redirect: " + e);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    revealManager.trackPlayerWorld(player.getUniqueId(), player.getWorld());
    Location loc  = player.getLocation();
    int playerY  = loc.getBlockY();
    int playerChunkX = loc.getBlockX() >> 4;
    int playerChunkZ = loc.getBlockZ() >> 4;

    if (playerY < revealManager.hideBelowY()) {
      Bukkit.getScheduler().runTaskLater(revealManager.plugin(), () -> {
        if (!player.isOnline()) return;
        revealManager.revealAround(player, playerChunkX, playerChunkZ, revealManager.initialRadius());
        revealManager.revealBarrierBandOnJoin(player, playerChunkX, playerChunkZ);
      }, 1L);
    } else {
      Bukkit.getScheduler().runTaskLater(revealManager.plugin(), () -> {
        if (!player.isOnline()) return;
        revealManager.revealBarrierBandOnJoin(player, playerChunkX, playerChunkZ);
      }, 1L);
    }

    int delay = playerY < revealManager.hideBelowY() ? 20 : 5;
    Bukkit.getScheduler().runTaskLater(revealManager.plugin(), () -> {
      if (!player.isOnline()) return;
      revealManager.invalidateRevealThrottle(player.getUniqueId());
      revealManager.invalidateCaveFloodCache(player.getUniqueId());
      revealManager.refreshRevealState(player);
    }, delay);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerTeleport(PlayerTeleportEvent event) {
    Player player = event.getPlayer();
    if (event.getTo() != null) {
      revealManager.trackPlayerWorld(player.getUniqueId(), event.getTo().getWorld());
    }
    revealManager.invalidateRevealThrottle(player.getUniqueId());
    revealManager.invalidateCaveFloodCache(player.getUniqueId());

    var from = event.getFrom();
    var destination   = event.getTo();
    if (destination != null && from.getWorld() != null && destination.getWorld() != null
        && !from.getWorld().getUID().equals(destination.getWorld().getUID())) {
      revealManager.clearDeliveredChunkCache(player.getUniqueId());
      revealManager.clearRevealedChunks(player.getUniqueId());
      if (hider != null) {
        hider.clearPlayer(player.getUniqueId());
      }
    }

    Bukkit.getScheduler().runTaskLater(revealManager.plugin(), () -> revealManager.refreshRevealState(player), 2L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onGameModeChange(PlayerGameModeChangeEvent event) {
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    if (event.getRespawnLocation() != null) {
      revealManager.trackPlayerWorld(player.getUniqueId(), event.getRespawnLocation().getWorld());
    }
    Bukkit.getScheduler().runTaskLater(revealManager.plugin(), () -> {
      revealManager.invalidateCaveFloodCache(player.getUniqueId());
      revealManager.invalidateRevealThrottle(player.getUniqueId());
      revealManager.refreshRevealState(player);
    }, 5L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    revealManager.removePlayer(event.getPlayer());
    lastVoidAttempt.remove(event.getPlayer().getUniqueId());
    if (hider != null) hider.clearPlayer(event.getPlayer().getUniqueId());
  }
}
