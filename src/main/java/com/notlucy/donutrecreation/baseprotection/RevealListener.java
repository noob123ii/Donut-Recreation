package com.notlucy.donutrecreation.baseprotection;

import com.notlucy.donutrecreation.baseprotection.packet.PacketHider;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import org.bukkit.Bukkit;
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

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class RevealListener implements Listener {

  private final RevealManager rm;
  private final PacketHider hider;
  private int taskId = -1;

  private final Deque<UUID> recomputeQueue = new ArrayDeque<>();
  private int period = 10;
  private int cycleBatch = 1;

  public RevealListener(RevealManager rm, PacketHider hider) {
    this.rm = rm;
    this.hider = hider;
  }

  public void start() {
    this.period = Math.max(1,
        rm.plugin().getConfig().getInt("hider.recompute-period-ticks", 10));
    // Run every tick but only process a slice of the player base each tick, draining the
    // full online snapshot over `period` ticks. This keeps each player's recompute cadence
    // the same while flattening the CPU spike that previously hit one tick every `period`.
    taskId = Bukkit.getScheduler()
        .runTaskTimer(rm.plugin(), this::tickSlice, 1L, 1L)
        .getTaskId();
  }

  private void tickSlice() {
    if (recomputeQueue.isEmpty()) {
      for (Player p : Bukkit.getOnlinePlayers()) {
        recomputeQueue.add(p.getUniqueId());
      }
      cycleBatch = Math.max(1, (recomputeQueue.size() + period - 1) / period);
    }
    for (int i = 0; i < cycleBatch && !recomputeQueue.isEmpty(); i++) {
      UUID id = recomputeQueue.poll();
      Player p = Bukkit.getPlayer(id);
      if (p != null && p.isOnline()) {
        rm.recomputeForPlayer(p);
      }
    }
  }

  public void cancel() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    int by = block.getY();
    int floor = rm.hideBelowY();

    if (by <= floor - 1) {
      Bukkit.getScheduler().runTask(rm.plugin(), () -> rm.recomputeForPlayer(player));
    }
    if (by == floor - 1) {
      rm.revealAndSend(player, block.getX() >> 4, block.getZ() >> 4, rm.initialRadius());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerInteract(PlayerInteractEvent event) {
    Block block = event.getClickedBlock();
    if (block == null || block.getY() != rm.hideBelowY() - 1) {
      return;
    }
    rm.revealAndSend(event.getPlayer(), block.getX() >> 4, block.getZ() >> 4, rm.initialRadius());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerMove(PlayerMoveEvent event) {
    var from = event.getFrom();
    var to = event.getTo();
    if (to == null) {
      return;
    }
    Player player = event.getPlayer();
    if (to.getY() <= -65) {
      handleVoidFall(player);
      return;
    }
    int fcx = from.getBlockX() >> 4;
    int fcz = from.getBlockZ() >> 4;
    int tcx = to.getBlockX() >> 4;
    int tcz = to.getBlockZ() >> 4;
    boolean crossedChunk = fcx != tcx || fcz != tcz;
    boolean crossedFloor = (from.getY() < rm.hideBelowY()) != (to.getY() < rm.hideBelowY());
    int upperY = rm.upperBarrierY();
    boolean crossedUpper = (from.getY() < upperY) != (to.getY() < upperY);
    if (!crossedChunk && !crossedFloor && !crossedUpper) {
      return;
    }
    if (crossedFloor || crossedUpper) {
      rm.invalidateRecompute(player.getUniqueId());
    }
    rm.recomputeForPlayer(player);
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  private void handleVoidFall(Player player) {
    var cfg = rm.plugin().getConfig();
    String redirect = cfg.getString("hider.void-redirect-server", "");
    if (redirect != null && !redirect.isEmpty()) {
      sendBungeeRedirect(player, redirect);
      return;
    }
    String worldName = cfg.getString("hider.void-spawn-world", "world");
    World world = worldName == null ? null : Bukkit.getWorld(worldName);
    if (world == null) {
      world = player.getWorld();
    }
    double x = cfg.getDouble("hider.void-spawn-x", world.getSpawnLocation().getX());
    double y = cfg.getDouble("hider.void-spawn-y", world.getSpawnLocation().getY());
    double z = cfg.getDouble("hider.void-spawn-z", world.getSpawnLocation().getZ());
    float yaw = (float) cfg.getDouble("hider.void-spawn-yaw", 0);
    float pitch = (float) cfg.getDouble("hider.void-spawn-pitch", 0);
    Location spawn = new Location(world, x, y, z, yaw, pitch);
    player.teleport(spawn);
  }

  private void sendBungeeRedirect(Player player, String server) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeUTF("Connect");
      dos.writeUTF(server);
      player.sendPluginMessage(rm.plugin(), "BungeeCord", baos.toByteArray());
    } catch (IOException e) {
      com.notlucy.donutrecreation.util.LogData.get().warning(
          "[void] failed to send bungee redirect: " + e);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> rm.recomputeForPlayer(player), 5L);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerTeleport(PlayerTeleportEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> rm.recomputeForPlayer(player), 2L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onGameModeChange(PlayerGameModeChangeEvent event) {
    Player player = event.getPlayer();
    // Switching gamemode (e.g. into Creative/Spectator) does not move the player or resend
    // chunks, so force a fresh recompute a couple ticks later once the new mode is applied.
    Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> {
      if (player.isOnline()) {
        rm.invalidateRecompute(player.getUniqueId());
        rm.recomputeForPlayer(player);
      }
    }, 2L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> rm.recomputeForPlayer(player), 5L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    rm.removePlayer(event.getPlayer());
    if (hider != null) {
      hider.clearPlayer(event.getPlayer().getUniqueId());
    }
  }
}
