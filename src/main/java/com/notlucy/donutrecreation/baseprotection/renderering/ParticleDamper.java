/*
 * Copyright (C) 2026 Notlucy and Donut Recreation contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
