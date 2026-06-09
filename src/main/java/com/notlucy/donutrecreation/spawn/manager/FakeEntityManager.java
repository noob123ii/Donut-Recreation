package com.notlucy.donutrecreation.spawn.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.notlucy.donutrecreation.util.LogData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class FakeEntityManager {

  private static final double HANG_OFFSET = 0.46875;

  private final Plugin plugin;
  private final AtomicInteger entityIdCounter = new AtomicInteger(-100000);
  private final ConcurrentMap<UUID, List<Integer>> active = new ConcurrentHashMap<>();

  public FakeEntityManager(Plugin plugin) {
    this.plugin = plugin;
  }

  public void spawnItemFrame(Player viewer, Location blockLoc, String itemStr,
                               int facing, long ttlTicks) {
    spawnHangingEntity(viewer, blockLoc, itemStr, facing,
        EntityTypes.ITEM_FRAME, ttlTicks);
  }

  public void spawnGlowItemFrame(Player viewer, Location blockLoc, String itemStr,
                                   int facing, long ttlTicks) {
    spawnHangingEntity(viewer, blockLoc, itemStr, facing,
        EntityTypes.GLOW_ITEM_FRAME, ttlTicks);
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  private void spawnHangingEntity(Player viewer, Location blockLoc, String itemStr,
                                    int facing,
                                    EntityType type,
                                    long ttlTicks) {
    int entityId = entityIdCounter.getAndDecrement();
    UUID uuid = UUID.randomUUID();

    double cx = blockLoc.getBlockX() + 0.5;
    double cy = blockLoc.getBlockY() + 0.5;
    double cz = blockLoc.getBlockZ() + 0.5;
    float yaw = 0f;
    float pitch = 0f;

    switch (facing) {
      case 0: // south
        yaw = 0f;
        cz += HANG_OFFSET;
        break;
      case 1: // west
        yaw = 90f;
        cx -= HANG_OFFSET;
        break;
      case 2: // north
        yaw = 180f;
        cz -= HANG_OFFSET;
        break;
      case 3: // east
        yaw = 270f;
        cx += HANG_OFFSET;
        break;
      case 4: // up
        pitch = -90f;
        cy += HANG_OFFSET;
        break;
      case 5: // down
        pitch = 90f;
        cy -= HANG_OFFSET;
        break;
      default:
        break;
    }

    com.github.retrooper.packetevents.protocol.world.Location packetLoc =
        new com.github.retrooper.packetevents.protocol.world.Location(
            cx, cy, cz, yaw, pitch);

    int spawnData = mapFacingToSpawnData(facing);
    WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
        entityId, uuid, type, packetLoc, yaw, spawnData,
        new Vector3d(0, 0, 0));
    LogData.get().info("[fakeentity] send spawn viewer=" + viewer.getName()
        + " id=" + entityId + " type=" + type.getName()
        + " loc=" + formatLoc(cx, cy, cz)
        + " yaw=" + yaw + " pitch=" + pitch
        + " facing=" + facing + " spawnData=" + spawnData);
    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawn);

    try {
      ItemStack item = buildPacketItem(itemStr);
      if (item != null && item.getType() != ItemTypes.AIR) {
        List<EntityData<?>> data = new ArrayList<>(1);
        data.add(new EntityData<>(8, EntityDataTypes.ITEMSTACK, item));
        WrapperPlayServerEntityMetadata meta =
            new WrapperPlayServerEntityMetadata(entityId, data);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, meta);
      }
    } catch (Throwable error) {
      LogData.get().warning("[fakeentity] failed to send item metadata id="
          + entityId + " item=" + itemStr + ": " + error);
    }

    trackAndSchedule(viewer, entityId, ttlTicks);
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  public void spawnArmorStand(Player viewer, Location loc, float yaw, long ttlTicks) {
    LogData.get().info("[fakeentity] skipped armor_stand viewer="
        + viewer.getName() + " loc=" + formatLoc(loc.getX(), loc.getY(), loc.getZ())
        + " yaw=" + yaw + " ttl=" + ttlTicks);
  }

  public void despawnAllFor(Player viewer) {
    List<Integer> list = active.remove(viewer.getUniqueId());
    if (list == null || list.isEmpty()) {
      return;
    }
    try {
      int[] ids = list.stream().mapToInt(Integer::intValue).toArray();
      WrapperPlayServerDestroyEntities destroy =
          new WrapperPlayServerDestroyEntities(ids);
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroy);
    } catch (Throwable ignored) {
    }
  }

  private void trackAndSchedule(Player viewer, int entityId, long ttlTicks) {
    active.computeIfAbsent(viewer.getUniqueId(),
        k -> Collections.synchronizedList(new ArrayList<>())).add(entityId);
    Bukkit.getScheduler().runTaskLater(plugin,
        () -> despawn(viewer, entityId), Math.max(1L, ttlTicks));
  }

  private void despawn(Player viewer, int entityId) {
    try {
      WrapperPlayServerDestroyEntities destroy =
          new WrapperPlayServerDestroyEntities(entityId);
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroy);
    } catch (Throwable ignored) {
    }
    List<Integer> list = active.get(viewer.getUniqueId());
    if (list != null) {
      list.remove(Integer.valueOf(entityId));
    }
  }

  private static String formatLoc(double x, double y, double z) {
    return String.format("%.3f,%.3f,%.3f", x, y, z);
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  private static int mapFacingToSpawnData(int facing) {
    return switch (facing) {
      case 0 -> 3; // south
      case 1 -> 4; // west
      case 2 -> 2; // north
      case 3 -> 5; // east
      case 4 -> 1; // up
      case 5 -> 0; // down
      default -> 0;
    };
  }

  private ItemStack buildPacketItem(String itemStr) {
    if (itemStr == null || itemStr.isEmpty()) {
      return ItemStack.builder().type(ItemTypes.AIR).amount(1).build();
    }
    String matName = itemStr;
    int colon = matName.indexOf(':');
    if (colon >= 0) {
      matName = matName.substring(colon + 1);
    }
    if (matName.isEmpty()) {
      return ItemStack.builder().type(ItemTypes.AIR).amount(1).build();
    }
    try {
      var itemType = ItemTypes.getByName(matName.toLowerCase());
      if (itemType == null) {
        return ItemStack.builder().type(ItemTypes.AIR).amount(1).build();
      }
      return ItemStack.builder().type(itemType).amount(1).build();
    } catch (Throwable e) {
      LogData.get().fine("[fakeentity] unknown item type: " + matName);
      return ItemStack.builder().type(ItemTypes.AIR).amount(1).build();
    }
  }
}
