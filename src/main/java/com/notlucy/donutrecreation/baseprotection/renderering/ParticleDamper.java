package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class ParticleDamper {

  private final RevealManager rm;

  public ParticleDamper(RevealManager rm) {
    this.rm = rm;
  }

  public boolean handle(PacketSendEvent event, Player viewer) {
    if (viewer == null) {
      return false;
    }
    WrapperPlayServerParticle wrapper;
    try {
      wrapper = new WrapperPlayServerParticle(event);
    } catch (Throwable ignored) {
      return false;
    }
    Vector3d pos = wrapper.getPosition();
    if (pos == null) {
      return false;
    }
    if (rm.shouldSuppressEntityFor(viewer, pos.getX(), pos.getY(), pos.getZ())) {
      event.setCancelled(true);
      return true;
    }
    return false;
  }
}
