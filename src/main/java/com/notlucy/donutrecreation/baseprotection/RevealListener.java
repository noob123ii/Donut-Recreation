package com.notlucy.donutrecreation.baseprotection;

import com.notlucy.donutrecreation.baseprotection.packet.PacketHider;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
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

  public RevealListener(RevealManager rm, PacketHider hider) {
    this.rm = rm;
    this.hider = hider;
  }

  public void start() {
    int period = rm.plugin().getConfig().getInt("hider.recompute-period-ticks", 10);
    taskId = Bukkit.getScheduler().runTaskTimer(rm.plugin(), () -> {
      for (Player p : Bukkit.getOnlinePlayers()) {
        rm.recomputeForPlayer(p);
      }
    }, period, period).getTaskId();
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
    int fcx = from.getBlockX() >> 4;
    int fcz = from.getBlockZ() >> 4;
    int tcx = to.getBlockX() >> 4;
    int tcz = to.getBlockZ() >> 4;
    boolean crossedChunk = fcx != tcx || fcz != tcz;
    boolean crossedFloor = (from.getY() < rm.hideBelowY()) != (to.getY() < rm.hideBelowY());
    if (!crossedChunk && !crossedFloor) {
      return;
    }
    rm.recomputeForPlayer(event.getPlayer());
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
