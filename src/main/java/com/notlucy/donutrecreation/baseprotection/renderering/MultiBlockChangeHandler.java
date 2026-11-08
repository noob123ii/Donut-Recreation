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

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;

public final class MultiBlockChangeHandler {
  private final RevealManager revealManager;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private final com.notlucy.donutrecreation.baseprotection.protection.OreVisibility oreVisibility;
  private GhostBlockManager ghostBlockManager;

  public MultiBlockChangeHandler(RevealManager revealManager, DeepslateProtection deepslate,
      AmethystProtection amethyst,
      com.notlucy.donutrecreation.baseprotection.protection.OreVisibility oreVisibility) {
    this.revealManager = revealManager;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
    this.oreVisibility = oreVisibility;
  }

  public void setGhostBlockManager(GhostBlockManager gbm) {
    this.ghostBlockManager = gbm;
  }

  public boolean handlePacket(PacketSendEvent event, Player player) {
    WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
    if (!deepslate.isWrapperRelevant(wrapper)) {
      return false;
    }
    var section = wrapper.getChunkPosition();
    if (section == null) {
      return false;
    }
    int chunkX = section.getX(), chunkZ = section.getZ();
    boolean upperRevealed = revealManager.isBarrierBandRevealed(player, chunkX, chunkZ);
    int salt = revealManager.getPlayerSalt(player.getUniqueId());
    int floorY = revealManager.hideBelowY();
    int upperY = revealManager.upperBarrierY();
    int spawnerMaskAboveY = revealManager.spawnerMaskAboveY();
    boolean checkGhosts = revealManager.hasAnyGhostBlocks(player.getUniqueId());
    boolean maskBase = player.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL;
    boolean surfaceLocked = revealManager.isSurfaceScrambleNear(player, chunkX, chunkZ);
    int surfaceTop = Math.min(player.getWorld().getMaxHeight(),
        revealManager.surfaceScrambleMaxY());
    int floorFixes = 0, spawnerFixes = 0;
    int spawnerId = deepslate.spawnerId();

    for (var encodedBlock : wrapper.getBlocks()) {
      int blockY = encodedBlock.getY();
      int blockX = encodedBlock.getX();
      int blockZ = encodedBlock.getZ();
      if (checkGhosts && ghostBlockManager != null
          && ghostBlockManager.hasGhostBlockAt(player.getUniqueId(), blockX, blockY, blockZ)) {
        continue;
      }
      boolean surfaceBand = maskBase && surfaceLocked
          && blockY >= revealManager.surfaceScrambleStartY() && blockY < surfaceTop;
      if (surfaceBand) {
        int fakeY = revealManager.fakeSurfaceY(blockX, blockZ, salt);
        int bandStart = revealManager.surfaceScrambleStartY();
        if (fakeY < bandStart) {
          fakeY = bandStart;
        }
        if (fakeY >= surfaceTop) {
          fakeY = surfaceTop - 1;
        }
        int want = (blockY == fakeY) ? deepslate.stoneId() : deepslate.airId();
        if (encodedBlock.getBlockId() != want) {
          encodedBlock.setBlockId(want);
          floorFixes++;
        }
        continue;
      }
      boolean belowFloor = maskBase && blockY < floorY;
      boolean barrierBand = maskBase && !belowFloor && blockY < upperY;
      boolean caveVisible = revealManager.isCaveBlockVisible(player, blockX, blockY, blockZ);
      boolean inRange = revealManager.isWithinRevealRange(player, blockX, blockY, blockZ, 10);
      boolean visible;
      if (belowFloor) {
        visible = caveVisible || inRange
            || revealManager.isChunkRevealed(player, blockX >> 4, blockZ >> 4);
      } else if (barrierBand) {
        visible = caveVisible || inRange || upperRevealed;
      } else {
        visible = true;
      }
      if (!visible && (belowFloor || barrierBand)) {
        boolean bandSpawner = barrierBand && encodedBlock.getBlockId() == spawnerId;
        if (!bandSpawner) {
          int want = deepslate.floorIdAt(salt, blockX, blockY, blockZ);
          if (encodedBlock.getBlockId() != want) {
            encodedBlock.setBlockId(want);
            floorFixes++;
          }
        }
      } else if (blockY > spawnerMaskAboveY
          && encodedBlock.getBlockId() == spawnerId
          && !deepslate.isNearSpawner(player, blockX, blockY, blockZ)) {
        encodedBlock.setBlockId(deepslate.stoneId());
        spawnerFixes++;
      }
    }
    int oreFixes = oreVisibility.rewriteMultiBlock(wrapper, player);
    int amFixes = amethyst.rewriteMultiBlock(wrapper, player, false, floorFixes);

    boolean touched = floorFixes > 0 || spawnerFixes > 0 || amFixes > 0 || oreFixes > 0;
    if (touched) {
      event.markForReEncode(true);
    }
    return touched;
  }
}
