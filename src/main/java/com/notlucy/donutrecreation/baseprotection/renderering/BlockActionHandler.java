package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;

public final class BlockActionHandler {
  private final RevealManager rm;

  public BlockActionHandler(RevealManager rm) {
    this.rm = rm;
  }

  public void handle(PacketSendEvent event, Player player) {
    try {
      var w = new WrapperPlayServerBlockAction(event);
      var pos = w.getBlockPosition();
      if (pos == null) return;
      int y = pos.getY();
      if (y < rm.hideBelowY()) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!rm.isRevealed(player, cx, cz)) {
          event.setCancelled(true);
        }
      } else if (y < rm.upperBarrierY()) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!rm.isUpperRevealed(player, cx, cz)) {
          event.setCancelled(true);
        }
      }
    } catch (Throwable ignored) {
    }
  }
}
