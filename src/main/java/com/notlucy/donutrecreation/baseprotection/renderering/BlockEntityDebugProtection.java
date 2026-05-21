package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.bukkit.entity.Player;

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

  private final RevealManager rm;

  public BlockEntityDebugProtection(RevealManager rm) {
    this.rm = rm;
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
    if (by >= rm.hideBelowY()) {
      return;
    }
    int cx = pos.getX() >> 4;
    int cz = pos.getZ() >> 4;
    if (rm.isRevealed(player, cx, cz)) {
      return;
    }
    event.setCancelled(true);
  }

  private static void warnOnce() {
    if (reflectionWarned) {
      return;
    }
    reflectionWarned = true;
    java.util.logging.Logger.getLogger("BaseProtection").warning(
        "[hider] Column tile-entity field not found; tile masking disabled "
            + "(PacketEvents API may have changed).");
  }
}
