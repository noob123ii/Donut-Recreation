package com.notlucy.donutrecreation.baseprotection.packet;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import java.util.BitSet;
import java.util.Set;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class BlockIdRegistry {

  private static final Set<StateType> AMETHYST_FAMILY = Set.of(
      StateTypes.AMETHYST_BLOCK,
      StateTypes.BUDDING_AMETHYST,
      StateTypes.AMETHYST_CLUSTER,
      StateTypes.SMALL_AMETHYST_BUD,
      StateTypes.MEDIUM_AMETHYST_BUD,
      StateTypes.LARGE_AMETHYST_BUD);

  private BitSet amethyst;
  private int amethystLen;
  private int stoneId;
  private int floorId;
  private int decoyAmethystId;
  private int[] floorPalette;
  private ClientVersion version;

  public void rebuild(ClientVersion version) {
    this.version = version;
    this.stoneId = WrappedBlockState.getDefaultState(version, StateTypes.STONE).getGlobalId();
    this.floorId = WrappedBlockState.getDefaultState(version, StateTypes.DEEPSLATE).getGlobalId();
    this.decoyAmethystId = WrappedBlockState
        .getDefaultState(version, StateTypes.AMETHYST_BLOCK).getGlobalId();
    this.floorPalette = buildFloorPalette(version);

    BitSet bits = new BitSet(8192);
    for (int id = 0; id < 32768; id++) {
      WrappedBlockState s = WrappedBlockState.getByGlobalId(version, id, false);
      if (s == null) {
        continue;
      }
      StateType t = s.getType();
      if (t != null && AMETHYST_FAMILY.contains(t)) {
        bits.set(id);
      }
    }
    this.amethyst = bits;
    this.amethystLen = bits.length();
  }

  public boolean isAmethyst(int id) {
    BitSet bits = amethyst;
    return bits != null && id >= 0 && id < amethystLen && bits.get(id);
  }

  public int amethystCount() {
    return amethyst == null ? 0 : amethyst.cardinality();
  }

  public int stoneId() {
    return stoneId;
  }

  public int floorId() {
    return floorId;
  }

  public int decoyAmethystId() {
    return decoyAmethystId;
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
        StateTypes.DEEPSLATE,
        StateTypes.TUFF,
        StateTypes.COBBLED_DEEPSLATE,
        StateTypes.STONE
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
