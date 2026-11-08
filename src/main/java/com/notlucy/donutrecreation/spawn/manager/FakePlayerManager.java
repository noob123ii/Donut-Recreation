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
package com.notlucy.donutrecreation.spawn.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
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
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class FakePlayerManager {

  public interface NpcHideState {
    boolean hideName(UUID viewerUuid, UUID npcUuid);

    boolean hideSkin(UUID viewerUuid, UUID npcUuid);
  }

  /** Profile name used while a name is hidden: matches no team and renders no tag. */
  private static final String HIDDEN_NAME = "";

  private static final int VISIBILITY_RADIUS = 100;
  private static final int VISIBILITY_RADIUS_SQ = VISIBILITY_RADIUS * VISIBILITY_RADIUS;
  private static final int TABLIST_RADIUS = 100;
  private static final int TABLIST_RADIUS_SQ = TABLIST_RADIUS * TABLIST_RADIUS;
  private static final int HIDE_ABOVE_Y = 0;

  private static final class Npc {
    final int entityId;
    final UUID npcUuid;
    final String name;
    final SkinStore.SkinRecord skin;
    final Location location;
    final Pose pose;
    final Component displayName;
    final Map<UUID, Boolean> visibleTo = new ConcurrentHashMap<>();

    Npc(int entityId, UUID npcUuid, String name, SkinStore.SkinRecord skin,
        Location location, Pose pose) {
      this.entityId = entityId;
      this.npcUuid = npcUuid;
      this.name = name;
      this.skin = skin;
      this.location = location.clone();
      this.pose = pose;
      this.displayName = computeDisplayName(name);
    }
  }

  private final Plugin plugin;
  private final SkinStore skinStore;
  private final ConcurrentMap<Integer, Npc> activeNpcs = new ConcurrentHashMap<>();
  private final AtomicInteger entityIdCounter = new AtomicInteger(-1);
  private NpcHideState npcHideState;
  private Consumer<Integer> despawnHook;
  private boolean tickTaskStarted;

  public FakePlayerManager(Plugin plugin, SkinStore skinStore) {
    this(plugin, skinStore, null);
  }

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Plugin shared by Bukkit.")
  public FakePlayerManager(Plugin plugin, SkinStore skinStore, NpcHideState npcHideState) {
    this.plugin = plugin;
    this.skinStore = skinStore;
    this.npcHideState = npcHideState;
  }

  public void setHideState(NpcHideState npcHideState) {
    this.npcHideState = npcHideState;
  }

  public void setDespawnHook(Consumer<Integer> despawnHook) {
    this.despawnHook = despawnHook;
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

  public int spawn(Location location, SkinStore.SkinRecord skin, long ttlTicks, Pose pose) {
    if (skin == null) {
      return -1;
    }
    return spawn(location, skin.name(), skin.uuid(), skin, ttlTicks, pose);
  }

  public int spawn(Location location, String name, long ttlTicks, Pose pose) {
    SkinStore.SkinRecord skin = skinStore.byName(name);
    UUID npcUuid = skin != null ? skin.uuid() : UUID.randomUUID();
    return spawn(location, name, npcUuid, skin, ttlTicks, pose);
  }

  private int spawn(Location location, String name, UUID npcUuid,
      SkinStore.SkinRecord skin, long ttlTicks, Pose pose) {
    int entityId = entityIdCounter.getAndDecrement();
    try {
      com.github.retrooper.packetevents.protocol.world.Location packetLocation =
          new com.github.retrooper.packetevents.protocol.world.Location(
              location.getX(), location.getY(), location.getZ(),
              location.getYaw(), location.getPitch());

      WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
          entityId, npcUuid, EntityTypes.PLAYER, packetLocation,
          location.getYaw(), 0, new Vector3d(0, 0, 0));

      Npc npc = new Npc(entityId, npcUuid, name, skin, location, pose);
      activeNpcs.put(entityId, npc);
      boolean undergroundOnly = pose == Pose.CRAWLING;

      for (Player viewer : Bukkit.getOnlinePlayers()) {
        if (!viewer.getWorld().equals(location.getWorld())) continue;
        if (undergroundOnly && viewer.getLocation().getY() >= HIDE_ABOVE_Y) continue;
        double distSq = viewer.getLocation().distanceSquared(location);
        if (distSq <= VISIBILITY_RADIUS_SQ) {
          try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer, buildInfoUpdate(viewer, npc));
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            sendPosePacket(viewer, npc);
            npc.visibleTo.put(viewer.getUniqueId(), true);
          } catch (Throwable ignored) { }
        } else if (distSq <= TABLIST_RADIUS_SQ) {
          try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer, buildInfoUpdate(viewer, npc));
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
      if (activeNpcs.isEmpty()) return;
      for (Npc npc : List.copyOf(activeNpcs.values())) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
          if (!viewer.getWorld().equals(npc.location.getWorld())) {
            Boolean wasVisible = npc.visibleTo.get(viewer.getUniqueId());
            if (wasVisible != null && wasVisible) {
              hideFrom(viewer, npc);
            }
            continue;
          }
          double distSq = viewer.getLocation().distanceSquared(npc.location);
          boolean undergroundOnly = npc.pose == Pose.CRAWLING;
          boolean belowFloor = !undergroundOnly || viewer.getLocation().getY() < HIDE_ABOVE_Y;
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
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, buildInfoUpdate(viewer, npc));
      if (showEntity) {
        com.github.retrooper.packetevents.protocol.world.Location packetLocation =
            new com.github.retrooper.packetevents.protocol.world.Location(
                npc.location.getX(), npc.location.getY(), npc.location.getZ(),
                npc.location.getYaw(), npc.location.getPitch());
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
            new WrapperPlayServerSpawnEntity(
                npc.entityId, npc.npcUuid, EntityTypes.PLAYER, packetLocation,
                npc.location.getYaw(), 0, new Vector3d(0, 0, 0)));
        sendPosePacket(viewer, npc);
      }
      npc.visibleTo.put(viewer.getUniqueId(), true);
    } catch (Throwable ignored) { }
  }

  private void showTablistOnly(Player viewer, Npc npc) {
    try {
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, buildInfoUpdate(viewer, npc));
      npc.visibleTo.put(viewer.getUniqueId(), false);
    } catch (Throwable ignored) { }
  }

  private void hideFrom(Player viewer, Npc npc) {
    try {
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerDestroyEntities(npc.entityId));
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npc.npcUuid)));
      npc.visibleTo.put(viewer.getUniqueId(), false);
    } catch (Throwable ignored) { }
  }

  public void despawn(int entityId) {
    Npc npc = activeNpcs.remove(entityId);
    if (npc == null) return;
    if (despawnHook != null) {
      try {
        despawnHook.accept(entityId);
      } catch (Throwable ignored) { }
    }
    try {
      WrapperPlayServerDestroyEntities destroyPacket =
          new WrapperPlayServerDestroyEntities(npc.entityId);
      WrapperPlayServerPlayerInfoRemove removePacket =
          new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npc.npcUuid));
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
    for (Integer activeEntityId : new ArrayList<>(activeNpcs.keySet())) {
      despawn(activeEntityId);
    }
  }

  public boolean isActiveNpc(int entityId) {
    return activeNpcs.containsKey(entityId);
  }

  public void respawnFor(Player viewer, int entityId) {
    Npc npc = activeNpcs.get(entityId);
    if (npc == null) return;
    if (!viewer.getWorld().equals(npc.location.getWorld())) return;
    try {
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, buildInfoUpdate(viewer, npc));
      Boolean wasVisible = npc.visibleTo.get(viewer.getUniqueId());
      if (wasVisible == null || !wasVisible) {
        npc.visibleTo.put(viewer.getUniqueId(), false);
        return;
      }
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerDestroyEntities(npc.entityId));
      com.github.retrooper.packetevents.protocol.world.Location packetLocation =
          new com.github.retrooper.packetevents.protocol.world.Location(
              npc.location.getX(), npc.location.getY(), npc.location.getZ(),
              npc.location.getYaw(), npc.location.getPitch());
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
          new WrapperPlayServerSpawnEntity(
              npc.entityId, npc.npcUuid, EntityTypes.PLAYER, packetLocation,
              npc.location.getYaw(), 0, new Vector3d(0, 0, 0)));
      sendPosePacket(viewer, npc);
    } catch (Throwable ignored) { }
  }

  private WrapperPlayServerPlayerInfoUpdate buildInfoUpdate(Player viewer, Npc npc) {
    boolean hideName = npcHideState != null
        && npcHideState.hideName(viewer.getUniqueId(), npc.npcUuid);
    boolean hideSkin = npcHideState != null
        && npcHideState.hideSkin(viewer.getUniqueId(), npc.npcUuid);
    UserProfile profile = resolveProfile(npc.name, npc.npcUuid, npc.skin, hideSkin, hideName);
    EnumSet<Action> actions = EnumSet.of(
        Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_DISPLAY_NAME);
    PlayerInfo info = new PlayerInfo(profile, true, 0, GameMode.SURVIVAL,
        hideName ? npc.displayName : Component.text(npc.name), null);
    return new WrapperPlayServerPlayerInfoUpdate(actions, List.of(info));
  }

  /** The full display name (with role prefix, as shown in the name tag) the npc copies. */
  private static Component computeDisplayName(String name) {
    Player online = Bukkit.getPlayerExact(name);
    if (online == null) {
      return Component.text(name);
    }
    Team team = online.getScoreboard().getEntryTeam(name);
    if (team != null) {
      return LegacyComponentSerializer.legacySection().deserialize(
          team.getPrefix() + team.getColor() + name + team.getSuffix());
    }
    Component listName = online.playerListName();
    if (listName != null) {
      return listName;
    }
    return Component.text(name);
  }

  private static UserProfile resolveProfile(String name, UUID npcUuid,
      SkinStore.SkinRecord skin, boolean hideSkin, boolean hideName) {
    String profileName = hideName ? HIDDEN_NAME : name;
    if (hideSkin) {
      return new UserProfile(npcUuid, profileName);
    }
    TextureProperty texture = resolveTexture(name, npcUuid, skin);
    if (texture != null) {
      return new UserProfile(npcUuid, profileName, List.of(texture));
    }
    return new UserProfile(npcUuid, profileName);
  }

  private static TextureProperty resolveTexture(String name, UUID npcUuid,
      SkinStore.SkinRecord skin) {
    if (skin != null && skin.texture() != null && !skin.texture().isEmpty()) {
      return new TextureProperty("textures", skin.texture(), skin.signature());
    }
    Player online = Bukkit.getPlayerExact(name);
    if (online != null) {
      SkinStore.SkinRecord live = captureLiveSkin(online);
      if (live != null) {
        return new TextureProperty("textures", live.texture(), live.signature());
      }
    }
    return null;
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
