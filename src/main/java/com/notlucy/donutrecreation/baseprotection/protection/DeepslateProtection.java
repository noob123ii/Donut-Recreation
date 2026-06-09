package com.notlucy.donutrecreation.baseprotection.protection;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.packet.BlockIdRegistry;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.LightDebugProtection;
import com.notlucy.donutrecreation.util.LogData;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class DeepslateProtection {

  private final RevealManager rm;
  private final BlockIdRegistry ids;
  private final int decoyMask;
  private final boolean decoyEnabled;

  public DeepslateProtection(RevealManager rm, BlockIdRegistry ids) {
    this.rm = rm;
    this.ids = ids;
    int rateBits = Math.max(0, Math.min(20,
        rm.plugin().getConfig().getInt("hider.decoy-amethyst-rate-bits", 11)));
    this.decoyEnabled = rm.plugin().getConfig().getBoolean("hider.decoy-amethyst-enabled", true)
        && rateBits > 0;
    this.decoyMask = (1 << rateBits) - 1;
  }

  public boolean rewriteChunk(
      PacketSendEvent event, WrapperPlayServerChunkData wrapper, Player player) {
    Column column = wrapper.getColumn();
    BaseChunk[] sections = column.getChunks();
    if (sections == null) {
      LogData.get().warning("[deepslate] " + player.getName() + " null sections");
      return false;
    }

    int cx = column.getX();
    int cz = column.getZ();
    int floorY = rm.hideBelowY();
    int upperY = rm.upperBarrierY();
    int floorSection = floorY >> 4;
    int minSection = rm.worldMinY() >> 4;
    int worldOriginX = cx << 4;
    int worldOriginZ = cz << 4;
    int salt = rm.saltFor(player.getUniqueId());
    boolean lowerRevealed = rm.isRevealed(player, cx, cz);
    boolean upperRevealed = rm.isUpperRevealed(player, cx, cz);
    boolean upperActive = upperY > floorY;

    boolean touched = false;
    int modifiedSections = 0;
    int nullSections = 0;
    for (int i = 0; i < sections.length; i++) {
      BaseChunk section = sections[i];
      int sy = minSection + i;
      if (section == null) {
        if (!lowerRevealed && sy <= floorSection) {
          nullSections++;
        }
        continue;
      }
      int worldOriginY = sy << 4;

      if (!lowerRevealed) {
        int hi = Math.min(16, floorY - worldOriginY);
        if (hi > 0) {
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, 0, hi, salt);
          touched = true;
          modifiedSections++;
        }
      }
      if (upperActive && !upperRevealed) {
        int lo = Math.max(0, floorY - worldOriginY);
        int hi = Math.min(16, upperY - worldOriginY);
        if (hi > lo) {
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, lo, hi, salt);
          touched = true;
          modifiedSections++;
        }
      }
    }

    boolean tilesChanged = handleTiles(column, sections, minSection, cx, cz,
        player, lowerRevealed, upperRevealed);

    if (touched || nullSections > 0) {
      if (!lowerRevealed) {
        try {
          LightDebugProtection.stripFloorLight(wrapper, minSection, floorSection);
        } catch (Throwable e) {
          LogData.get().warning("[deepslate] light strip failed at " + cx + "," + cz + ": " + e);
        }
      }
      if (nullSections > 0) {
        int py = player.getLocation().getBlockY();
        LogData.get().warning("[deepslate] " + player.getName()
            + " chunk=" + cx + "," + cz + " NULL sections below floor=" + nullSections
            + " playerY=" + py);
      }
      if (rm.verboseLogging()) {
        int py = player.getLocation().getBlockY();
        LogData.get().info("[deepslate] " + player.getName()
            + " chunk=" + cx + "," + cz + " floorY=" + floorY + " upperY=" + upperY
            + " sections=" + sections.length + " modified=" + modifiedSections
            + " null=" + nullSections + " playerY=" + py);
      }
    }
    return touched || nullSections > 0 || tilesChanged;
  }

  public boolean rewriteBlockChange(
      WrapperPlayServerBlockChange wrapper, Player player, int x, int y, int z) {
    int floorY = rm.hideBelowY();
    int cx = x >> 4;
    int cz = z >> 4;
    int salt = rm.saltFor(player.getUniqueId());
    if (y < floorY) {
      if (rm.isRevealed(player, cx, cz)) {
        return false;
      }
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(ids.floorIdAt(salt, x, y, z)));
      return true;
    }
    int upperY = rm.upperBarrierY();
    if (upperY > floorY && y < upperY) {
      if (rm.isUpperRevealed(player, cx, cz)) {
        return false;
      }
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(ids.floorIdAt(salt, x, y, z)));
      return true;
    }
    return false;
  }

  public boolean maskTileBlockChange(
      WrapperPlayServerBlockChange wrapper, Player player, int x, int y, int z) {
    if (!rm.tileMaskEnabled()) {
      return false;
    }
    Set<Long> tiles = rm.tilePositions(RevealManager.chunkKey(x >> 4, z >> 4));
    if (tiles == null || !tiles.contains(RevealManager.packPos(x, y, z))) {
      return false;
    }
    if (bandRevealedForTile(player, x, y, z) && rm.shouldMaskTile(player, x, y, z)) {
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(ids.airId()));
      return true;
    }
    return false;
  }

  public int maskTilesMultiBlock(WrapperPlayServerMultiBlockChange wrapper, Player player) {
    if (!rm.tileMaskEnabled()) {
      return 0;
    }
    var section = wrapper.getChunkPosition();
    if (section == null) {
      return 0;
    }
    Set<Long> tiles = rm.tilePositions(RevealManager.chunkKey(section.getX(), section.getZ()));
    if (tiles == null || tiles.isEmpty()) {
      return 0;
    }
    int airId = ids.airId();
    int swaps = 0;
    for (var enc : wrapper.getBlocks()) {
      int wx = enc.getX();
      int wy = enc.getY();
      int wz = enc.getZ();
      if (!tiles.contains(RevealManager.packPos(wx, wy, wz))) {
        continue;
      }
      if (bandRevealedForTile(player, wx, wy, wz) && rm.shouldMaskTile(player, wx, wy, wz)
          && enc.getBlockId() != airId) {
        enc.setBlockId(airId);
        swaps++;
      }
    }
    return swaps;
  }

  private boolean bandRevealedForTile(Player player, int x, int y, int z) {
    int floorY = rm.hideBelowY();
    if (y < floorY) {
      return rm.isRevealed(player, x >> 4, z >> 4);
    }
    if (y < rm.upperBarrierY()) {
      return rm.isUpperRevealed(player, x >> 4, z >> 4);
    }
    return true;
  }

  private boolean handleTiles(
      Column column, BaseChunk[] sections, int minSection, int cx, int cz,
      Player player, boolean lowerRevealed, boolean upperRevealed) {
    if (!rm.tileMaskEnabled()) {
      return false;
    }
    TileEntity[] tiles;
    try {
      tiles = column.getTileEntities();
    } catch (Throwable ignored) {
      return false;
    }
    long key = RevealManager.chunkKey(cx, cz);
    if (tiles == null || tiles.length == 0) {
      rm.recordTiles(key, Collections.emptySet());
      return false;
    }
    int floorY = rm.hideBelowY();
    int upperY = rm.upperBarrierY();
    int airId = ids.airId();

    Set<Long> positions = new HashSet<>(tiles.length);
    TileEntity[] keep = new TileEntity[tiles.length];
    int kept = 0;
    boolean changed = false;
    for (TileEntity tile : tiles) {
      if (tile == null) {
        continue;
      }
      int wy = tile.getY();
      int lx = tile.getX();
      int lz = tile.getZ();
      int wx = (cx << 4) + lx;
      int wz = (cz << 4) + lz;
      positions.add(RevealManager.packPos(wx, wy, wz));

      boolean bandRevealed;
      if (wy < floorY) {
        bandRevealed = lowerRevealed;
      } else if (wy < upperY) {
        bandRevealed = upperRevealed;
      } else {
        bandRevealed = true;
      }

      boolean drop = false;
      boolean air = false;
      if (!bandRevealed) {
        if (wy < upperY) {
          drop = true;
        }
      } else if (rm.shouldMaskTile(player, wx, wy, wz)) {
        drop = true;
        air = true;
      }

      if (air) {
        int idx = (wy >> 4) - minSection;
        if (idx >= 0 && idx < sections.length && sections[idx] != null) {
          sections[idx].set(lx, wy & 15, lz, airId);
        }
      }
      if (drop) {
        changed = true;
      } else {
        keep[kept++] = tile;
      }
    }
    rm.recordTiles(key, positions);
    if (changed) {
      TileEntity[] result = kept == 0 ? new TileEntity[0] : java.util.Arrays.copyOf(keep, kept);
      try {
        BlockEntityDebugProtection.replaceTiles(column, result);
      } catch (Throwable ignored) {
        return false;
      }
    }
    return changed;
  }

  public boolean shouldMaskMultiBlock(int by, boolean lowerRevealed, boolean upperRevealed) {
    int floorY = rm.hideBelowY();
    if (by < floorY) {
      return !lowerRevealed;
    }
    int upperY = rm.upperBarrierY();
    return upperY > floorY && by < upperY && !upperRevealed;
  }

  private void fillNoise(
      BaseChunk section, int worldOriginX, int worldOriginY, int worldOriginZ,
      int lowY, int highY, int salt) {
    boolean decoy = decoyEnabled;
    int decoyId = decoy ? ids.decoyClusterId() : 0;
    int dmask = decoyMask;
    for (int x = 0; x < 16; x++) {
      int wx = worldOriginX + x;
      for (int z = 0; z < 16; z++) {
        int wz = worldOriginZ + z;
        for (int y = lowY; y < highY; y++) {
          int wy = worldOriginY + y;
          int target = ids.floorIdAt(salt, wx, wy, wz);
          if (decoy && (decoyHash(salt, wx, wy, wz) & dmask) == 0) {
            target = decoyId;
          }
          if (section.getBlockId(x, y, z) != target) {
            section.set(x, y, z, target);
          }
        }
      }
    }
  }

  private static int decoyHash(int salt, int x, int y, int z) {
    int h = (x ^ salt) * 0x27D4EB2D;
    h ^= Integer.rotateLeft((z ^ salt) * 0x165667B1, 11);
    h ^= Integer.rotateLeft(y * 0xD2511F53, 7);
    h ^= (h >>> 17);
    return h;
  }

  public int floorId() {
    return ids.floorId();
  }

  public int floorIdAt(int salt, int x, int y, int z) {
    return ids.floorIdAt(salt, x, y, z);
  }

  public boolean isWrapperRelevant(WrapperPlayServerMultiBlockChange wrapper) {
    return wrapper != null && wrapper.getChunkPosition() != null && wrapper.getBlocks() != null;
  }
}
