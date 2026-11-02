package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class ExplosionDamper {

  private final RevealManager revealManager;

  public ExplosionDamper(RevealManager revealManager) {
    this.revealManager = revealManager;
  }

  public boolean handlePacket(PacketSendEvent event, Player viewer) {
    if (viewer == null) {
      return false;
    }
    WrapperPlayServerExplosion wrapper;
    try {
      wrapper = new WrapperPlayServerExplosion(event);
    } catch (Throwable ignored) {
      return false;
    }
    Vector3d position;
    try {
      position = wrapper.getPosition();
    } catch (Throwable ignored) {
      return false;
    }
    if (position == null) {
      return false;
    }
    if (revealManager.isPositionHidden(viewer, position.getX(), position.getY(), position.getZ())) {
      event.setCancelled(true);
      return true;
    }
    return false;
  }
}
