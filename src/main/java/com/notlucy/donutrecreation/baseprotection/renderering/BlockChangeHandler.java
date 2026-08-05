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

  public void handle(PacketSendEvent event, Player player) {
    WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
    var pos = wrapper.getBlockPosition();
    if (pos == null) {
      return;
    }
    int x = pos.getX();
    int y = pos.getY();
    int z = pos.getZ();

    if (y < rm.hideBelowY()) {
      int cx = x >> 4, cz = z >> 4;
      if (!rm.isRevealed(player, cx, cz)) {
        event.setCancelled(true);
        return;
      }
    }

    if (deepslate.rewriteBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return;
    }
    if (deepslate.maskTileBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return;
    }
    if (amethyst.rewriteBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return;
    }

    if (y >= rm.hideBelowY()) {
      var blockState = wrapper.getBlockState();
      if (blockState != null && deepslate.isSpawner(blockState.getGlobalId())) {
        wrapper.setBlockState(WrappedBlockState.getByGlobalId(deepslate.floorId()));
        event.markForReEncode(true);
      }
    }
  }
}
