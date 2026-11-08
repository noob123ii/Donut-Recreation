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
package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import java.util.Arrays;
import java.util.BitSet;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class LightDebugProtection {

  private LightDebugProtection() {
  }

  private static final byte FAKE_LIGHT_LEVEL = 5;

  public static void stripFloorLight(
      WrapperPlayServerChunkData wrapper, int minSection, int floorSection) {
    LightData light = wrapper.getLightData();
    stripFloorLight(light, minSection, floorSection);
  }

  public static void stripFloorLight(LightData light, int minSection, int floorSection) {
    stripSectionRange(light, minSection, floorSection, minSection);
  }

  /**
   * Strips light data for an inclusive section range. Sections are absolute
   * chunk-section indices (world Y >> 4); {@code minSection} is the lowest
   * section present in the packet.
   */
  public static void stripSectionRange(
      LightData light, int firstSection, int lastSection, int minSection) {
    if (light == null || lastSection < firstSection) {
      return;
    }
    byte[][] block = light.getBlockLightArray();
    byte[][] sky = light.getSkyLightArray();

    int firstSlot = Math.max(1, firstSection - minSection + 1);
    int lastSlot = Math.max(firstSlot, lastSection - minSection + 1);
    for (int slot = firstSlot; slot <= lastSlot; slot++) {
      safeFillSlot(block, slot, FAKE_LIGHT_LEVEL);
      safeFillSlot(sky, slot, FAKE_LIGHT_LEVEL);
    }
    clearLightMaskRange(light, firstSlot, lastSlot);
  }

  public static void stripLightForSections(LightData light, BitSet sections) {
    if (light == null) {
      return;
    }
    byte[][] block = light.getBlockLightArray();
    byte[][] sky = light.getSkyLightArray();

    for (int i = sections.nextSetBit(0); i >= 0; i = sections.nextSetBit(i + 1)) {
      int slot = i + 1;
      safeFillSlot(block, slot, FAKE_LIGHT_LEVEL);
      safeFillSlot(sky, slot, FAKE_LIGHT_LEVEL);
    }
    clearLightMasks(light, sections);
  }

  private static void clearLightMaskRange(LightData light, int firstSlot, int lastSlot) {
    byte[][] blockLight = light.getBlockLightArray();
    byte[][] skyLight = light.getSkyLightArray();
    for (int idx = firstSlot; idx <= lastSlot; idx++) {
      if (blockLight != null && idx >= 0 && idx < blockLight.length && blockLight[idx] != null) {
        Arrays.fill(blockLight[idx], (byte) 0);
      }
      if (skyLight != null && idx >= 0 && idx < skyLight.length && skyLight[idx] != null) {
        Arrays.fill(skyLight[idx], (byte) 0);
      }
    }
    try {
      BitSet blockMask = light.getBlockLightMask();
      BitSet skyMask = light.getSkyLightMask();
      BitSet emptyBlockMask = light.getEmptyBlockLightMask();
      BitSet emptySkyMask = light.getEmptySkyLightMask();
      for (int idx = firstSlot; idx <= lastSlot; idx++) {
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

  private static void safeFillSlot(byte[][] data, int slot, int level) {
    if (data != null && slot >= 0 && slot < data.length && data[slot] != null) {
      Arrays.fill(data[slot], (byte) (level & 0xFF));
    }
  }

}
