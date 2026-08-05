package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;

public final class EntityMoveHandler {
  private final RevealManager rm;

  public EntityMoveHandler(RevealManager rm) {
    this.rm = rm;
  }

  public void handle(PacketSendEvent event, Player player) {
    try {
      var packetType = event.getPacketType();
      if (packetType != PacketType.Play.Server.ENTITY_TELEPORT) {
        return;
      }
      var w = new WrapperPlayServerEntityTeleport(event);
      double x = w.getPosition().getX();
      double y = w.getPosition().getY();
      double z = w.getPosition().getZ();
      if (y < rm.upperBarrierY()) {
        int cx = (int) Math.floor(x) >> 4;
        int cz = (int) Math.floor(z) >> 4;
        if (!rm.isRevealed(player, cx, cz)) {
          event.setCancelled(true);
        }
      }
    } catch (Throwable ignored) {
    }
  }
}
