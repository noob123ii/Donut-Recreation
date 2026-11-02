package com.notlucy.donutrecreation.baseprotection.renderering;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.util.LogData;

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

  private final RevealManager revealManager;
  private final boolean maskEnabled;

  public BlockEntityDebugProtection(RevealManager revealManager) {
    this.revealManager = revealManager;
    var cfg = revealManager.plugin().getConfig();
    this.maskEnabled = cfg.getBoolean("hider.tile-entity-mask-enabled", true);
  }

  public boolean maskEnabled() {
    return maskEnabled;
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

  /**
   * Removes block entities from a chunk payload for a viewer:
   *  - tiles below the hide floor are removed unless the chunk is revealed;
   *  - tiles in the barrier band (floorY..upperY) are removed unless the
   *    band is revealed for the viewer.
   * Any region the viewer may legitimately see (surface, revealed
   * underworld, revealed band) keeps all of its block entities.
   */
  public boolean scrubChunk(Column column, Player player, int chunkX, int chunkZ) {
    if (!maskEnabled) {
      return false;
    }
    TileEntity[] tiles = column.getTileEntities();
    if (tiles == null || tiles.length == 0) {
      return false;
    }
    int floorY = revealManager.hideBelowY();
    int upperY = revealManager.upperBarrierY();

    List<TileEntity> keep = new ArrayList<>(tiles.length);
    for (TileEntity t : tiles) {
      if (t == null) {
        continue;
      }
      int y = t.getY();
      if (y >= upperY) {
        keep.add(t);
        continue;
      }
      boolean regionVisible = y < floorY
          ? revealManager.isChunkRevealed(player, chunkX, chunkZ)
          : revealManager.isBarrierBandRevealed(player, chunkX, chunkZ);
      if (!regionVisible) {
        continue;
      }
      keep.add(t);
    }
    if (keep.size() == tiles.length) {
      return false;
    }
    TileEntity[] result = keep.isEmpty()
        ? new TileEntity[0]
        : keep.toArray(new TileEntity[0]);
    replaceTiles(column, result);
    return true;
  }

  public void handleBlockEntityData(PacketSendEvent event, Player player) {
    WrapperPlayServerBlockEntityData wrapper = new WrapperPlayServerBlockEntityData(event);
    var position = wrapper.getPosition();
    int blockY = position.getY();

    if (!revealManager.isPositionVisible(player, position.getX(), blockY, position.getZ())) {
      event.setCancelled(true);
    }
  }

  private static void warnOnce() {
    if (reflectionWarned) {
      return;
    }
    reflectionWarned = true;
    LogData.get().warning(
        "[hider] Column tile-entity field not found; tile masking disabled "
            + "(PacketEvents API may have changed).");
  }
}
