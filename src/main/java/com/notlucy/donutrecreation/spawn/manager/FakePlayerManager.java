package com.notlucy.donutrecreation.spawn.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Spawns and despawns short-lived fake-player NPCs via PacketEvents.
 *
 * <p>An NPC is broadcast to every online viewer as a real-looking player entity (tablist
 * entry plus entity spawn). It cannot move and is removed after a configurable TTL. All
 * packet-side identifiers are tracked so disable / quit can clean up reliably.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class FakePlayerManager {

  private static final class Npc {
    final int entityId;
    final UUID uuid;

    Npc(int entityId, UUID uuid) {
      this.entityId = entityId;
      this.uuid = uuid;
    }
  }

  private final Plugin plugin;
  private final ConcurrentMap<Integer, Npc> active = new ConcurrentHashMap<>();
  private final AtomicInteger entityIdCounter = new AtomicInteger(-1);

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Plugin shared by Bukkit.")
  public FakePlayerManager(Plugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Spawns a fake player at {@code location} with the given {@code name} and schedules
   * cleanup after {@code ttlTicks}. Returns the entity id on success, or -1 if PacketEvents
   * is unavailable or the wrappers could not be constructed.
   */
  public int spawn(Location location, String name, long ttlTicks) {
    return spawn(location, name, ttlTicks, false);
  }

  /**
   * Spawns a fake player at {@code location} with the given {@code name} and schedules
   * cleanup after {@code ttlTicks}. If {@code crawling} is true the NPC is sent with
   * the SWIMMING pose so it renders as crawling. Returns the entity id on success, or -1
   * on failure.
   */
  public int spawn(Location location, String name, long ttlTicks, boolean crawling) {
    int entityId = entityIdCounter.getAndDecrement();
    UUID uuid = UUID.randomUUID();
    try {
      UserProfile profile = new UserProfile(uuid, name);

      WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
          new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
              profile,
              true,
              0,
              GameMode.SURVIVAL,
              Component.text(name),
              null);

      WrapperPlayServerPlayerInfoUpdate updatePacket =
          new WrapperPlayServerPlayerInfoUpdate(
              EnumSet.of(
                  WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                  WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
              List.of(info));

      com.github.retrooper.packetevents.protocol.world.Location packetLocation =
          new com.github.retrooper.packetevents.protocol.world.Location(
              location.getX(),
              location.getY(),
              location.getZ(),
              location.getYaw(),
              location.getPitch());

      WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
          entityId,
          uuid,
          EntityTypes.PLAYER,
          packetLocation,
          location.getYaw(),
          0,
          new Vector3d(0, 0, 0));

      for (Player viewer : Bukkit.getOnlinePlayers()) {
        try {
          PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, updatePacket);
          PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
        } catch (Throwable ignored) {
        }
      }

      if (crawling) {
        try {
          com.github.retrooper.packetevents.protocol.entity.data.EntityData<Byte> pose =
              new com.github.retrooper.packetevents.protocol.entity.data.EntityData<>(
                  6,
                  com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes.BYTE,
                  (byte) 3);
          WrapperPlayServerEntityMetadata meta =
              new WrapperPlayServerEntityMetadata(entityId, List.of(pose));
          for (Player viewer : Bukkit.getOnlinePlayers()) {
            try {
              PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, meta);
            } catch (Throwable ignored) {
            }
          }
        } catch (Throwable ignored) {
        }
      }

      active.put(entityId, new Npc(entityId, uuid));
      Bukkit.getScheduler().runTaskLater(plugin,
          () -> despawn(entityId), Math.max(1L, ttlTicks));
      return entityId;
    } catch (Throwable error) {
      plugin.getLogger().warning(
          "[spawn] failed to spawn fake player '" + name + "': " + error);
      return -1;
    }
  }

  public void despawn(int entityId) {
    Npc npc = active.remove(entityId);
    if (npc == null) {
      return;
    }
    try {
      WrapperPlayServerDestroyEntities destroyPacket =
          new WrapperPlayServerDestroyEntities(npc.entityId);
      WrapperPlayServerPlayerInfoRemove removePacket =
          new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npc.uuid));
      for (Player viewer : Bukkit.getOnlinePlayers()) {
        try {
          PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
          PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removePacket);
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable ignored) {
    }
  }

  public void despawnAll() {
    for (Integer id : new ArrayList<>(active.keySet())) {
      despawn(id);
    }
  }
}
