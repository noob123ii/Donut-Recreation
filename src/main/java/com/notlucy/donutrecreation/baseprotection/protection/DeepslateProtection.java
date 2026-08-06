package com.notlucy.donutrecreation.baseprotection.protection;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.packet.BlockIdRegistry;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.LightDebugProtection;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class DeepslateProtection {

  private final RevealManager rm;
  private final BlockIdRegistry ids;

  public DeepslateProtection(RevealManager rm, BlockIdRegistry ids) {
    this.rm = rm;
    this.ids = ids;
  }

  public boolean rewriteChunk(
      PacketSendEvent event, WrapperPlayServerChunkData wrapper, Player player) {

    var env = player.getWorld().getEnvironment();
    if (env == org.bukkit.World.Environment.THE_END || env == org.bukkit.World.Environment.NETHER) {
      return false;
    }

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
    long worldSeed = player.getWorld().getSeed();
    boolean geodeChunk = Math.abs(geodeNoise(cx, 999, cz, salt) % 100) <= 8;
    int[] geodeBudget = {rm.fakeGeodeAmethystPerChunk()};
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
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, 0, hi,
              salt, player, geodeChunk, geodeBudget);
          touched = true;
          modifiedSections++;
        }
      }
      if (upperActive && !upperRevealed) {
        int lo = Math.max(0, floorY - worldOriginY);
        int hi = Math.min(16, upperY - worldOriginY);
        if (hi > lo) {
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, lo, hi,
              salt, player, geodeChunk, geodeBudget);
          touched = true;
          modifiedSections++;
        }
      }

      if ((lowerRevealed || upperRevealed) && env == org.bukkit.World.Environment.NORMAL) {
        int lo = Math.max(0, floorY - worldOriginY);
        int hi = Math.min(16, upperY - worldOriginY);
        if (hi > lo) {
          if (shouldHideOresInChunk(worldSeed, cx, cz)) {
            int stoneId = ids.stoneId();
            int oreMasked = 0;
            for (int x = 0; x < 16; x++) {
              int wx = worldOriginX + x;
              for (int z = 0; z < 16; z++) {
                int wz = worldOriginZ + z;
                for (int y = lo; y < hi; y++) {
                  int blockId = section.getBlockId(x, y, z);
                  if (ids.isOre(blockId) && shouldHideOre(worldSeed, cx, cz, wx, worldOriginY + y, wz)) {
                    section.set(x, y, z, stoneId);
                    oreMasked++;
                  }
                }
              }
            }
            if (oreMasked > 0) {
              touched = true;
              final int masked = oreMasked;
              LogData.get().fine(() -> "[ore] masked " + masked + " ores in " + cx + "," + cz);
            }
          }
        }
      }

      int spawnerFloorId = ids.floorId();
      int spawnerMasked = 0;
      for (int x = 0; x < 16; x++) {
        for (int z = 0; z < 16; z++) {
          for (int y = Math.max(0, floorY - worldOriginY); y < 16; y++) {
            if (section.getBlockId(x, y, z) == ids.spawnerId()) {
              section.set(x, y, z, spawnerFloorId);
              spawnerMasked++;
            }
          }
        }
      }
      if (spawnerMasked > 0) {
        touched = true;
      }
    }

    if (touched || nullSections > 0) {
      if (!lowerRevealed) {
        try {
          LightDebugProtection.stripFloorLight(wrapper, minSection, floorSection);
        } catch (Exception e) {
          LogData.get().warning("[deepslate] light strip failed at " + cx + "," + cz + ": " + e);
        }
      }
      if (nullSections > 0) {
        int py = player.getLocation().getBlockY();
        LogData.get().warning("[deepslate] " + player.getName()
            + " chunk=" + cx + "," + cz + " NULL sections below floor=" + nullSections
            + " playerY=" + py);
      }
    }
    return touched || nullSections > 0;
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

  public boolean shouldMaskMultiBlock(int by, boolean lowerRevealed, boolean upperRevealed) {
    int floorY = rm.hideBelowY();
    if (by < floorY) return !lowerRevealed;
    int upperY = rm.upperBarrierY();
    return upperY > floorY && by < upperY && !upperRevealed;
  }

  private void fillNoise(
      BaseChunk section, int worldOriginX, int worldOriginY, int worldOriginZ,
      int lowY, int highY, int salt, Player player,
      boolean geodeChunk, int[] geodeBudget) {
    java.util.UUID playerId = player.getUniqueId();
    for (int x = 0; x < 16; x++) {
      int wx = worldOriginX + x;
      for (int z = 0; z < 16; z++) {
        int wz = worldOriginZ + z;
        for (int y = lowY; y < highY; y++) {
          int wy = worldOriginY + y;
          if (rm.hasGhostBlockAt(playerId, wx, wy, wz)) {
            continue;
          }
          int geodeBlock = 0;
          if (geodeChunk && geodeBudget[0] > 0) {
            int h = geodeNoise(wx, wy, wz, salt);
            if ((h & 0x7FFFFFFF) % 64 == 0) {
              geodeBlock = fakeAmethystVariant(h);
              geodeBudget[0]--;
            }
          }
          if (geodeBlock == 0) {
            geodeBlock = ids.floorIdAt(salt, wx, wy, wz);
          }
          if (section.getBlockId(x, y, z) != geodeBlock) {
            section.set(x, y, z, geodeBlock);
          }
        }
      }
    }
  }

  private int fakeAmethystVariant(int h) {
    int r = (h & 0x7FFFFFFF) % 12;
    if (r == 0) return ids.buddingAmethystId();
    if (r == 1) return ids.decoyClusterId();
    if (r == 2) return ids.smallBudId();
    if (r == 3) return ids.mediumBudId();
    if (r == 4) return ids.largeBudId();
    return ids.amethystBlockId();
  }

  private static int geodeNoise(int x, int y, int z, int salt) {
    int h = (x * 374761393 ^ z * 668265263 ^ y * 1274126177) + salt;
    h = (h ^ (h >> 13)) * 1274126177;
    h = h ^ (h >> 16);
    return h;
  }

  public int floorId() {
    return ids.floorId();
  }

  public boolean isSpawner(int id) {
    return ids.isSpawner(id);
  }

  public int spawnerId() {
    return ids.spawnerId();
  }

  public int floorIdAt(int salt, int x, int y, int z) {
    return ids.floorIdAt(salt, x, y, z);
  }

  public boolean isWrapperRelevant(WrapperPlayServerMultiBlockChange wrapper) {
    return wrapper != null && wrapper.getChunkPosition() != null && wrapper.getBlocks() != null;
  }

  private static boolean shouldHideOresInChunk(long worldSeed, int chunkX, int chunkZ) {
    long mixed = worldSeed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L);
    int hash = (int)(mixed ^ (mixed >>> 32));
    return (hash & 0x7FFFFFFF) % 100 < 65;
  }

  private static boolean shouldHideOre(long worldSeed, int chunkX, int chunkZ, int x, int y, int z) {
    long mixed = worldSeed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L);
    mixed ^= (long) x * 724379273L + (long) y * 537182317L + (long) z * 918273647L;
    mixed ^= (mixed >>> 31);
    int hash = (int)(mixed ^ (mixed >>> 32));
    return (hash & 0x7FFFFFFF) % 100 < 85;
  }
}
