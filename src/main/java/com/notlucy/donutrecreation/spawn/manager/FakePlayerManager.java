package com.notlucy.donutrecreation.spawn.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.notlucy.donutrecreation.util.LogData;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class FakePlayerManager {

  private static final int VISIBILITY_RADIUS = 100;
  private static final int VISIBILITY_RADIUS_SQ = VISIBILITY_RADIUS * VISIBILITY_RADIUS;
  private static final int TABLIST_RADIUS = 100;
  private static final int TABLIST_RADIUS_SQ = TABLIST_RADIUS * TABLIST_RADIUS;
  private static final int HIDE_ABOVE_Y = 0;

  private static final class Npc {
    final int entityId;
    final UUID uuid;
    final String name;
    final SkinStore.SkinRecord copy;
    final Location location;
    final Pose pose;
    final Map<UUID, Boolean> visibleTo = new ConcurrentHashMap<>();

    Npc(int entityId, UUID uuid, String name, SkinStore.SkinRecord copy,
        Location location, Pose pose) {
      this.entityId = entityId;
      this.uuid = uuid;
      this.name = name;
      this.copy = copy;
      this.location = location.clone();
      this.pose = pose;
    }
  }

  private final Plugin plugin;
  private final SkinStore skins;
  private final ConcurrentMap<Integer, Npc> active = new ConcurrentHashMap<>();
  private final AtomicInteger entityIdCounter = new AtomicInteger(-1);
  private boolean tickTaskStarted;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Plugin shared by Bukkit.")
  public FakePlayerManager(Plugin plugin, SkinStore skins) {
    this.plugin = plugin;
    this.skins = skins;
  }

  public enum Pose {
    STANDING,
    SNEAKING,
    CRAWLING
  }

  public int spawn(Location location, String name, long ttlTicks) {
    return spawn(location, name, ttlTicks, Pose.SNEAKING);
  }

  public int spawn(Location location, String name, long ttlTicks, boolean crawling) {
    return spawn(location, name, ttlTicks, crawling ? Pose.CRAWLING : Pose.STANDING);
  }

  public int spawn(Location location, SkinStore.SkinRecord copy, long ttlTicks, Pose pose) {
    if (copy == null) {
      return -1;
    }
    return spawn(location, copy.name(), copy.uuid(), copy, ttlTicks, pose);
  }

  public int spawn(Location location, String name, long ttlTicks, Pose pose) {
    SkinStore.SkinRecord copy = skins.byName(name);
    UUID uuid = copy != null ? copy.uuid() : UUID.randomUUID();
    return spawn(location, name, uuid, copy, ttlTicks, pose);
  }

  private int spawn(Location location, String name, UUID uuid,
      SkinStore.SkinRecord copy, long ttlTicks, Pose pose) {
    int entityId = entityIdCounter.getAndDecrement();
    try {
      UserProfile profile = resolveProfile(name, uuid, copy);

      WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
          new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
              profile, true, 0, GameMode.SURVIVAL, Component.text(name), null);

      WrapperPlayServerPlayerInfoUpdate updatePacket =
          new WrapperPlayServerPlayerInfoUpdate(
              EnumSet.of(
                  WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                  WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
              List.of(info));

      com.github.retrooper.packetevents.protocol.world.Location packetLocation =
          new com.github.retrooper.packetevents.protocol.world.Location(
              location.getX(), location.getY(), location.getZ(),
              location.getYaw(), location.getPitch());

      WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
          entityId, uuid, EntityTypes.PLAYER, packetLocation,
          location.getYaw(), 0, new Vector3d(0, 0, 0));

      Npc npc = new Npc(entityId, uuid, name, copy, location, pose);
      active.put(entityId, npc);

      for (Player viewer : Bukkit.getOnlinePlayers()) {
        if (!viewer.getWorld().equals(location.getWorld())) continue;
        if (viewer.getLocation().getY() >= HIDE_ABOVE_Y) continue;
        double distSq = viewer.getLocation().distanceSquared(location);
        if (distSq <= VISIBILITY_RADIUS_SQ) {
          try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, updatePacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            sendPosePacket(viewer, npc);
            npc.visibleTo.put(viewer.getUniqueId(), true);
          } catch (Throwable ignored) { }
        } else if (distSq <= TABLIST_RADIUS_SQ) {
          try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, updatePacket);
            npc.visibleTo.put(viewer.getUniqueId(), false);
          } catch (Throwable ignored) { }
        }
      }

      startTickTask();
      Bukkit.getScheduler().runTaskLater(plugin,
          () -> despawn(entityId), Math.max(1L, ttlTicks));
      return entityId;
    } catch (Throwable error) {
      LogData.get().warning("[spawn] failed to spawn fake player '" + name + "': " + error);
      return -1;
    }
  }

  private void sendPosePacket(Player viewer, Npc npc) {
    if (npc.pose == Pose.STANDING) return;
    try {
      List<EntityData<?>> data = new ArrayList<>(2);
      if (npc.pose == Pose.SNEAKING) {
        data.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x02));
        data.add(new EntityData<>(6, EntityDataTypes.ENTITY_POSE, EntityPose.CROUCHING));
      } else if (npc.pose == Pose.CRAWLING) {
        data.add(new EntityData<>(6, EntityDataTypes.ENTITY_POSE, EntityPose.SWIMMING));
      }
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerEntityMetadata(npc.entityId, data));
    } catch (Throwable ignored) { }
  }

  private void startTickTask() {
    if (tickTaskStarted) return;
    tickTaskStarted = true;
    Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      if (active.isEmpty()) return;
      for (Npc npc : List.copyOf(active.values())) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
          if (!viewer.getWorld().equals(npc.location.getWorld())) {
            Boolean wasVisible = npc.visibleTo.get(viewer.getUniqueId());
            if (wasVisible != null && wasVisible) {
              hideFrom(viewer, npc);
            }
            continue;
          }
          double distSq = viewer.getLocation().distanceSquared(npc.location);
          boolean belowFloor = viewer.getLocation().getY() < HIDE_ABOVE_Y;
          boolean inEntityRange = distSq <= VISIBILITY_RADIUS_SQ;
          boolean inTablistRange = distSq <= TABLIST_RADIUS_SQ;
          Boolean wasVisible = npc.visibleTo.get(viewer.getUniqueId());

          if (belowFloor && inEntityRange) {
            if (wasVisible == null || !wasVisible) {
              showTo(viewer, npc, true);
            }
          } else if (belowFloor && inTablistRange && !inEntityRange) {
            if (wasVisible == null || !wasVisible) {
              showTablistOnly(viewer, npc);
            }
          } else {
            if (wasVisible != null && wasVisible) {
              hideFrom(viewer, npc);
            }
          }
        }
      }
    }, 40L, 40L);
  }

  private void showTo(Player viewer, Npc npc, boolean showEntity) {
    try {
      UserProfile profile = resolveProfile(npc.name, npc.uuid, npc.copy);
      WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
          new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
              profile, true, 0, GameMode.SURVIVAL, Component.text(npc.name), null);
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerPlayerInfoUpdate(
              EnumSet.of(
                  WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                  WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
              List.of(info)));
      if (showEntity) {
        com.github.retrooper.packetevents.protocol.world.Location pl =
            new com.github.retrooper.packetevents.protocol.world.Location(
                npc.location.getX(), npc.location.getY(), npc.location.getZ(),
                npc.location.getYaw(), npc.location.getPitch());
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
            new WrapperPlayServerSpawnEntity(
                npc.entityId, npc.uuid, EntityTypes.PLAYER, pl,
                npc.location.getYaw(), 0, new Vector3d(0, 0, 0)));
        sendPosePacket(viewer, npc);
      }
      npc.visibleTo.put(viewer.getUniqueId(), true);
    } catch (Throwable ignored) { }
  }

  private void showTablistOnly(Player viewer, Npc npc) {
    try {
      UserProfile profile = resolveProfile(npc.name, npc.uuid, npc.copy);
      WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
          new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
              profile, true, 0, GameMode.SURVIVAL, Component.text(npc.name), null);
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerPlayerInfoUpdate(
              EnumSet.of(
                  WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                  WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
              List.of(info)));
      npc.visibleTo.put(viewer.getUniqueId(), false);
    } catch (Throwable ignored) { }
  }

  private void hideFrom(Player viewer, Npc npc) {
    try {
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerDestroyEntities(npc.entityId));
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npc.uuid)));
      npc.visibleTo.put(viewer.getUniqueId(), false);
    } catch (Throwable ignored) { }
  }

  public void despawn(int entityId) {
    Npc npc = active.remove(entityId);
    if (npc == null) return;
    try {
      WrapperPlayServerDestroyEntities destroyPacket =
          new WrapperPlayServerDestroyEntities(npc.entityId);
      WrapperPlayServerPlayerInfoRemove removePacket =
          new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npc.uuid));
      for (Player viewer : Bukkit.getOnlinePlayers()) {
        Boolean wasVisible = npc.visibleTo.get(viewer.getUniqueId());
        if (wasVisible == null || !wasVisible) continue;
        try {
          PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
          PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removePacket);
        } catch (Throwable ignored) { }
      }
    } catch (Throwable ignored) { }
  }

  public void despawnAll() {
    for (Integer id : new ArrayList<>(active.keySet())) {
      despawn(id);
    }
  }

  private static UserProfile resolveProfile(String name, UUID npcUuid,
      SkinStore.SkinRecord copy) {
    if (copy != null) {
      return new UserProfile(copy.uuid(), copy.name(), List.of(
          new com.github.retrooper.packetevents.protocol.player.TextureProperty(
              "textures", copy.texture(), copy.signature())));
    }
    Player online = Bukkit.getPlayerExact(name);
    if (online != null) {
      SkinStore.SkinRecord live = captureLiveSkin(online);
      if (live != null) {
        return new UserProfile(live.uuid(), live.name(), List.of(
            new com.github.retrooper.packetevents.protocol.player.TextureProperty(
                "textures", live.texture(), live.signature())));
      }
      return new UserProfile(online.getUniqueId(), online.getName());
    }
    return new UserProfile(npcUuid, name);
  }

  private static SkinStore.SkinRecord captureLiveSkin(Player player) {
    try {
      com.destroystokyo.paper.profile.PlayerProfile profile = player.getPlayerProfile();
      for (com.destroystokyo.paper.profile.ProfileProperty property : profile.getProperties()) {
        if ("textures".equals(property.getName()) && property.getValue() != null
            && !property.getValue().isEmpty()) {
          return new SkinStore.SkinRecord(
              player.getUniqueId(), player.getName(),
              property.getValue(), property.getSignature());
        }
      }
      return null;
    } catch (Throwable ignored) {
      return null;
    }
  }
}
