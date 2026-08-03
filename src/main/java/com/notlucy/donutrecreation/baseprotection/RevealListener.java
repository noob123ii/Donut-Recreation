package com.notlucy.donutrecreation.baseprotection;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

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

import com.notlucy.donutrecreation.baseprotection.packet.PacketHider;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class RevealListener implements Listener {

  private final RevealManager rm;
  private final PacketHider hider;
  private int taskId = -1;

  private int period = 10;

  public RevealListener(RevealManager rm, PacketHider hider) {
    this.rm    = rm;
    this.hider = hider;
  }

  public void start() {
    this.period = Math.max(1, rm.plugin().getConfig().getInt("hider.recompute-period-ticks", 10));
    taskId = Bukkit.getScheduler()
        .runTaskTimer(rm.plugin(), this::tick, 1L, (long) period)
        .getTaskId();
  }

  private void tick() {
    for (Player p : Bukkit.getOnlinePlayers()) {
      rm.recomputeForPlayer(p);
    }
  }

  public void cancel() {
    if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Block  block  = event.getBlock();
    Player player = event.getPlayer();
    int by    = block.getY();
    int floor = rm.hideBelowY();
    if (by <= floor) {
      rm.revealAndSend(player, block.getX() >> 4, block.getZ() >> 4, rm.initialRadius());
      Bukkit.getScheduler().runTask(rm.plugin(), () -> rm.recomputeForPlayer(player));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerInteract(PlayerInteractEvent event) {
    Block block = event.getClickedBlock();
    if (block == null || block.getY() != rm.hideBelowY() - 1) return;
    Player player = event.getPlayer();
    if (player.getLocation().getBlockY() < rm.hideBelowY()) {
      rm.revealAndSend(player, block.getX() >> 4, block.getZ() >> 4, rm.initialRadius());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerMove(PlayerMoveEvent event) {
    var from = event.getFrom();
    var to   = event.getTo();
    if (to == null) return;

    Player player = event.getPlayer();

    if (to.getY() <= -65) { handleVoid(player); return; }

    int fcx = from.getBlockX() >> 4, fcz = from.getBlockZ() >> 4;
    int tcx = to.getBlockX()   >> 4, tcz = to.getBlockZ()   >> 4;
    boolean crossedChunk = fcx != tcx || fcz != tcz;
    boolean crossedFloor = (from.getY() < rm.hideBelowY()) != (to.getY() < rm.hideBelowY());
    int upperY = rm.upperBarrierY();
    boolean crossedUpper   = (from.getY() < upperY) != (to.getY() < upperY);
    boolean approachingUpper = !crossedUpper && to.getY() >= upperY
        && to.getY() <= upperY + 3 && to.getY() < from.getY();
    boolean approachingFloor = !crossedFloor && to.getY() >= rm.hideBelowY()
        && to.getY() <= rm.hideBelowY() + 3 && to.getY() < from.getY();

    if (!crossedChunk && !crossedFloor && !crossedUpper && !approachingUpper && !approachingFloor) {
      rm.updateEntityVisibility(player);
      return;
    }
    if (crossedFloor || crossedUpper || approachingUpper || approachingFloor) {
      rm.invalidateRecompute(player.getUniqueId());
      if (crossedFloor) {
        rm.clearHiddenEntities(player);
      }
    }
    rm.recomputeForPlayer(player);
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  private void handleVoid(Player player) {
    var cfg = rm.plugin().getConfig();
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
    player.teleport(new Location(world, x, y, z, yaw, pitch));
  }

  private void sendRedirect(Player player, String server) {
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
    rm.setPlayerWorld(player.getUniqueId(), player.getWorld());
    Location loc  = player.getLocation();
    int py  = loc.getBlockY();
    int pcx = loc.getBlockX() >> 4;
    int pcz = loc.getBlockZ() >> 4;

    if (py < rm.hideBelowY()) {
      Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> {
        if (!player.isOnline()) return;
        rm.revealAndSend(player, pcx, pcz, rm.initialRadius());
        rm.revealUpperBandForJoin(player, pcx, pcz);
      }, 1L);
    } else {
      Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> {
        if (!player.isOnline()) return;
        rm.revealUpperBandForJoin(player, pcx, pcz);
      }, 1L);
    }

    int delay = py < rm.hideBelowY() ? 20 : 5;
    Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> {
      if (!player.isOnline()) return;
      rm.invalidateRecompute(player.getUniqueId());
      rm.invalidateFloodCache(player.getUniqueId());
      rm.recomputeForPlayer(player);
    }, delay);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerTeleport(PlayerTeleportEvent event) {
    Player player = event.getPlayer();
    if (event.getTo() != null) {
      rm.setPlayerWorld(player.getUniqueId(), event.getTo().getWorld());
    }
    rm.invalidateRecompute(player.getUniqueId());
    rm.invalidateFloodCache(player.getUniqueId());

    var from = event.getFrom();
    var to   = event.getTo();
    if (to != null && from.getWorld() != null && to.getWorld() != null
        && !from.getWorld().getUID().equals(to.getWorld().getUID())) {
      rm.onPlayerChangeWorld(from.getWorld().getUID());
      rm.clearDeliveredChunks(player.getUniqueId());
    }

    Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> rm.recomputeForPlayer(player), 2L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onGameModeChange(PlayerGameModeChangeEvent event) {
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    if (event.getRespawnLocation() != null) {
      rm.setPlayerWorld(player.getUniqueId(), event.getRespawnLocation().getWorld());
    }
    Bukkit.getScheduler().runTaskLater(rm.plugin(), () -> {
      rm.invalidateFloodCache(player.getUniqueId());
      rm.invalidateRecompute(player.getUniqueId());
      rm.recomputeForPlayer(player);
    }, 5L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    rm.removePlayer(event.getPlayer());
    if (hider != null) hider.clearPlayer(event.getPlayer().getUniqueId());
  }
}