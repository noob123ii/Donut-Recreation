package com.notlucy.donutrecreation.baseprotection.protection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

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
    boolean lowerRevealed = rm.isRevealed(player, cx, cz);
    boolean upperRevealed = rm.isUpperRevealed(player, cx, cz);
    boolean upperActive = upperY > floorY;

    boolean touched = false;
    boolean tilesChanged = false;
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
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, 0, hi, salt, player);
          touched = true;
          modifiedSections++;
        }
      }
      if (upperActive && !upperRevealed) {
        int lo = Math.max(0, floorY - worldOriginY);
        int hi = Math.min(16, upperY - worldOriginY);
        if (hi > lo) {
          fillNoise(section, worldOriginX, worldOriginY, worldOriginZ, lo, hi, salt, player);
          touched = true;
          modifiedSections++;
        }
      }

      if ((lowerRevealed || upperRevealed) && env == org.bukkit.World.Environment.NORMAL) {
        int lo = Math.max(0, floorY - worldOriginY);
        int hi = Math.min(16, upperY - worldOriginY);
        if (hi > lo) {
          long worldSeed = player.getWorld().getSeed();
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

      tilesChanged = handleTiles(column, sections, minSection, cx, cz,
        player, lowerRevealed, upperRevealed);
    }

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
    var env = player.getWorld().getEnvironment();
    boolean isNether = env == org.bukkit.World.Environment.NETHER;
    boolean isOverworld = env == org.bukkit.World.Environment.NORMAL;
    int floorY = rm.hideBelowY();
    int upperY = rm.upperBarrierY();
    Location playerLoc = player.getLocation();
    for (var enc : wrapper.getBlocks()) {
      int wx = enc.getX();
      int wy = enc.getY();
      int wz = enc.getZ();
      if (!tiles.contains(RevealManager.packPos(wx, wy, wz))) {
        continue;
      }

      if ((isNether || (isOverworld && wy >= floorY && wy < upperY))) {
        double dist = Math.sqrt(
            Math.pow(wx - playerLoc.getX(), 2) +
            Math.pow(wy - playerLoc.getY(), 2) +
            Math.pow(wz - playerLoc.getZ(), 2)
        );
        if (dist > 50) {

          if (enc.getBlockId() != airId) {
            enc.setBlockId(airId);
            swaps++;
          }
          continue;
        }
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
      int lowY, int highY, int salt, Player player) {
    java.util.UUID playerId = player.getUniqueId();
    long worldSeed = rm.plugin().getServer().getWorlds().get(0).getSeed();
    for (int x = 0; x < 16; x++) {
      int wx = worldOriginX + x;
      for (int z = 0; z < 16; z++) {
        int wz = worldOriginZ + z;
        for (int y = lowY; y < highY; y++) {
          int wy = worldOriginY + y;
          if (rm.hasGhostBlockAt(playerId, wx, wy, wz)) {
            continue;
          }
          int geodeBlock = fakeGeodeAt(worldSeed, salt, wx, wy, wz);
          if (geodeBlock != 0) {
            if (section.getBlockId(x, y, z) != geodeBlock) {
              section.set(x, y, z, geodeBlock);
            }
          } else {
            int target = ids.floorIdAt(salt, wx, wy, wz);
            if (section.getBlockId(x, y, z) != target) {
              section.set(x, y, z, target);
            }
          }
        }
      }
    }
  }

  private int fakeGeodeAt(long worldSeed, int salt, int wx, int wy, int wz) {
    if (wy > 5) return 0;

    int chunkX = wx >> 4;
    int chunkZ = wz >> 4;
    int chance = Math.abs(geodeNoise(chunkX, 999, chunkZ, salt) % 100);
    if (chance > 8) return 0;

    int gx = chunkX * 32 + Math.abs(geodeNoise(chunkX, 0, chunkZ, salt) % 32);
    int gz = chunkZ * 32 + Math.abs(geodeNoise(chunkX, 1, chunkZ, salt) % 32);
    int gy = -40 + Math.abs(geodeNoise(chunkX, 2, chunkZ, salt) % 50);

    int h0 = geodeNoise(gx, 100, gz, salt);
    int h1 = geodeNoise(gx, 101, gz, salt);
    int h2 = geodeNoise(gx, 102, gz, salt);
    int h3 = geodeNoise(gx, 103, gz, salt);

    double outerWallDist = 3.5 + (h0 & 0x3) * 0.2;
    double outerDistSum = 0;
    for (int p = 0; p < 4; p++) {
      double px = gx + ((geodeNoise(gx, p * 3, gz, salt) & 0xF) - 8) * 0.35;
      double py = gy + ((geodeNoise(gx, p * 3 + 1, gz, salt) & 0xF) - 8) * 0.3;
      double pz = gz + ((geodeNoise(gx, p * 3 + 2, gz, salt) & 0xF) - 8) * 0.35;
      double ddx = wx - px;
      double ddy = wy - py;
      double ddz = wz - pz;
      double distSq = ddx * ddx + ddy * ddy + ddz * ddz;
      outerDistSum += fastInvSqrt(distSq + 1.0);
    }
    double noiseVal = pseudoPerlin(wx, wy, wz, salt) * 0.04;
    double outerDist = (outerDistSum + noiseVal) / outerWallDist;
    if (outerDist > 1.0) {
      return 0;
    }

    double calciteThickness = 2.8 + (h1 & 0x3) * 0.3;
    double amethystThickness = 1.8 + (h2 & 0x3) * 0.2;
    double fillingThickness = 1.2 + (h3 & 0x3) * 0.15;

    double calciteThreshold = 1.0 / Math.sqrt(calciteThickness);
    double amethystThreshold = 1.0 / Math.sqrt(amethystThickness);
    double fillingThreshold = 1.0 / Math.sqrt(fillingThickness);

    if (outerDist > calciteThreshold) {
      return ids.smoothBasaltId();
    }
    if (outerDist > amethystThreshold) {
      return ids.calciteId();
    }

    int h = geodeNoise(wx, wy, wz, salt);
    if (outerDist > fillingThreshold) {
      if ((h & 0x7FFFFFFF) % 12 == 0) {
        return ids.buddingAmethystId();
      }
      return ids.amethystBlockId();
    }

    if (outerDist > fillingThreshold * 0.75 && (h & 0x7FFFFFFF) % 10 == 0) {
      return ids.decoyClusterId();
    }

    int coreHash = (h & 0x7FFFFFFF) % 20;
    if (coreHash == 0) return ids.largeBudId();
    if (coreHash == 1) return ids.mediumBudId();
    if (coreHash == 2) return ids.smallBudId();
    if (coreHash == 3) return ids.buddingAmethystId();
    if (coreHash < 7) return ids.amethystBlockId();
    return ids.floorId();
  }

  private static double fastInvSqrt(double x) {
    if (x <= 0) return 0;
    double half = 0.5 * x;
    long bits = Double.doubleToLongBits(x);
    bits = 0x5FE6EB50C7B537AAL - (bits >> 1);
    double guess = Double.longBitsToDouble(bits);
    guess *= (1.5 - half * guess * guess);
    return guess;
  }

  private static double pseudoPerlin(int x, int y, int z, int salt) {
    int h1 = geodeNoise(x, y, z, salt);
    int h2 = geodeNoise(x + 1, y, z, salt);
    int h3 = geodeNoise(x, y + 1, z, salt);
    int h4 = geodeNoise(x, y, z + 1, salt);
    int h5 = geodeNoise(x + 1, y + 1, z, salt);
    int h6 = geodeNoise(x + 1, y, z + 1, salt);
    int h7 = geodeNoise(x, y + 1, z + 1, salt);
    int h8 = geodeNoise(x + 1, y + 1, z + 1, salt);
    double fx = (x & 1) == 0 ? 0.3 : 0.7;
    double fy = (y & 1) == 0 ? 0.3 : 0.7;
    double fz = (z & 1) == 0 ? 0.3 : 0.7;
    double v000 = (h1 & 0xFFFF) / 65535.0;
    double v100 = (h2 & 0xFFFF) / 65535.0;
    double v010 = (h3 & 0xFFFF) / 65535.0;
    double v001 = (h4 & 0xFFFF) / 65535.0;
    double v110 = (h5 & 0xFFFF) / 65535.0;
    double v101 = (h6 & 0xFFFF) / 65535.0;
    double v011 = (h7 & 0xFFFF) / 65535.0;
    double v111 = (h8 & 0xFFFF) / 65535.0;
    double x0 = v000 * (1 - fx) + v100 * fx;
    double x1 = v001 * (1 - fx) + v101 * fx;
    double y0 = v010 * (1 - fx) + v110 * fx;
    double y1 = v011 * (1 - fx) + v111 * fx;
    double z0 = x0 * (1 - fy) + y0 * fy;
    double z1 = x1 * (1 - fy) + y1 * fy;
    return z0 * (1 - fz) + z1 * fz;
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
