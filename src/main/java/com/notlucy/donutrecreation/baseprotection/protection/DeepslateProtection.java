package com.notlucy.donutrecreation.baseprotection.protection;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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

  private final RevealManager revealManager;
  private final BlockIdRegistry blockIds;

  public DeepslateProtection(RevealManager revealManager, BlockIdRegistry blockIds) {
    this.revealManager = revealManager;
    this.blockIds = blockIds;
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

    int chunkX = column.getX();
    int chunkZ = column.getZ();
    int floorY = revealManager.hideBelowY();
    int upperY = revealManager.upperBarrierY();
    int floorSection = floorY >> 4;
    int minSection = revealManager.worldMinY() >> 4;
    int worldOriginX = chunkX << 4;
    int worldOriginZ = chunkZ << 4;
    int salt = revealManager.getPlayerSalt(player.getUniqueId());
    long worldSeed = player.getWorld().getSeed();
    boolean geodeChunk = Math.abs(geodeNoise(chunkX, 999, chunkZ, salt) % 100) <= 8;
    int[] geodeBudget = {revealManager.fakeGeodeAmethystPerChunk()};
    boolean lowerRevealed = revealManager.isChunkRevealed(player, chunkX, chunkZ);
    LongOpenHashSet visibleCave = revealManager.visibleCaveSet(player, chunkX, chunkZ);
    boolean upperRevealed = revealManager.isBarrierBandRevealed(player, chunkX, chunkZ);
    boolean upperActive = upperY > floorY;
    int spawnerMaskAboveY = revealManager.spawnerMaskAboveY();
    int spawnerStoneId = blockIds.stoneId();
    boolean scanSpawners = spawnerMaskAboveY < player.getWorld().getMaxHeight() - 1;

    boolean touched = false;
    int modifiedSections = 0;
    int nullSections = 0;
    for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
      BaseChunk section = sections[sectionIndex];
      int sectionY = minSection + sectionIndex;
      if (section == null) {
        if (!lowerRevealed && sectionY <= floorSection) {
          nullSections++;
        }
        continue;
      }
      int worldOriginY = sectionY << 4;

      if (!lowerRevealed) {
        int toY = Math.min(16, floorY - worldOriginY);
        if (toY > 0) {
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, 0, toY,
              salt, player, geodeChunk, geodeBudget, scanSpawners, visibleCave);
          touched = true;
          modifiedSections++;
        }
      }
      if (upperActive && !upperRevealed) {
        int fromY = Math.max(0, floorY - worldOriginY);
        int toY = Math.min(16, upperY - worldOriginY);
        if (toY > fromY) {
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, fromY, toY,
              salt, player, geodeChunk, geodeBudget, scanSpawners, visibleCave);
          touched = true;
          modifiedSections++;
        }
      }

      if ((lowerRevealed || upperRevealed) && env == org.bukkit.World.Environment.NORMAL) {
        int fromY = Math.max(0, floorY - worldOriginY);
        int toY = Math.min(16, upperY - worldOriginY);
        if (toY > fromY) {
          if (shouldHideOresInChunk(worldSeed, chunkX, chunkZ)) {
            int stoneId = blockIds.stoneId();
            int oreMasked = 0;
            for (int x = 0; x < 16; x++) {
              int worldX = worldOriginX + x;
              for (int z = 0; z < 16; z++) {
                int worldZ = worldOriginZ + z;
                for (int y = fromY; y < toY; y++) {
                  int blockId = section.getBlockId(x, y, z);
                  if (blockIds.isOre(blockId) && shouldHideOre(worldSeed, chunkX, chunkZ, worldX, worldOriginY + y, worldZ)) {
                    section.set(x, y, z, stoneId);
                    oreMasked++;
                  }
                }
              }
            }
            if (oreMasked > 0) {
              touched = true;
              final int masked = oreMasked;
              LogData.get().fine(() -> "[ore] masked " + masked + " ores in " + chunkX + "," + chunkZ);
            }
          }
        }
      }

      if (scanSpawners && worldOriginY + 15 > spawnerMaskAboveY) {
        int spawnerMasked = 0;
        for (int x = 0; x < 16; x++) {
          for (int z = 0; z < 16; z++) {
            for (int y = 0; y < 16; y++) {
              if (section.getBlockId(x, y, z) != blockIds.spawnerId()) {
                continue;
              }
              int worldY = worldOriginY + y;
              if (worldY > spawnerMaskAboveY
                  && !isNearSpawner(player, worldOriginX + x, worldY, worldOriginZ + z)) {
                section.set(x, y, z, spawnerStoneId);
                spawnerMasked++;
              }
            }
          }
        }
        if (spawnerMasked > 0) {
          touched = true;
        }
      }
    }

    if (touched || nullSections > 0) {
      try {
        var light = wrapper.getLightData();
        if (!lowerRevealed) {
          LightDebugProtection.stripFloorLight(light, minSection, (floorY - 1) >> 4);
        }
        if (upperActive && !upperRevealed) {
          LightDebugProtection.stripSectionRange(
              light, Math.max(minSection, floorSection), (upperY - 1) >> 4, minSection);
        }
      } catch (Exception e) {
        LogData.get().warning("[deepslate] light strip failed at " + chunkX + "," + chunkZ + ": " + e);
      }
      if (nullSections > 0) {
        int playerY = player.getLocation().getBlockY();
        LogData.get().warning("[deepslate] " + player.getName()
            + " chunk=" + chunkX + "," + chunkZ + " NULL sections below floor=" + nullSections
            + " playerY=" + playerY);
      }
    }
    return touched || nullSections > 0;
  }

  public boolean rewriteBlockChange(
      WrapperPlayServerBlockChange wrapper, Player player, int x, int y, int z) {
    int floorY = revealManager.hideBelowY();
    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    int salt = revealManager.getPlayerSalt(player.getUniqueId());
    if (y < floorY) {
      if (revealManager.isCaveBlockVisible(player, x, y, z)
          || revealManager.isChunkRevealed(player, chunkX, chunkZ)) {
        return false;
      }
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(blockIds.floorIdAt(salt, x, y, z)));
      return true;
    }
    int upperY = revealManager.upperBarrierY();
    if (upperY > floorY && y < upperY) {
      if (revealManager.isBarrierBandRevealed(player, chunkX, chunkZ)) {
        return false;
      }
      var state = wrapper.getBlockState();
      if (state != null && blockIds.isSpawner(state.getGlobalId())) {
        return false;
      }
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(blockIds.floorIdAt(salt, x, y, z)));
      return true;
    }
    return false;
  }

  public boolean shouldMaskMultiBlock(int blockY, boolean lowerRevealed, boolean upperRevealed) {
    int floorY = revealManager.hideBelowY();
    if (blockY < floorY) return !lowerRevealed;
    int upperY = revealManager.upperBarrierY();
    return upperY > floorY && blockY < upperY && !upperRevealed;
  }

  private void fillNoise(
      BaseChunk section, int worldOriginX, int worldOriginY, int worldOriginZ,
      int lowY, int highY, int salt, Player player,
      boolean geodeChunk, int[] geodeBudget, boolean preserveSpawners, LongOpenHashSet visibleCave) {
    java.util.UUID playerId = player.getUniqueId();
    boolean checkGhosts = revealManager.hasAnyGhostBlocks(playerId);
    for (int x = 0; x < 16; x++) {
      int worldX = worldOriginX + x;
      for (int z = 0; z < 16; z++) {
        int worldZ = worldOriginZ + z;
        for (int y = lowY; y < highY; y++) {
          int worldY = worldOriginY + y;
          if (checkGhosts && revealManager.hasGhostBlockAt(playerId, worldX, worldY, worldZ)) {
            continue;
          }
          if (visibleCave != null
              && visibleCave.contains(RevealManager.packPos(worldX, worldY, worldZ))) {
            continue;
          }
          if (preserveSpawners && section.getBlockId(x, y, z) == blockIds.spawnerId()) {
            continue;
          }
          int geodeBlock = 0;
          if (geodeChunk && geodeBudget[0] > 0) {
            int hash = geodeNoise(worldX, worldY, worldZ, salt);
            if ((hash & 0x7FFFFFFF) % 64 == 0) {
              geodeBlock = fakeAmethystVariant(hash);
              geodeBudget[0]--;
            }
          }
          if (geodeBlock == 0) {
            geodeBlock = blockIds.floorIdAt(salt, worldX, worldY, worldZ);
          }
          section.set(x, y, z, geodeBlock);
        }
      }
    }
  }

  private int fakeAmethystVariant(int hash) {
    int variant = (hash & 0x7FFFFFFF) % 12;
    if (variant == 0) return blockIds.buddingAmethystId();
    if (variant == 1) return blockIds.decoyClusterId();
    if (variant == 2) return blockIds.smallBudId();
    if (variant == 3) return blockIds.mediumBudId();
    if (variant == 4) return blockIds.largeBudId();
    return blockIds.amethystBlockId();
  }

  private static int geodeNoise(int x, int y, int z, int salt) {
    int hash = (x * 374761393 ^ z * 668265263 ^ y * 1274126177) + salt;
    hash = (hash ^ (hash >> 13)) * 1274126177;
    hash = hash ^ (hash >> 16);
    return hash;
  }

  public int floorId() {
    return blockIds.floorId();
  }

  public boolean isSpawner(int blockId) {
    return blockIds.isSpawner(blockId);
  }

  public int spawnerId() {
    return blockIds.spawnerId();
  }

  public int floorIdAt(int salt, int x, int y, int z) {
    return blockIds.floorIdAt(salt, x, y, z);
  }

  public int stoneId() {
    return blockIds.stoneId();
  }

  public boolean isNearSpawner(Player player, int x, int y, int z) {
    org.bukkit.Location location = player.getLocation();
    long offsetX = location.getBlockX() - x;
    long offsetY = location.getBlockY() - y;
    long offsetZ = location.getBlockZ() - z;
    return offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ <= revealManager.spawnerRevealDistanceSq();
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
