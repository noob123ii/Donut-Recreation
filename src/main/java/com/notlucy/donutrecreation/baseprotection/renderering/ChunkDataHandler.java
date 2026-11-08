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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.PlacedBlockHider;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.packet.BlockIdRegistry;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.util.LogData;

public final class ChunkDataHandler {
  private final RevealManager revealManager;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private final BlockEntityDebugProtection blockEntities;
  private final com.notlucy.donutrecreation.baseprotection.protection.OreVisibility oreVisibility;
  private final PlacedBlockHider placedBlockHider;
  private final BlockIdRegistry blockIds;

  public ChunkDataHandler(RevealManager revealManager, DeepslateProtection deepslate,
      AmethystProtection amethyst, BlockEntityDebugProtection blockEntities,
      com.notlucy.donutrecreation.baseprotection.protection.OreVisibility oreVisibility) {
    this(revealManager, deepslate, amethyst, blockEntities, oreVisibility, null, null);
  }

  public ChunkDataHandler(RevealManager revealManager, DeepslateProtection deepslate,
      AmethystProtection amethyst, BlockEntityDebugProtection blockEntities,
      com.notlucy.donutrecreation.baseprotection.protection.OreVisibility oreVisibility,
      PlacedBlockHider placedBlockHider, BlockIdRegistry blockIds) {
    this.revealManager = revealManager;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
    this.blockEntities = blockEntities;
    this.oreVisibility = oreVisibility;
    this.placedBlockHider = placedBlockHider;
    this.blockIds = blockIds;
  }

  public boolean handlePacket(PacketSendEvent event, Player player) {
    WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
    int chunkX = wrapper.getColumn().getX();
    int chunkZ = wrapper.getColumn().getZ();
    boolean touched = false;
    try {
      if (revealManager.geodeHideEnabled()) {
        touched = amethyst.rewriteChunk(wrapper, player);
      }
    } catch (Throwable e) {
      LogData.get().warning("[hider] amethyst rewrite crashed at " + chunkX + "," + chunkZ + ": " + e);
    }
    try {
      touched |= deepslate.rewriteChunk(event, wrapper, player);
    } catch (Throwable e) {
      LogData.get().warning("[hider] deepslate rewrite crashed at " + chunkX + "," + chunkZ + ": " + e);
    }
    try {
      /* Scrub blockEntities even when other rewrites are skipped: they expose containers. */
      touched |= blockEntities.scrubChunk(wrapper.getColumn(), player, chunkX, chunkZ);
    } catch (Throwable e) {
      LogData.get().warning("[hider] tile scrub crashed at " + chunkX + "," + chunkZ + ": " + e);
    }
    try {
      touched |= oreVisibility.rewriteChunk(wrapper, player);
    } catch (Throwable e) {
      LogData.get().warning("[hider] ore visibility crashed at " + chunkX + "," + chunkZ + ": " + e);
    }
    try {
      if (placedBlockHider != null && blockIds != null) {
        touched |= placedBlockHider.rewriteChunk(wrapper, player, blockIds);
      }
    } catch (Throwable e) {
      LogData.get().warning("[hider] placed-block hide crashed at " + chunkX + "," + chunkZ + ": " + e);
    }
    if (touched) {
      event.markForReEncode(true);
    }
    revealManager.recordChunkDelivered(player.getUniqueId(), chunkX, chunkZ);
    return touched;
  }
}
