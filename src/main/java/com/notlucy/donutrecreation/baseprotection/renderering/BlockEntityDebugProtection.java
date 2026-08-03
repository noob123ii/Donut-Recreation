package com.notlucy.donutrecreation.baseprotection.renderering;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.notlucy.donutrecreation.baseprotection.RevealManager;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class BlockEntityDebugProtection {

  private static final Field TILE_ARRAY_FIELD = locateTileField();
  private static volatile boolean reflectionWarned = false;

  private static Field locateTileField() {
    String[] candidates = {"tileEntities", "blockEntities", "tiles", "tileData"};
    for (String name : candidates) {
      try {
        Field f = Column.class.getDeclaredField(name);
        if (TileEntity[].class.isAssignableFrom(f.getType())) {
          f.setAccessible(true);
          return f;
        }
      } catch (NoSuchFieldException ignored) {
      }
    }
    for (Field f : Column.class.getDeclaredFields()) {
      if (TileEntity[].class.isAssignableFrom(f.getType())) {
        f.setAccessible(true);
        return f;
      }
    }
    return null;
  }

  private static final int PROXIMITY_BLOCKS = 10;
  private static final Set<Material> RESTRICTED_TILES = Set.of(
      Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
      Material.BARREL, Material.SHULKER_BOX,
      Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
      Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
      Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
      Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
      Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
      Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
      Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
      Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX,
      Material.SPAWNER);

  private final RevealManager rm;

  public BlockEntityDebugProtection(RevealManager rm) {
    this.rm = rm;
  }

  public static void replaceTiles(Column column, TileEntity[] tiles) {
    if (TILE_ARRAY_FIELD == null) {
      warnOnce();
      return;
    }
    try {
      TILE_ARRAY_FIELD.set(column, tiles);
    } catch (IllegalAccessException ignored) {
    }
  }

  public static void scrubTilesBelow(Column column, int floorY) {
    if (TILE_ARRAY_FIELD == null) {
      warnOnce();
      return;
    }
    TileEntity[] tiles = column.getTileEntities();
    if (tiles == null || tiles.length == 0) {
      return;
    }

    TileEntity[] keep = new TileEntity[tiles.length];
    int kept = 0;
    for (TileEntity t : tiles) {
      if (t == null) {
        continue;
      }
      if (t.getYShort() >= floorY) {
        keep[kept++] = t;
      }
    }
    if (kept == tiles.length) {
      return;
    }
    TileEntity[] result = kept == 0 ? new TileEntity[0] : Arrays.copyOf(keep, kept);
    try {
      TILE_ARRAY_FIELD.set(column, result);
    } catch (IllegalAccessException ignored) {
    }
  }

  public void handleBlockEntityData(PacketSendEvent event, Player player) {
    WrapperPlayServerBlockEntityData wrapper = new WrapperPlayServerBlockEntityData(event);
    var pos = wrapper.getPosition();
    int by = pos.getY();
    int cx = pos.getX() >> 4;
    int cz = pos.getZ() >> 4;

    if (by < rm.hideBelowY()) {
      if (!rm.isRevealed(player, cx, cz)) {
        event.setCancelled(true);
        return;
      }
    }

    World world = player.getWorld();
    Material type = world.getBlockAt(pos.getX(), by, pos.getZ()).getType();
    if (RESTRICTED_TILES.contains(type)) {
      Location playerLoc = player.getLocation();
      double dx = pos.getX() + 0.5 - playerLoc.getX();
      double dy = by + 0.5 - playerLoc.getY();
      double dz = pos.getZ() + 0.5 - playerLoc.getZ();
      double distSq = dx * dx + dy * dy + dz * dz;
      if (distSq > PROXIMITY_BLOCKS * PROXIMITY_BLOCKS) {
        event.setCancelled(true);
      }
    }
  }

  private static void warnOnce() {
    if (reflectionWarned) {
      return;
    }
    reflectionWarned = true;
    com.notlucy.donutrecreation.util.LogData.get().warning(
        "[hider] Column tile-entity field not found; tile masking disabled "
            + "(PacketEvents API may have changed).");
  }
}
