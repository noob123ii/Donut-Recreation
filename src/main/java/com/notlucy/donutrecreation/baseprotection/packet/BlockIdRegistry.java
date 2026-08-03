package com.notlucy.donutrecreation.baseprotection.packet;

import java.util.BitSet;
import java.util.Set;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class BlockIdRegistry {

  private static final Set<StateType> AMETHYST_FAMILY = Set.of(
      StateTypes.AMETHYST_BLOCK,
      StateTypes.BUDDING_AMETHYST,
      StateTypes.AMETHYST_CLUSTER,
      StateTypes.SMALL_AMETHYST_BUD,
      StateTypes.MEDIUM_AMETHYST_BUD,
      StateTypes.LARGE_AMETHYST_BUD,
      StateTypes.CALCITE,
      StateTypes.SMOOTH_BASALT);

  private static final Set<StateType> ORE_FAMILY = Set.of(
      StateTypes.COAL_ORE,
      StateTypes.DEEPSLATE_COAL_ORE,
      StateTypes.IRON_ORE,
      StateTypes.DEEPSLATE_IRON_ORE,
      StateTypes.GOLD_ORE,
      StateTypes.DEEPSLATE_GOLD_ORE,
      StateTypes.COPPER_ORE,
      StateTypes.DEEPSLATE_COPPER_ORE,
      StateTypes.REDSTONE_ORE,
      StateTypes.DEEPSLATE_REDSTONE_ORE,
      StateTypes.LAPIS_ORE,
      StateTypes.DEEPSLATE_LAPIS_ORE,
      StateTypes.DIAMOND_ORE,
      StateTypes.DEEPSLATE_DIAMOND_ORE,
      StateTypes.EMERALD_ORE,
      StateTypes.DEEPSLATE_EMERALD_ORE,
      StateTypes.ANCIENT_DEBRIS,
      StateTypes.NETHER_GOLD_ORE,
      StateTypes.NETHER_QUARTZ_ORE,
      StateTypes.GILDED_BLACKSTONE,
      StateTypes.RAW_IRON_BLOCK,
      StateTypes.RAW_COPPER_BLOCK,
      StateTypes.RAW_GOLD_BLOCK);

  private BitSet amethyst;
  private int amethystLen;
  private BitSet ores;
  private int oresLen;
  private BitSet transparentBlocks;
  private int transparentLen;
  private BitSet transparentBlocksSkipLava;
  private int transparentSkipLavaLen;
  private int stoneId;
  private int netherrackId;
  private int airId;
  private int floorId;
  private int spawnerId;
  private int decoyClusterId;
  private int amethystBlockId;
  private int calciteId;
  private int smoothBasaltId;
  private int mediumBudId;
  private int largeBudId;
  private int smallBudId;
  private int buddingAmethystId;
  private int[] floorPalette;
  private ClientVersion version;

  public void rebuild(ClientVersion version) {
    this.version = version;
    this.stoneId = WrappedBlockState.getDefaultState(version, StateTypes.STONE).getGlobalId();
    this.netherrackId = WrappedBlockState.getDefaultState(version, StateTypes.NETHERRACK).getGlobalId();
    this.airId = WrappedBlockState.getDefaultState(version, StateTypes.AIR).getGlobalId();
    this.floorId = WrappedBlockState.getDefaultState(version, StateTypes.DEEPSLATE).getGlobalId();
    this.spawnerId = WrappedBlockState.getDefaultState(version, StateTypes.SPAWNER).getGlobalId();
    this.decoyClusterId = WrappedBlockState
        .getDefaultState(version, StateTypes.AMETHYST_CLUSTER).getGlobalId();
    this.amethystBlockId = WrappedBlockState
        .getDefaultState(version, StateTypes.AMETHYST_BLOCK).getGlobalId();
    this.calciteId = WrappedBlockState
        .getDefaultState(version, StateTypes.CALCITE).getGlobalId();
    this.smoothBasaltId = WrappedBlockState
        .getDefaultState(version, StateTypes.SMOOTH_BASALT).getGlobalId();
    this.mediumBudId = WrappedBlockState
        .getDefaultState(version, StateTypes.MEDIUM_AMETHYST_BUD).getGlobalId();
    this.largeBudId = WrappedBlockState
        .getDefaultState(version, StateTypes.LARGE_AMETHYST_BUD).getGlobalId();
    this.smallBudId = WrappedBlockState
        .getDefaultState(version, StateTypes.SMALL_AMETHYST_BUD).getGlobalId();
    this.buddingAmethystId = WrappedBlockState
        .getDefaultState(version, StateTypes.BUDDING_AMETHYST).getGlobalId();
    this.floorPalette = buildFloorPalette(version);

    BitSet amethystBits = new BitSet(8192);
    BitSet oreBits = new BitSet(8192);
    BitSet transBits = new BitSet(8192);
    BitSet transBitsSkipLava = new BitSet(8192);
    for (int id = 0; id < 32768; id++) {
      WrappedBlockState s = WrappedBlockState.getByGlobalId(version, id, false);
      if (s == null) {
        continue;
      }
      StateType t = s.getType();
      if (t != null) {
        if (AMETHYST_FAMILY.contains(t)) {
          amethystBits.set(id);
        }
        if (ORE_FAMILY.contains(t)) {
          oreBits.set(id);
        }
        boolean isTrans = t == StateTypes.AIR || t == StateTypes.CAVE_AIR || t == StateTypes.VOID_AIR
            || t == StateTypes.WATER
            || t == StateTypes.GLASS || t == StateTypes.TINTED_GLASS
            || t == StateTypes.BARRIER || t == StateTypes.LIGHT || t == StateTypes.SEA_LANTERN
            || t == StateTypes.SLIME_BLOCK || t == StateTypes.HONEY_BLOCK
            || t == StateTypes.FROSTED_ICE || t == StateTypes.POWDER_SNOW
            || t == StateTypes.END_ROD || t == StateTypes.CHAIN || t == StateTypes.COBWEB;
        if (isTrans) {
          transBits.set(id);
          transBitsSkipLava.set(id);
        }
        if (t == StateTypes.LAVA) {
          transBits.set(id);
        }
      }
    }
    this.amethyst = amethystBits;
    this.amethystLen = amethystBits.length();
    this.ores = oreBits;
    this.oresLen = oreBits.length();
    this.transparentBlocks = transBits;
    this.transparentLen = transBits.length();
    this.transparentBlocksSkipLava = transBitsSkipLava;
    this.transparentSkipLavaLen = transBitsSkipLava.length();
  }

  public boolean isAmethyst(int id) {
    BitSet bits = amethyst;
    return bits != null && id >= 0 && id < amethystLen && bits.get(id);
  }

  public int amethystCount() {
    return amethyst == null ? 0 : amethyst.cardinality();
  }

  public boolean isOre(int id) {
    BitSet bits = ores;
    return bits != null && id >= 0 && id < oresLen && bits.get(id);
  }

  public boolean isTransparent(int id, boolean skipLava) {
    BitSet bits = skipLava ? transparentBlocksSkipLava : transparentBlocks;
    int len = skipLava ? transparentSkipLavaLen : transparentLen;
    return bits != null && id >= 0 && id < len && bits.get(id);
  }

  public int oreCount() {
    return ores == null ? 0 : ores.cardinality();
  }

  public int stoneId() {
    return stoneId;
  }

  public int netherrackId() {
    return netherrackId;
  }

  public int airId() {
    return airId;
  }

  public int floorId() {
    return floorId;
  }

  public int spawnerId() {
    return spawnerId;
  }

  public boolean isSpawner(int id) {
    return id == spawnerId;
  }

  public int decoyClusterId() {
    return decoyClusterId;
  }

  public int amethystBlockId() {
    return amethystBlockId;
  }

  public int calciteId() {
    return calciteId;
  }

  public int smoothBasaltId() {
    return smoothBasaltId;
  }

  public int mediumBudId() {
    return mediumBudId;
  }

  public int largeBudId() {
    return largeBudId;
  }

  public int smallBudId() {
    return smallBudId;
  }

  public int buddingAmethystId() {
    return buddingAmethystId;
  }

  public int floorIdAt(int salt, int x, int y, int z) {
    int[] palette = floorPalette;
    if (palette == null || palette.length == 0) {
      return floorId;
    }
    int h = scramble(x ^ salt, y, z ^ salt);
    return palette[Math.floorMod(h, palette.length)];
  }

  public int[] floorPalette() {
    return floorPalette == null ? new int[]{floorId} : floorPalette.clone();
  }

  private static int scramble(int x, int y, int z) {
    int h = x * 0x9E3779B1;
    h ^= Integer.rotateLeft(z * 0x85EBCA77, 13);
    h ^= Integer.rotateLeft(y * 0xC2B2AE3D, 17);
    h ^= (h >>> 16);
    return h;
  }

  private static int[] buildFloorPalette(ClientVersion version) {
    StateType[] candidates = {
        StateTypes.DEEPSLATE
    };
    int[] tmp = new int[candidates.length];
    int written = 0;
    for (StateType type : candidates) {
      if (type == null) {
        continue;
      }
      try {
        WrappedBlockState state = WrappedBlockState.getDefaultState(version, type);
        if (state != null) {
          tmp[written++] = state.getGlobalId();
        }
      } catch (Throwable ignored) {
      }
    }
    if (written == 0) {
      return new int[]{
          WrappedBlockState.getDefaultState(version, StateTypes.DEEPSLATE).getGlobalId()
      };
    }
    int[] out = new int[written];
    System.arraycopy(tmp, 0, out, 0, written);
    return out;
  }

  public ClientVersion version() {
    return version;
  }
}
