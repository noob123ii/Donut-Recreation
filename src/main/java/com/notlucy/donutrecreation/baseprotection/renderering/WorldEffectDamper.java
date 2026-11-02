package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class WorldEffectDamper {

  private final RevealManager revealManager;

  public WorldEffectDamper(RevealManager revealManager) {
    this.revealManager = revealManager;
  }

  public boolean handlePacket(PacketSendEvent event, Player viewer) {
    if (viewer == null) {
      return false;
    }
    if (event.getPacketType() == PacketType.Play.Server.EFFECT) {
      return handleWorldEvent(event, viewer);
    }
    if (event.getPacketType() == PacketType.Play.Server.BLOCK_BREAK_ANIMATION) {
      return handleBlockDestruction(event, viewer);
    }
    return false;
  }

  private boolean handleWorldEvent(PacketSendEvent event, Player viewer) {
    WrapperPlayServerEffect wrapper;
    try {
      wrapper = new WrapperPlayServerEffect(event);
    } catch (Throwable ignored) {
      return false;
    }
    Vector3i position;
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

  private boolean handleBlockDestruction(PacketSendEvent event, Player viewer) {
    WrapperPlayServerBlockBreakAnimation wrapper;
    try {
      wrapper = new WrapperPlayServerBlockBreakAnimation(event);
    } catch (Throwable ignored) {
      return false;
    }
    Vector3i position;
    try {
      position = wrapper.getBlockPosition();
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
