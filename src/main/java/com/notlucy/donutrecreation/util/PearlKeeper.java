package com.notlucy.donutrecreation.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class PearlKeeper implements Listener {

  private static final long DEATH_KEEP_MS = 5L * 60L * 1000L;
  private static final long TELEPORT_CONSUME_TICKS = 10L;

  private final Map<UUID, UUID> pearls = new ConcurrentHashMap<>();
  private final Map<UUID, Long> deadOwners = new ConcurrentHashMap<>();
  private final Map<UUID, Long> teleportedAt = new ConcurrentHashMap<>();

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Listener registered by Bukkit.")
  public PearlKeeper() {
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onLaunch(ProjectileLaunchEvent event) {
    if (event.getEntity() instanceof EnderPearl pearl
        && pearl.getShooter() instanceof Player player) {
      pearls.put(pearl.getUniqueId(), player.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(PlayerDeathEvent event) {
    deadOwners.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerRespawnEvent event) {
    deadOwners.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    UUID id = event.getPlayer().getUniqueId();
    pearls.entrySet().removeIf(e -> e.getValue().equals(id));
    deadOwners.remove(id);
    teleportedAt.remove(id);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onTeleport(PlayerTeleportEvent event) {
    if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
      teleportedAt.put(event.getPlayer().getUniqueId(), (long) Bukkit.getCurrentTick());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onRemove(EntityRemoveEvent event) {
    if (!(event.getEntity() instanceof EnderPearl pearl)) {
      return;
    }
    EntityRemoveEvent.Cause cause = event.getCause();
    if (cause == EntityRemoveEvent.Cause.UNLOAD) {
      return;
    }
    UUID owner = pearls.remove(pearl.getUniqueId());
    if (owner == null) {
      return;
    }
    Long deathAt = deadOwners.get(owner);
    if (deathAt == null || System.currentTimeMillis() - deathAt > DEATH_KEEP_MS) {
      return;
    }
    Long teleported = teleportedAt.get(owner);
    if (teleported != null
        && (long) Bukkit.getCurrentTick() - teleported < TELEPORT_CONSUME_TICKS) {
      return;
    }
    Location loc = pearl.getLocation();
    Vector velocity = pearl.getVelocity();
    if (loc.getWorld() == null) {
      return;
    }
    Player ownerPlayer = Bukkit.getPlayer(owner);
    if (ownerPlayer == null) {
      return;
    }
    EnderPearl replacement = loc.getWorld().spawn(loc, EnderPearl.class, p -> {
      p.setVelocity(velocity);
      p.setShooter(ownerPlayer);
    });
    pearls.put(replacement.getUniqueId(), owner);
  }
}