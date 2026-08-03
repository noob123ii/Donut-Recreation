package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class ExplosionDamper {

  private final RevealManager rm;

  public ExplosionDamper(RevealManager rm) {
    this.rm = rm;
  }

  public boolean handle(PacketSendEvent event, Player viewer) {
    if (viewer == null) {
      return false;
    }
    WrapperPlayServerExplosion wrapper;
    try {
      wrapper = new WrapperPlayServerExplosion(event);
    } catch (Throwable ignored) {
      return false;
    }
    Vector3d pos;
    try {
      pos = wrapper.getPosition();
    } catch (Throwable ignored) {
      return false;
    }
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
