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

  public BlockChangeHandler(RevealManager revealManager, DeepslateProtection deepslate, AmethystProtection amethyst) {
    this.revealManager = revealManager;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
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
