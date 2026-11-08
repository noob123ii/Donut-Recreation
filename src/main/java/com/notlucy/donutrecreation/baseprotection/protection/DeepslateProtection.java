/*
 * Copyright (C) 2026 Notlucy and Donut Recreation contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.notlucy.donutrecreation.baseprotection.protection;

import org.bukkit.HeightMap;
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
    boolean geodeChunk = Math.abs(geodeNoise(chunkX, 999, chunkZ, salt) % 100) <= 8;
    int[] geodeBudget = {revealManager.fakeGeodeAmethystPerChunk()};
    boolean lowerRevealed = revealManager.isChunkRevealed(player, chunkX, chunkZ);
    LongOpenHashSet visibleCave = revealManager.visibleCaveSet(player, chunkX, chunkZ);
    boolean upperRevealed = revealManager.isBarrierBandRevealed(player, chunkX, chunkZ);
    boolean upperActive = upperY > floorY;
    int spawnerMaskAboveY = revealManager.spawnerMaskAboveY();
    int spawnerStoneId = blockIds.stoneId();
    boolean scanSpawners = spawnerMaskAboveY < player.getWorld().getMaxHeight() - 1;
    boolean surfaceLocked = revealManager.isSurfaceScrambleNear(player, chunkX, chunkZ);
    int surfaceTop = Math.min(player.getWorld().getMaxHeight(),
        revealManager.surfaceScrambleMaxY());

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
      int fromY = Math.max(0, floorY - worldOriginY);
      int toY = Math.min(16, upperY - worldOriginY);
      if (upperActive && !upperRevealed && toY > fromY) {
        fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, fromY, toY,
            salt, player, geodeChunk, geodeBudget, scanSpawners, visibleCave);
        touched = true;
        modifiedSections++;
      }
      if (surfaceLocked) {
        /* Underground surface lock: replace the top terrain layer and clear blocks above it.
         * Terrain below the heightmap is left intact so the area stays usable and cheap to render. */
        int sFromY = Math.max(0, revealManager.surfaceScrambleStartY() - worldOriginY);
        int sToY = Math.min(16, surfaceTop - worldOriginY);
        if (sToY > sFromY) {
          fillSurfaceMask(section, worldOriginX, worldOriginY, worldOriginZ, sFromY, sToY,
              salt, player, sections, minSection);
          touched = true;
          modifiedSections++;
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

    if (surfaceLocked) {
      revealManager.recordSurfaceMaskedChunk(player, chunkX, chunkZ);
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
        if (surfaceLocked) {
          LightDebugProtection.stripSectionRange(
              light, Math.max(minSection, revealManager.surfaceScrambleStartY() >> 4),
              (surfaceTop - 1) >> 4, minSection);
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
    var env = player.getWorld().getEnvironment();
    if (env == org.bukkit.World.Environment.THE_END || env == org.bukkit.World.Environment.NETHER) {
      return false;
    }
    int floorY = revealManager.hideBelowY();
    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    int salt = revealManager.getPlayerSalt(player.getUniqueId());
    int surfaceTop = Math.min(player.getWorld().getMaxHeight(), revealManager.surfaceScrambleMaxY());
    if (revealManager.isSurfaceScrambleNear(player, chunkX, chunkZ)
        && y >= revealManager.surfaceScrambleStartY()
        && y < surfaceTop) {
      if (revealManager.hasGhostBlockAt(player.getUniqueId(), x, y, z)) {
        return false;
      }
      int flatY = chunkFlatYFromWorld(player.getWorld(), chunkX, chunkZ, salt);
      if (y > flatY) {
        wrapper.setBlockState(WrappedBlockState.getByGlobalId(blockIds.airId()));
        return true;
      }
      if (y == flatY) {
        wrapper.setBlockState(WrappedBlockState.getByGlobalId(blockIds.stoneId()));
        return true;
      }
      return false;
    }
    if (y < floorY) {
      if (revealManager.isCaveBlockVisible(player, x, y, z)
          || revealManager.isWithinRevealRange(player, x, y, z, 10)
          || revealManager.isChunkRevealed(player, chunkX, chunkZ)) {
        return false;
      }
      wrapper.setBlockState(WrappedBlockState.getByGlobalId(blockIds.floorIdAt(salt, x, y, z)));
      return true;
    }
    int upperY = revealManager.upperBarrierY();
    if (upperY > floorY && y < upperY) {
      if (revealManager.isCaveBlockVisible(player, x, y, z)
          || revealManager.isWithinRevealRange(player, x, y, z, 10)
          || revealManager.isBarrierBandRevealed(player, chunkX, chunkZ)) {
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

  private void fillSurfaceMask(
      BaseChunk section, int worldOriginX, int worldOriginY, int worldOriginZ,
      int lowY, int highY, int salt, Player player,
      BaseChunk[] allSections, int minSection) {
    java.util.UUID playerId = player.getUniqueId();
    boolean checkGhosts = revealManager.hasAnyGhostBlocks(playerId);
    int stoneId = blockIds.stoneId();
    int airId = blockIds.airId();
    org.bukkit.World world = player.getWorld();
    int bandStart = Math.max(revealManager.surfaceScrambleStartY(), world.getMinHeight());
    int bandEnd = Math.min(world.getMaxHeight(), revealManager.surfaceScrambleMaxY());
    if (bandEnd <= bandStart) {
      return;
    }
    int chunkX = worldOriginX >> 4;
    int chunkZ = worldOriginZ >> 4;
    int flatY = chunkFlatYFromPacket(allSections, minSection, chunkX, chunkZ, salt,
        bandStart, bandEnd - 1);
    for (int x = 0; x < 16; x++) {
      int worldX = worldOriginX + x;
      for (int z = 0; z < 16; z++) {
        int worldZ = worldOriginZ + z;
        for (int y = lowY; y < highY; y++) {
          int worldY = worldOriginY + y;
          if (checkGhosts && revealManager.hasGhostBlockAt(playerId, worldX, worldY, worldZ)) {
            continue;
          }
          if (worldY > flatY) {
            if (section.getBlockId(x, y, z) != airId) {
              section.set(x, y, z, airId);
            }
          } else if (worldY == flatY) {
            section.set(x, y, z, stoneId);
          }
        }
      }
    }
  }

  private int chunkFlatYFromPacket(BaseChunk[] sections, int minSection, int chunkX, int chunkZ,
      int salt, int worldMin, int worldMax) {
    int mix = sampleMix(chunkX, chunkZ, salt);
    int sx = mix & 15;
    int sz = (mix >>> 4) & 15;
    return findTerrainHeightmap(sections, minSection, sx, sz, worldMin, worldMax);
  }

  private int chunkFlatYFromWorld(org.bukkit.World world, int chunkX, int chunkZ, int salt) {
    int mix = sampleMix(chunkX, chunkZ, salt);
    int sx = mix & 15;
    int sz = (mix >>> 4) & 15;
    int x = (chunkX << 4) + sx;
    int z = (chunkZ << 4) + sz;
    try {
      if (world.isChunkLoaded(chunkX, chunkZ)) {
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
      }
    } catch (Throwable ignored) {
      try {
        if (world.isChunkLoaded(chunkX, chunkZ)) {
          return world.getHighestBlockYAt(x, z);
        }
      } catch (Throwable ignored2) {
      }
    }
    return Math.max(world.getMinHeight(), revealManager.surfaceScrambleStartY());
  }

  private static int sampleMix(int chunkX, int chunkZ, int salt) {
    int hash = (chunkX * 73428767 ^ chunkZ * 912367) + (salt * 0x45D9F3B);
    hash ^= (hash >>> 16);
    hash *= 0x45D9F3B;
    hash ^= (hash >>> 16);
    return hash;
  }

  private int findTerrainHeightmap(
      BaseChunk[] sections, int minSection, int localX, int localZ,
      int worldMin, int worldMax) {
    for (int worldY = worldMax; worldY >= worldMin; worldY--) {
      int sectionIndex = (worldY >> 4) - minSection;
      if (sectionIndex < 0 || sectionIndex >= sections.length) {
        continue;
      }
      BaseChunk section = sections[sectionIndex];
      if (section == null) {
        /* Unknown; assume solid to avoid hiding terrain. */
        return worldY;
      }
      int id = section.getBlockId(localX, worldY & 15, localZ);
      if (isTerrainForHeightmap(id)) {
        return worldY;
      }
    }
    return worldMin;
  }

  private boolean isTerrainForHeightmap(int globalId) {
    if (globalId == blockIds.airId()) {
      return false;
    }
    try {
      WrappedBlockState state = WrappedBlockState.getByGlobalId(
          blockIds.version(), globalId, false);
      if (state == null || state.getType() == null) {
        return false;
      }
      String name = state.getType().getName();
      if (name == null) {
        return false;
      }
      if ("air".equals(name) || "cave_air".equals(name) || "void_air".equals(name)) {
        return false;
      }
      if (name.contains("leaves")) {
        return false;
      }
      if (name.endsWith("_log") || name.endsWith("_wood") || name.equals("log")
          || name.equals("wood") || name.contains("stem") || name.contains("hyphae")
          || name.equals("mangrove_roots") || name.equals("muddy_mangrove_roots")) {
        return false;
      }
      if (name.contains("vine") || name.contains("sapling") || name.contains("propagule")
          || name.contains("azalea") && !name.contains("azalea_leaves")
          || name.equals("bee_nest") || name.equals("beehive")
          || name.equals("mushroom_stem") || name.contains("mushroom_block")
          || name.equals("cocoa")) {
        return false;
      }
      return true;
    } catch (Throwable ignored) {
      return true;
    }
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
          if (revealManager.isWithinRevealRange(player, worldX, worldY, worldZ, 10)) {
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

  public int airId() {
    return blockIds.airId();
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

}
