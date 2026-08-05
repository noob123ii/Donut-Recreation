package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;

public final class BlockChangeHandler {
  private final RevealManager rm;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;

  public BlockChangeHandler(RevealManager rm, DeepslateProtection deepslate, AmethystProtection amethyst) {
    this.rm = rm;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
  }

  public boolean handle(PacketSendEvent event, Player player) {
    WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
    var pos = wrapper.getBlockPosition();
    if (pos == null) {
      return false;
    }
    int x = pos.getX(), y = pos.getY(), z = pos.getZ();

    if (y < rm.hideBelowY() && !rm.isRevealed(player, x >> 4, z >> 4)) {
      event.setCancelled(true);
      return true;
    }
    if (deepslate.rewriteBlockChange(wrapper, player, x, y, z)
        || deepslate.maskTileBlockChange(wrapper, player, x, y, z)
        || amethyst.rewriteBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return true;
    }
    if (y >= rm.hideBelowY()) {
      var state = wrapper.getBlockState();
      if (state != null && deepslate.isSpawner(state.getGlobalId())) {
        wrapper.setBlockState(WrappedBlockState.getByGlobalId(deepslate.floorId()));
        event.markForReEncode(true);
        return true;
      }
    }
    return false;
  }
}
