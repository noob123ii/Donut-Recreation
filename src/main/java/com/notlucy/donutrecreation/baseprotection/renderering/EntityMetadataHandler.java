package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;

public final class EntityMetadataHandler {
  private final RevealManager rm;

  public EntityMetadataHandler(RevealManager rm) {
    this.rm = rm;
  }

  public void handle(PacketSendEvent event, Player player) {
    try {
      var loc = player.getLocation();
      int cx = loc.getBlockX() >> 4;
      int cz = loc.getBlockZ() >> 4;
      if (!rm.isRevealed(player, cx, cz)) {
        event.setCancelled(true);
      }
    } catch (Throwable ignored) {
    }
  }
}
