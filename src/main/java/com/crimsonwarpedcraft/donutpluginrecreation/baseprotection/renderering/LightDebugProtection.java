package com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import java.util.Arrays;
import java.util.BitSet;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class LightDebugProtection {

  private LightDebugProtection() {
  }

  public static void stripFloorLight(
      WrapperPlayServerChunkData wrapper, int minSection, int floorSection) {
    LightData light = wrapper.getLightData();
    if (light == null) {
      return;
    }
    int span = floorSection - minSection + 1;
    if (span <= 0) {
      return;
    }

    byte[][] block = light.getBlockLightArray();
    byte[][] sky = light.getSkyLightArray();
    BitSet blockMask = light.getBlockLightMask();
    BitSet skyMask = light.getSkyLightMask();
    BitSet blockEmpty = light.getEmptyBlockLightMask();
    BitSet skyEmpty = light.getEmptySkyLightMask();

    for (int i = 0; i < span; i++) {
      int slot = i + 1;
      zeroSlot(block, slot);
      zeroSlot(sky, slot);
      clearMask(blockMask, slot);
      clearMask(skyMask, slot);
      markEmpty(blockEmpty, slot);
      markEmpty(skyEmpty, slot);
    }
  }

  public static void stripBlockLightForSections(LightData light, BitSet sections) {
    if (light == null) {
      return;
    }
    byte[][] block = light.getBlockLightArray();
    if (block == null) {
      return;
    }
    BitSet mask = light.getBlockLightMask();
    BitSet empty = light.getEmptyBlockLightMask();

    for (int i = sections.nextSetBit(0); i >= 0; i = sections.nextSetBit(i + 1)) {
      int slot = i + 1;
      zeroSlot(block, slot);
      clearMask(mask, slot);
      markEmpty(empty, slot);
    }
  }

  private static void zeroSlot(byte[][] data, int slot) {
    if (data != null && slot < data.length && data[slot] != null) {
      Arrays.fill(data[slot], (byte) 0);
    }
  }

  private static void clearMask(BitSet mask, int slot) {
    if (mask != null) {
      mask.clear(slot);
    }
  }

  private static void markEmpty(BitSet mask, int slot) {
    if (mask != null) {
      mask.set(slot);
    }
  }
}
