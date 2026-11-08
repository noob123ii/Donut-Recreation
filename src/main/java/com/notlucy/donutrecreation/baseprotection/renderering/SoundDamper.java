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
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class SoundDamper {

  private static final double SELF_SOUND_RADIUS_SQ = 8.0 * 8.0;

  private final RevealManager revealManager;
  private final Map<World, Map<Integer, Entity>> entityIndex = new HashMap<>();
  private final Object indexLock = new Object();
  private long indexTick = Long.MIN_VALUE;

  public SoundDamper(RevealManager revealManager) {
    this.revealManager = revealManager;
  }

  public boolean handlePacket(PacketSendEvent event, Player viewer) {
    if (viewer == null) {
      return false;
    }
    var type = event.getPacketType();
    if (type == PacketType.Play.Server.SOUND_EFFECT) {
      return handlePositional(event, viewer);
    }
    if (type == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
      return handleEntity(event, viewer);
    }
    return false;
  }

  private static final Set<String> LOGICAL_SOUND_PREFIXES = Set.of(
      "block.piston", "block.dispenser", "block.dropper", "block.lever",
      "block.button", "block.pressure", "block.redstone", "block.tripwire",
      "block.wooden_door", "block.iron_door", "block.trapdoor",
      "block.chest", "block.note_block", "block.comparator",
      "block.repeater", "block.click", "block.wood", "block.stone",
      "block.iron", "block.crafter", "block.spawner", "entity.ender_eye",
      "entity.experience_orb", "item.flintandsteel", "ui.button");

  private boolean handlePositional(PacketSendEvent event, Player viewer) {
    WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(event);
    var position = wrapper.getPosition();
    if (position == null) {
      return false;
    }
    var vloc = viewer.getLocation();
    double dx = position.getX() - vloc.getX();
    double dy = position.getY() - vloc.getY();
    double dz = position.getZ() - vloc.getZ();
    if (dx * dx + dy * dy + dz * dz <= SELF_SOUND_RADIUS_SQ) {
      return false;
    }
    boolean unrevealed = revealManager.isPositionHidden(viewer, position.getX(), position.getY(), position.getZ());
    if (unrevealed) {
      event.setCancelled(true);
      return true;
    }
    int blockY = (int) Math.floor(position.getY());
    if (!revealManager.isPositionVisible(viewer, (int) Math.floor(position.getX()), blockY, (int) Math.floor(position.getZ()))
        && isLogicalSound(wrapper.getSound())) {
      event.setCancelled(true);
      return true;
    }
    return false;
  }

  private boolean isLogicalSound(com.github.retrooper.packetevents.protocol.sound.Sound sound) {
    if (sound == null) {
      return false;
    }
    String key = sound.getSoundId().getKey().toLowerCase();
    for (String prefix : LOGICAL_SOUND_PREFIXES) {
      if (key.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private boolean handleEntity(PacketSendEvent event, Player viewer) {
    WrapperPlayServerEntitySoundEffect wrapper = new WrapperPlayServerEntitySoundEffect(event);
    int entityId = wrapper.getEntityId();
    if (entityId == viewer.getEntityId()) {
      return false;
    }
    Entity src = resolveEntity(viewer, entityId);
    if (src == null) {
      return false;
    }
    if (src.getUniqueId().equals(viewer.getUniqueId())) {
      return false;
    }
    var loc = src.getLocation();
    var vloc = viewer.getLocation();
    double dx = loc.getX() - vloc.getX();
    double dy = loc.getY() - vloc.getY();
    double dz = loc.getZ() - vloc.getZ();
    if (dx * dx + dy * dy + dz * dz <= SELF_SOUND_RADIUS_SQ) {
      return false;
    }
    if (revealManager.isPositionHidden(viewer, loc.getX(), loc.getY(), loc.getZ())) {
      event.setCancelled(true);
      return true;
    }
    return false;
  }

  private Entity resolveEntity(Player viewer, int entityId) {
    World world = viewer.getWorld();
    long now = Bukkit.getCurrentTick();

    synchronized (indexLock) {
      if (now != indexTick) {
        entityIndex.clear();
        indexTick = now;
      }
      Map<Integer, Entity> byId = entityIndex.get(world);
      if (byId == null) {
        byId = new HashMap<>();
        for (Entity e : world.getEntities()) {
          byId.put(e.getEntityId(), e);
        }
        entityIndex.put(world, byId);
      }
      return byId.get(entityId);
    }
  }
}
