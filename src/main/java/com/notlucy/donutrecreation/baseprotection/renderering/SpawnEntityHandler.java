package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;

public final class SpawnEntityHandler {
  private final RevealManager rm;

  public SpawnEntityHandler(RevealManager rm) {
    this.rm = rm;
  }

  public void handle(PacketSendEvent event, Player player) {
    WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
    var entityType = wrapper.getEntityType();
    if (entityType == null) {
      return;
    }

    var position = wrapper.getPosition();
    if (position == null) {
      return;
    }
    double y = position.getY();

    if (entityType == EntityTypes.PLAYER) {
      if (y < rm.upperBarrierY()) {
        int cx = (int) Math.floor(position.getX()) >> 4;
        int cz = (int) Math.floor(position.getZ()) >> 4;
        if (!rm.isRevealed(player, cx, cz)) {
          event.setCancelled(true);
        }
      }
      return;
    }

    if (y < rm.upperBarrierY()) {
      int cx = (int) Math.floor(position.getX()) >> 4;
      int cz = (int) Math.floor(position.getZ()) >> 4;
      if (!rm.isRevealed(player, cx, cz)) {
        event.setCancelled(true);
      }
    }
  }
}
