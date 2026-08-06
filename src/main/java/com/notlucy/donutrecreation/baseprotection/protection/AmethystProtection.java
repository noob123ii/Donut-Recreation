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

  private final RevealManager rm;
  private final BlockIdRegistry ids;

  public AmethystProtection(RevealManager rm, BlockIdRegistry ids) {
    this.rm = rm;
    this.ids = ids;
  }

  public boolean rewriteChunk(WrapperPlayServerChunkData wrapper, Player player) {
    var column = wrapper.getColumn();
    BaseChunk[] sections = column.getChunks();
    if (sections == null) {
      LogData.get().warning("[geode] " + player.getName() + " null sections");
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
      try {
        positions = scanForAmethyst(sections, cx, cz, minSection, floorSection, litSections);
      } catch (Throwable e) {
        LogData.get().warning("[geode] scan crashed at " + cx + "," + cz + ": " + e);
        return false;
      }
      rm.recordGeodeChunk(key, positions);
      if (!positions.isEmpty() && rm.verboseLogging()) {
        LogData.get().info("[geode] discovered " + cx + "," + cz
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

    enhanceGeodeBuds(sections, positions, minSection);

    int swapped = maskPositions(sections, positions, minSection, rm.hideBelowY(), player.getWorld());
    if (swapped <= 0) {
      return false;
    }

    LightData light = wrapper.getLightData();
    if (light != null) {
      try {
        LightDebugProtection.stripLightForSections(light, litSections);
      } catch (Throwable e) {
        LogData.get().warning("[geode] light strip crashed at " + cx + "," + cz + ": " + e);
      }
    }
    int finalSwapped = swapped;
    LogData.get().fine(() -> "[geode] hid " + finalSwapped + " block(s) for "
        + player.getName() + " in " + cx + "," + cz
        + " sections=" + sections.length + " lit=" + litSections.cardinality());
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

    if (rm.hasGhostBlockAt(player.getUniqueId(), x, y, z)) {
      return false;
    }
    long packed = RevealManager.packPos(x, y, z);
    Set<Long> positions = rm.geodePositions(RevealManager.chunkKey(x >> 4, z >> 4));
    boolean wasAmethyst = positions != null && positions.contains(packed);

    syncCache(nowAmethyst, wasAmethyst, x, y, z);

    if ((nowAmethyst || wasAmethyst) && !rm.isGeodeRevealedFor(player, x >> 4, z >> 4)) {
      int target = (y < rm.hideBelowY()) ? ids.floorId() : ids.stoneId();
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(target));
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

    for (var enc : wrapper.getBlocks()) {
      int by = enc.getY();
      if (by < floorY && !chunkRevealed) {
        continue;
      }

      int bx = enc.getX();
      int bz = enc.getZ();
      if (rm.hasGhostBlockAt(player.getUniqueId(), bx, by, bz)) {
        continue;
      }
      int blockId = enc.getBlockId();
      boolean nowAmethyst = ids.isAmethyst(blockId);
      long packed = RevealManager.packPos(bx, by, bz);
      boolean wasAmethyst = positions != null && positions.contains(packed);

      syncCache(nowAmethyst, wasAmethyst, bx, by, bz);

      if (!revealedHere && (nowAmethyst || wasAmethyst)) {
        int target = (by < floorY) ? ids.floorId() : ids.stoneId();
        enc.setBlockId(target);
        swaps++;
      }
    }
    if (swaps > 0) {
      final int finalSwaps = swaps;
      LogData.get().fine(() -> "[geode] multi swap=" + finalSwaps + " in " + cx + "," + cz
          + " viewer=" + player.getName() + " floorFixes=" + floorFixesAlready);
    }
    return swaps;
  }

  private Set<Long> scanForAmethyst(
      BaseChunk[] sections, int cx, int cz,
      int minSection, int floorSection, BitSet litSections) {
    Set<Long> allAmethyst = new HashSet<>();

    int maxScanSection = floorSection + 10;
    for (int i = 0; i < sections.length; i++) {
      int sy = minSection + i;
      if (sy < floorSection || sy > maxScanSection) {
        continue;
      }
      BaseChunk s = sections[i];
      if (s == null) {
        continue;
      }

      boolean any = false;
      for (int x = 0; x < 16; x++) {
        for (int y = 0; y < 16; y++) {
          for (int z = 0; z < 16; z++) {
            int blockId = s.getBlockId(x, y, z);
            if (ids.isAmethyst(blockId)) {
              allAmethyst.add(RevealManager.packPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
              any = true;
            }
          }
        }
      }
      if (any) {
        litSections.set(i);
      }
    }

    Set<Long> clustered = new HashSet<>();
    if (allAmethyst.size() < 20) {

      return clustered;
    }

    for (long packed : allAmethyst) {
      int ax = RevealManager.unpackX(packed);
      int ay = RevealManager.unpackY(packed);
      int az = RevealManager.unpackZ(packed);
      int neighbors = 0;

      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            long neighborPacked = RevealManager.packPos(ax + dx, ay + dy, az + dz);

            if (allAmethyst.contains(neighborPacked)) {
              neighbors++;
            } else {

              int neighborCx = (ax + dx) >> 4;
              int neighborCz = (az + dz) >> 4;
              if (neighborCx != cx || neighborCz != cz) {
                Set<Long> neighborChunkPos = rm.geodePositions(RevealManager.chunkKey(neighborCx, neighborCz));
                if (neighborChunkPos != null && neighborChunkPos.contains(neighborPacked)) {
                  neighbors++;
                }
              }
            }
          }
        }
      }

      if (neighbors >= 3) {
        clustered.add(packed);
      }
    }

    return clustered;
  }

  private void enhanceGeodeBuds(BaseChunk[] sections, Set<Long> positions, int minSection) {
    int largeBudId = ids.largeBudId();
    int replaced = 0;
    for (long packed : positions) {
      int wx = RevealManager.unpackX(packed);
      int wy = RevealManager.unpackY(packed);
      int wz = RevealManager.unpackZ(packed);
      int idx = (wy >> 4) - minSection;
      if (idx < 0 || idx >= sections.length) continue;
      BaseChunk s = sections[idx];
      if (s == null) continue;
      int lx = wx & 0xF;
      int ly = wy & 0xF;
      int lz = wz & 0xF;
      int currentId = s.getBlockId(lx, ly, lz);
      if (ids.isAmethyst(currentId) && currentId != ids.decoyClusterId()
          && currentId != ids.amethystBlockId()) {
        int h = (wx * 374761393 ^ wz * 668265263 ^ wy * 1274126177);
        h = (h ^ (h >> 13)) * 1274126177;
        h = h ^ (h >> 16);
        if ((h & 0x7FFFFFFF) % 9 == 0) {
          s.set(lx, ly, lz, largeBudId);
          replaced++;
        }
      }
    }
  }

  private int maskPositions(BaseChunk[] sections, Set<Long> positions, int minSection, int floorY, World world) {
    int swapped = 0;
    for (long packed : positions) {
      int wx = RevealManager.unpackX(packed);
      int wy = RevealManager.unpackY(packed);
      int wz = RevealManager.unpackZ(packed);

      int idx = (wy >> 4) - minSection;
      if (idx < 0 || idx >= sections.length) {
        continue;
      }
      BaseChunk s = sections[idx];
      if (s == null) {
        continue;
      }
      int lx = wx & 0xF;
      int ly = wy & 0xF;
      int lz = wz & 0xF;
      if (!ids.isAmethyst(s.getBlockId(lx, ly, lz))) {
        continue;
      }
      int target = (wy < floorY) ? ids.floorId() : ids.stoneId();
      s.set(lx, ly, lz, target);
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
