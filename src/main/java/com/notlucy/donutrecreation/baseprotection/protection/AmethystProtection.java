package com.notlucy.donutrecreation.baseprotection.protection;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.packet.BlockIdRegistry;
import com.notlucy.donutrecreation.baseprotection.renderering.LightDebugProtection;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class AmethystProtection {

  private final RevealManager revealManager;
  private final BlockIdRegistry blockIds;

  public AmethystProtection(RevealManager revealManager, BlockIdRegistry blockIds) {
    this.revealManager = revealManager;
    this.blockIds = blockIds;
  }

  public boolean rewriteChunk(WrapperPlayServerChunkData wrapper, Player player) {
    var column = wrapper.getColumn();
    BaseChunk[] sections = column.getChunks();
    if (sections == null) {
      LogData.get().warning("[geode] " + player.getName() + " null sections");
      return false;
    }
    int chunkX = column.getX();
    int chunkZ = column.getZ();
    int minSection = revealManager.worldMinY() >> 4;

    long key = RevealManager.chunkKey(chunkX, chunkZ);
    Set<Long> positions = revealManager.geodePositions(key);
    BitSet litSections = new BitSet(sections.length);

    if (positions == null || !revealManager.wasGeodeScanned(key)) {
      try {
        positions = scanForAmethyst(sections, chunkX, chunkZ, minSection, litSections);
      } catch (Throwable e) {
        LogData.get().warning("[geode] scan crashed at " + chunkX + "," + chunkZ + ": " + e);
        return false;
      }
      revealManager.recordGeodePositions(key, positions);
      if (!positions.isEmpty() && revealManager.verboseLogging()) {
        LogData.get().info("[geode] discovered " + chunkX + "," + chunkZ
            + " (" + positions.size() + " nodes)");
      }
    } else if (!positions.isEmpty()) {
      for (long packed : positions) {
        int sectionIndex = (RevealManager.unpackY(packed) >> 4) - minSection;
        if (sectionIndex >= 0 && sectionIndex < sections.length) {
          litSections.set(sectionIndex);
        }
      }
    }

    if (positions.isEmpty()) {
      return false;
    }

    int swapped = maskPositions(sections, positions, minSection);
    if (swapped <= 0) {
      return false;
    }

    LightData light = wrapper.getLightData();
    if (light != null) {
      try {
        LightDebugProtection.stripLightForSections(light, litSections);
      } catch (Throwable e) {
        LogData.get().warning("[geode] light strip crashed at " + chunkX + "," + chunkZ + ": " + e);
      }
    }
    int finalSwapped = swapped;
    LogData.get().fine(() -> "[geode] hid " + finalSwapped + " block(section) for "
        + player.getName() + " in " + chunkX + "," + chunkZ
        + " sections=" + sections.length + " lit=" + litSections.cardinality());
    return true;
  }

  public boolean rewriteBlockChange(
      WrapperPlayServerBlockChange wrapper, Player player, int x, int y, int z) {
    if (!revealManager.geodeHideEnabled()) {
      return false;
    }

    WrappedBlockState state = wrapper.getBlockState();
    int newId = state == null ? -1 : state.getGlobalId();
    boolean nowAmethyst = blockIds.isAmethyst(newId);

    if (revealManager.hasGhostBlockAt(player.getUniqueId(), x, y, z)) {
      return false;
    }
    long packed = RevealManager.packPos(x, y, z);
    Set<Long> positions = revealManager.geodePositions(RevealManager.chunkKey(x >> 4, z >> 4));
    boolean wasAmethyst = positions != null && positions.contains(packed);

    syncCache(nowAmethyst, wasAmethyst, x, y, z);

    if (nowAmethyst || wasAmethyst) {
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(blockIds.floorId()));
      return true;
    }
    return false;
  }

  public int rewriteMultiBlock(
      WrapperPlayServerMultiBlockChange wrapper, Player player,
      boolean chunkRevealed, int floorFixesAlready) {
    if (!revealManager.geodeHideEnabled()) {
      return 0;
    }
    var section = wrapper.getChunkPosition();
    int chunkX = section.getX();
    int chunkZ = section.getZ();
    Set<Long> positions = revealManager.geodePositions(RevealManager.chunkKey(chunkX, chunkZ));

    int swaps = 0;
    boolean checkGhosts = revealManager.hasAnyGhostBlocks(player.getUniqueId());

    for (var enc : wrapper.getBlocks()) {
      int blockY = enc.getY();
      int blockX = enc.getX();
      int blockZ = enc.getZ();
      if (checkGhosts && revealManager.hasGhostBlockAt(
          player.getUniqueId(), blockX, blockY, blockZ)) {
        continue;
      }
      int blockId = enc.getBlockId();
      boolean nowAmethyst = blockIds.isAmethyst(blockId);
      long packed = RevealManager.packPos(blockX, blockY, blockZ);
      boolean wasAmethyst = positions != null && positions.contains(packed);

      syncCache(nowAmethyst, wasAmethyst, blockX, blockY, blockZ);

      if (nowAmethyst || wasAmethyst) {
        enc.setBlockId(blockIds.floorId());
        swaps++;
      }
    }
    if (swaps > 0) {
      final int finalSwaps = swaps;
      LogData.get().fine(() -> "[geode] multi swap=" + finalSwaps + " in " + chunkX + "," + chunkZ
          + " viewer=" + player.getName() + " floorFixes=" + floorFixesAlready);
    }
    return swaps;
  }

  private Set<Long> scanForAmethyst(
      BaseChunk[] sections, int chunkX, int chunkZ,
      int minSection, BitSet litSections) {
    Set<Long> allAmethyst = new HashSet<>();

    /* Scan every section that can contain a vanilla geode. */
    int maxScanSection = Math.max(2, minSection);
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
      int sectionY = minSection + sectionIndex;
      if (sectionY < minSection || sectionY > maxScanSection) {
        continue;
      }
      BaseChunk section = sections[sectionIndex];
      if (section == null) {
        continue;
      }

      boolean any = false;
      for (int x = 0; x < 16; x++) {
        for (int y = 0; y < 16; y++) {
          for (int z = 0; z < 16; z++) {
            int blockId = section.getBlockId(x, y, z);
            if (blockIds.isAmethyst(blockId)) {
              allAmethyst.add(RevealManager.packPos(
                  baseX + x, (sectionY << 4) + y, baseZ + z));
              any = true;
            }
          }
        }
      }
      if (any) {
        litSections.set(sectionIndex);
      }
    }
    return allAmethyst;
  }

  private int maskPositions(BaseChunk[] sections, Set<Long> positions, int minSection) {
    int swapped = 0;
    for (long packed : positions) {
      int worldX = RevealManager.unpackX(packed);
      int worldY = RevealManager.unpackY(packed);
      int worldZ = RevealManager.unpackZ(packed);

      int sectionIndex = (worldY >> 4) - minSection;
      if (sectionIndex < 0 || sectionIndex >= sections.length) {
        continue;
      }
      BaseChunk section = sections[sectionIndex];
      if (section == null) {
        continue;
      }
      int localX = worldX & 0xF;
      int localY = worldY & 0xF;
      int localZ = worldZ & 0xF;
      if (!blockIds.isAmethyst(section.getBlockId(localX, localY, localZ))) {
        continue;
      }
      int target = blockIds.floorId();
      section.set(localX, localY, localZ, target);
      swapped++;
    }
    return swapped;
  }

  private void syncCache(boolean nowAmethyst, boolean wasAmethyst, int x, int y, int z) {
    if (nowAmethyst && !wasAmethyst) {
      revealManager.rememberAmethystBlock(x, y, z);
    } else if (!nowAmethyst && wasAmethyst) {
      revealManager.forgetAmethystBlock(x, y, z);
    }
  }
}
