package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import java.util.Arrays;
import java.util.BitSet;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class LightDebugProtection {

  private LightDebugProtection() {
  }

  /**
   * Zeroes the light arrays and updates masks for every section that is at or
   * below the floor. This prevents cheat clients from using light level anomalies
   * to detect hidden chunks.
   */
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

    for (int i = 0; i < span; i++) {
      int slot = i + 1;
      safeZeroSlot(block, slot);
      safeZeroSlot(sky, slot);
    }
    clearLightMasks(light, span);
  }

  /**
   * Zeroes the light arrays and updates masks for the sections indicated by
   * {@code sections}. This prevents cheat clients from using light level
   * anomalies to detect hidden chunks.
   */
  public static void stripLightForSections(LightData light, BitSet sections) {
    if (light == null) {
      return;
    }
    byte[][] block = light.getBlockLightArray();
    byte[][] sky = light.getSkyLightArray();

    for (int i = sections.nextSetBit(0); i >= 0; i = sections.nextSetBit(i + 1)) {
      int slot = i + 1;
      safeZeroSlot(block, slot);
      safeZeroSlot(sky, slot);
    }
    clearLightMasks(light, sections);
  }

  private static void clearLightMasks(LightData light, int sectionsToClear) {
    byte[][] blockLight = light.getBlockLightArray();
    byte[][] skyLight = light.getSkyLightArray();
    for (int i = 0; i < sectionsToClear; i++) {
      int idx = i + 1;
      if (blockLight != null && idx < blockLight.length && blockLight[idx] != null) {
        Arrays.fill(blockLight[idx], (byte) 0);
      }
      if (skyLight != null && idx < skyLight.length && skyLight[idx] != null) {
        Arrays.fill(skyLight[idx], (byte) 0);
      }
    }
    try {
      BitSet blockMask = light.getBlockLightMask();
      BitSet skyMask = light.getSkyLightMask();
      BitSet emptyBlockMask = light.getEmptyBlockLightMask();
      BitSet emptySkyMask = light.getEmptySkyLightMask();
      for (int i = 0; i < sectionsToClear; i++) {
        int idx = i + 1;
        if (blockMask != null) {
          blockMask.clear(idx);
        }
        if (skyMask != null) {
          skyMask.clear(idx);
        }
        if (emptyBlockMask != null) {
          emptyBlockMask.set(idx);
        }
        if (emptySkyMask != null) {
          emptySkyMask.set(idx);
        }
      }
    } catch (Throwable ignored) {
    }
  }

  private static void clearLightMasks(LightData light, BitSet sections) {
    try {
      BitSet blockMask = light.getBlockLightMask();
      BitSet skyMask = light.getSkyLightMask();
      BitSet emptyBlockMask = light.getEmptyBlockLightMask();
      BitSet emptySkyMask = light.getEmptySkyLightMask();
      for (int i = sections.nextSetBit(0); i >= 0; i = sections.nextSetBit(i + 1)) {
        int idx = i + 1;
        if (blockMask != null) {
          blockMask.clear(idx);
        }
        if (skyMask != null) {
          skyMask.clear(idx);
        }
        if (emptyBlockMask != null) {
          emptyBlockMask.set(idx);
        }
        if (emptySkyMask != null) {
          emptySkyMask.set(idx);
        }
      }
    } catch (Throwable ignored) {
    }
  }

  private static void safeZeroSlot(byte[][] data, int slot) {
    if (data != null && slot >= 0 && slot < data.length && data[slot] != null) {
      Arrays.fill(data[slot], (byte) 0);
    }
  }

}
