package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class ParticleDamper {

  private final RevealManager revealManager;

  public ParticleDamper(RevealManager revealManager) {
    this.revealManager = revealManager;
  }

  public boolean handlePacket(PacketSendEvent event, Player viewer) {
    if (viewer == null) {
      return false;
    }
    WrapperPlayServerParticle wrapper;
    try {
      wrapper = new WrapperPlayServerParticle(event);
    } catch (Throwable ignored) {
      return false;
    }
    Vector3d position = wrapper.getPosition();
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
