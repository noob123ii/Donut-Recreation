package com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.renderering;

import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.RevealManager;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class SoundDamper {

  private static final double SELF_SOUND_RADIUS_SQ = 4.0 * 4.0;

  private final RevealManager rm;
  private final Map<World, Map<Integer, Entity>> entityIndex = new HashMap<>();
  private long indexTick = Long.MIN_VALUE;

  public SoundDamper(RevealManager rm) {
    this.rm = rm;
  }

  public boolean handle(PacketSendEvent event, Player viewer) {
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

  private boolean handlePositional(PacketSendEvent event, Player viewer) {
    WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(event);
    var pos = wrapper.getEffectPosition();
    if (pos == null) {
      return false;
    }
    // Never suppress sounds emitted at (or essentially at) the viewer's own location —
    // e.g. their own footsteps, jumps, elytra-equip, eating, etc. The vanilla server
    // emits many of these as positional SOUND_EFFECT packets pinned to the player's
    // current location, which would otherwise be silenced once the player descends
    // below the hide floor.
    var vloc = viewer.getLocation();
    double dx = pos.getX() - vloc.getX();
    double dy = pos.getY() - vloc.getY();
    double dz = pos.getZ() - vloc.getZ();
    if (dx * dx + dy * dy + dz * dz <= SELF_SOUND_RADIUS_SQ) {
      return false;
    }
    if (rm.shouldSuppressEntityFor(viewer, pos.getX(), pos.getY(), pos.getZ())) {
      event.setCancelled(true);
      return true;
    }
    return false;
  }

  private boolean handleEntity(PacketSendEvent event, Player viewer) {
    WrapperPlayServerEntitySoundEffect wrapper = new WrapperPlayServerEntitySoundEffect(event);
    int entityId = wrapper.getEntityId();
    // Fast-path: a player's own ENTITY_SOUND_EFFECT (elytra flap/equip, damage, etc.)
    // must never be suppressed for themselves regardless of where they are.
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
    if (rm.shouldSuppressEntityFor(viewer, loc.getX(), loc.getY(), loc.getZ())) {
      event.setCancelled(true);
      return true;
    }
    return false;
  }

  private Entity resolveEntity(Player viewer, int entityId) {
    World world = viewer.getWorld();
    long now = Bukkit.getCurrentTick();
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
