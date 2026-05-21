package com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.protection;

import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.RevealManager;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.packet.BlockIdRegistry;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.renderering.LightDebugProtection;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class AmethystProtection {

  private final RevealManager rm;
  private final BlockIdRegistry ids;
  private final Logger log;

  public AmethystProtection(RevealManager rm, BlockIdRegistry ids, Logger log) {
    this.rm = rm;
    this.ids = ids;
    this.log = log;
  }

  public boolean rewriteChunk(WrapperPlayServerChunkData wrapper, Player player) {
    var column = wrapper.getColumn();
    BaseChunk[] sections = column.getChunks();
    if (sections == null) {
      return false;
    }
    int cx = column.getX();
    int cz = column.getZ();
    int floorSection = rm.hideBelowY() >> 4;
    int minSection = rm.worldMinY() >> 4;

    long key = RevealManager.chunkKey(cx, cz);
    Set<Long> positions = rm.geodePositions(key);
    BitSet litSections = new BitSet(sections.length);

    if (positions == null || !rm.wasGeodeScanned(key)) {
      positions = scanForAmethyst(sections, cx, cz, minSection, floorSection, litSections);
      rm.recordGeodeChunk(key, positions);
      if (!positions.isEmpty()) {
        log.info("[geode] discovered " + cx + "," + cz
            + " (" + positions.size() + " nodes)");
      }
    } else if (!positions.isEmpty()) {
      for (long packed : positions) {
        int idx = (RevealManager.unpackY(packed) >> 4) - minSection;
        if (idx >= 0 && idx < sections.length) {
          litSections.set(idx);
        }
      }
    }

    if (positions.isEmpty() || rm.isGeodeRevealedFor(player, cx, cz)) {
      return false;
    }

    int swapped = maskPositions(sections, positions, minSection);
    if (swapped <= 0) {
      return false;
    }

    LightData light = wrapper.getLightData();
    if (light != null) {
      LightDebugProtection.stripBlockLightForSections(light, litSections);
    }
    log.fine(() -> "[geode] hid " + swapped + " block(s) for "
        + player.getName() + " in " + cx + "," + cz);
    return true;
  }

  public boolean rewriteBlockChange(
      WrapperPlayServerBlockChange wrapper, Player player, int x, int y, int z) {
    if (!rm.geodeHideEnabled()) {
      return false;
    }

    WrappedBlockState state = wrapper.getBlockState();
    int newId = state == null ? -1 : state.getGlobalId();
    boolean nowAmethyst = ids.isAmethyst(newId);

    long packed = RevealManager.packPos(x, y, z);
    Set<Long> positions = rm.geodePositions(RevealManager.chunkKey(x >> 4, z >> 4));
    boolean wasAmethyst = positions != null && positions.contains(packed);

    syncCache(nowAmethyst, wasAmethyst, x, y, z);

    if ((nowAmethyst || wasAmethyst) && !rm.isGeodeRevealedFor(player, x >> 4, z >> 4)) {
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(ids.stoneId()));
      return true;
    }
    return false;
  }

  public int rewriteMultiBlock(
      WrapperPlayServerMultiBlockChange wrapper, Player player,
      boolean chunkRevealed, int floorFixesAlready) {
    if (!rm.geodeHideEnabled()) {
      return 0;
    }
    var section = wrapper.getChunkPosition();
    int cx = section.getX();
    int cz = section.getZ();
    boolean revealedHere = rm.isGeodeRevealedFor(player, cx, cz);
    Set<Long> positions = rm.geodePositions(RevealManager.chunkKey(cx, cz));

    int swaps = 0;
    int floorY = rm.hideBelowY();
    int stone = ids.stoneId();

    for (var enc : wrapper.getBlocks()) {
      int by = enc.getY();
      if (by < floorY && !chunkRevealed) {
        continue;
      }

      int bx = enc.getX();
      int bz = enc.getZ();
      int blockId = enc.getBlockId();
      boolean nowAmethyst = ids.isAmethyst(blockId);
      long packed = RevealManager.packPos(bx, by, bz);
      boolean wasAmethyst = positions != null && positions.contains(packed);

      syncCache(nowAmethyst, wasAmethyst, bx, by, bz);

      if (!revealedHere && (nowAmethyst || wasAmethyst)) {
        enc.setBlockId(stone);
        swaps++;
      }
    }
    if (swaps > 0) {
      final int finalSwaps = swaps;
      log.fine(() -> "[geode] multi swap=" + finalSwaps + " in " + cx + "," + cz
          + " viewer=" + player.getName() + " floorFixes=" + floorFixesAlready);
    }
    return swaps;
  }

  private Set<Long> scanForAmethyst(
      BaseChunk[] sections, int cx, int cz,
      int minSection, int floorSection, BitSet litSections) {
    Set<Long> hits = new HashSet<>();
    for (int i = 0; i < sections.length; i++) {
      int sy = minSection + i;
      if (sy < floorSection) {
        continue;
      }
      BaseChunk s = sections[i];
      if (s == null) {
        continue;
      }

      if (!sparseHasAmethyst(s)) {
        continue;
      }

      boolean any = false;
      for (int x = 0; x < 16; x++) {
        for (int y = 0; y < 16; y++) {
          for (int z = 0; z < 16; z++) {
            int blockId = s.getBlockId(x, y, z);
            if (ids.isAmethyst(blockId)) {
              hits.add(RevealManager.packPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
              any = true;
            }
          }
        }
      }
      if (any) {
        litSections.set(i);
      }
    }
    return hits;
  }

  private boolean sparseHasAmethyst(BaseChunk section) {
    for (int x = 0; x < 16; x += 4) {
      for (int y = 0; y < 16; y += 4) {
        for (int z = 0; z < 16; z += 4) {
          if (ids.isAmethyst(section.getBlockId(x, y, z))) {
            return true;
          }
        }
      }
    }
    for (int x = 1; x < 16; x += 4) {
      for (int y = 1; y < 16; y += 4) {
        for (int z = 1; z < 16; z += 4) {
          if (ids.isAmethyst(section.getBlockId(x, y, z))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private int maskPositions(BaseChunk[] sections, Set<Long> positions, int minSection) {
    int stone = ids.stoneId();
    int swapped = 0;
    for (long packed : positions) {
      int wy = RevealManager.unpackY(packed);
      int idx = (wy >> 4) - minSection;
      if (idx < 0 || idx >= sections.length) {
        continue;
      }
      BaseChunk s = sections[idx];
      if (s == null) {
        continue;
      }
      int lx = RevealManager.unpackX(packed) & 0xF;
      int ly = wy & 0xF;
      int lz = RevealManager.unpackZ(packed) & 0xF;
      if (!ids.isAmethyst(s.getBlockId(lx, ly, lz))) {
        continue;
      }
      s.set(lx, ly, lz, stone);
      swapped++;
    }
    return swapped;
  }

  private void syncCache(boolean nowAmethyst, boolean wasAmethyst, int x, int y, int z) {
    if (nowAmethyst && !wasAmethyst) {
      rm.recordAmethystAt(x, y, z);
    } else if (!nowAmethyst && wasAmethyst) {
      rm.forgetAmethystAt(x, y, z);
    }
  }
}
