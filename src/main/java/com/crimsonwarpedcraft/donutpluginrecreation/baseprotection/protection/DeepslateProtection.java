package com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.protection;

import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.RevealManager;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.packet.BlockIdRegistry;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.renderering.LightDebugProtection;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class DeepslateProtection {

  private final RevealManager rm;
  private final BlockIdRegistry ids;
  private final Logger log;
  private final int decoyMask;
  private final boolean decoyEnabled;

  public DeepslateProtection(RevealManager rm, BlockIdRegistry ids, Logger log) {
    this.rm = rm;
    this.ids = ids;
    this.log = log;
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
      return false;
    }

    int cx = column.getX();
    int cz = column.getZ();
    if (rm.isRevealed(player, cx, cz)) {
      return false;
    }

    int floorY = rm.hideBelowY();
    int floorSection = floorY >> 4;
    int minSection = rm.worldMinY() >> 4;
    int worldOriginX = cx << 4;
    int worldOriginZ = cz << 4;
    int salt = rm.saltFor(player.getUniqueId());

    boolean touched = false;
    for (int i = 0; i < sections.length; i++) {
      BaseChunk section = sections[i];
      if (section == null) {
        continue;
      }
      int sy = minSection + i;
      int worldOriginY = sy << 4;

      switch (Integer.compare(sy, floorSection)) {
        case -1 -> {
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, 16, salt);
          touched = true;
        }
        case 0 -> {
          int top = Math.min(floorY - worldOriginY, 16);
          if (top > 0) {
            fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, top, salt);
            touched = true;
          }
        }
        default -> {}
      }
    }

    if (touched) {
      LightDebugProtection.stripFloorLight(wrapper, minSection, floorSection);
      BlockEntityDebugProtection.scrubTilesBelow(column, floorY);
      log.fine(() -> "[deepslate] " + player.getName()
          + " chunk=" + cx + "," + cz + " floorY=" + floorY);
    }
    return touched;
  }

  public boolean rewriteBlockChange(
      WrapperPlayServerBlockChange wrapper, Player player, int x, int y, int z) {
    if (y >= rm.hideBelowY()) {
      return false;
    }
    int cx = x >> 4;
    int cz = z >> 4;
    if (rm.isRevealed(player, cx, cz)) {
      return false;
    }
    int salt = rm.saltFor(player.getUniqueId());
    wrapper.setBlockState(WrappedBlockState.getByGlobalId(ids.floorIdAt(salt, x, y, z)));
    return true;
  }

  public boolean shouldMaskMultiBlock(int by, boolean chunkRevealed) {
    return by < rm.hideBelowY() && !chunkRevealed;
  }

  private void fillNoise(
      BaseChunk section, int worldOriginX, int worldOriginY, int worldOriginZ,
      int topYexclusive, int salt) {
    boolean decoy = decoyEnabled;
    int decoyId = decoy ? ids.decoyAmethystId() : 0;
    int dmask = decoyMask;
    for (int x = 0; x < 16; x++) {
      int wx = worldOriginX + x;
      for (int z = 0; z < 16; z++) {
        int wz = worldOriginZ + z;
        for (int y = 0; y < topYexclusive; y++) {
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
