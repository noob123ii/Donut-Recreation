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
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;

public final class BlockChangeHandler {
  private final RevealManager revealManager;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private final com.notlucy.donutrecreation.baseprotection.protection.OreVisibility oreVisibility;

  public BlockChangeHandler(RevealManager revealManager, DeepslateProtection deepslate,
      AmethystProtection amethyst,
      com.notlucy.donutrecreation.baseprotection.protection.OreVisibility oreVisibility) {
    this.revealManager = revealManager;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
    this.oreVisibility = oreVisibility;
  }

  public boolean handlePacket(PacketSendEvent event, Player player) {
    WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
    var position = wrapper.getBlockPosition();
    if (position == null) {
      return false;
    }
    int x = position.getX(), y = position.getY(), z = position.getZ();

    if (deepslate.rewriteBlockChange(wrapper, player, x, y, z)
        || amethyst.rewriteBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return true;
    }
    if (oreVisibility.rewriteBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return true;
    }
    if (y > revealManager.spawnerMaskAboveY() && !deepslate.isNearSpawner(player, x, y, z)) {
      var state = wrapper.getBlockState();
      if (state != null && deepslate.isSpawner(state.getGlobalId())) {
        wrapper.setBlockState(WrappedBlockState.getByGlobalId(deepslate.stoneId()));
        event.markForReEncode(true);
        return true;
      }
    }
    return false;
  }
}
