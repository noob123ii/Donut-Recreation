package com.notlucy.donutrecreation.baseprotection.packet;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockChangeHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.ChunkDataHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.ExplosionDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.MultiBlockChangeHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.ParticleDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.SoundDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.UpdateLightHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.WorldEffectDamper;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class PacketHider {

  private final RevealManager rm;
  private final BlockIdRegistry registry;
  private final AtomicInteger failureCount = new AtomicInteger();
  private final Map<UUID, Map<Integer, PendingSpawn>> pendingSpawns = new ConcurrentHashMap<>();
  private final Map<PacketType.Play.Server, BiConsumer<PacketSendEvent, Player>> handlers =
      new EnumMap<>(PacketType.Play.Server.class);
  private BlockEntityDebugProtection tiles;
  private MultiBlockChangeHandler multiBlockChange;
  private PacketListenerAbstract listener;

  public PacketHider(RevealManager revealManager) {
    this.rm = revealManager;
    this.registry = new BlockIdRegistry();
    rm.setChunkRevealCallback(this::resendSpawnsForChunk);
    DeepslateProtection deepslate = new DeepslateProtection(rm, registry);
    AmethystProtection amethyst = new AmethystProtection(rm, registry);
    BlockEntityDebugProtection tiles = new BlockEntityDebugProtection(rm);

    ChunkDataHandler chunkData = new ChunkDataHandler(rm, deepslate, amethyst, tiles);
    UpdateLightHandler updateLight = new UpdateLightHandler(rm);
    BlockChangeHandler blockChange = new BlockChangeHandler(rm, deepslate, amethyst);
    this.multiBlockChange = new MultiBlockChangeHandler(rm, deepslate, amethyst);
    this.tiles = tiles;
    SoundDamper sounds = new SoundDamper(rm);
    ParticleDamper particles = new ParticleDamper(rm);
    ExplosionDamper explosions = new ExplosionDamper(rm);
    WorldEffectDamper worldEffects = new WorldEffectDamper(rm);

    registerHandler(PacketType.Play.Server.CHUNK_DATA, chunkData::handle);
    registerHandler(PacketType.Play.Server.UPDATE_LIGHT, updateLight::handle);
    registerHandler(PacketType.Play.Server.MULTI_BLOCK_CHANGE, multiBlockChange::handle);
    registerHandler(PacketType.Play.Server.BLOCK_CHANGE, blockChange::handle);
    registerHandler(PacketType.Play.Server.BLOCK_ENTITY_DATA, tiles::handleBlockEntityData);
    registerHandler(PacketType.Play.Server.BLOCK_ACTION, this::handleBlockAction);
    registerHandler(PacketType.Play.Server.UNLOAD_CHUNK, this::handleUnloadChunk);
    registerHandler(PacketType.Play.Server.SPAWN_ENTITY, this::handleSpawnEntity);
    registerHandler(PacketType.Play.Server.ENTITY_RELATIVE_MOVE, this::handleEntityMove);
    registerHandler(PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION, this::handleEntityMove);
    registerHandler(PacketType.Play.Server.ENTITY_TELEPORT, this::handleEntityMove);
    registerHandler(PacketType.Play.Server.SOUND_EFFECT, sounds::handle);
    registerHandler(PacketType.Play.Server.ENTITY_SOUND_EFFECT, sounds::handle);
    registerHandler(PacketType.Play.Server.PARTICLE, particles::handle);
    registerHandler(PacketType.Play.Server.EXPLOSION, explosions::handle);
    registerHandler(PacketType.Play.Server.EFFECT, worldEffects::handle);
    registerHandler(PacketType.Play.Server.BLOCK_BREAK_ANIMATION, worldEffects::handle);
  }

  private void registerHandler(PacketType.Play.Server type, BiConsumer<PacketSendEvent, Player> handler) {
    handlers.put(type, handler);
  }

  public void register() {
    ClientVersion v = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    registry.rebuild(v);
    LogData.get().info("[hider] cached " + registry.amethystCount() + " amethyst states (" + v + ")");
    listener = new Listener();
    PacketEvents.getAPI().getEventManager().registerListener(listener);
  }

  public void unregister() {
    if (listener != null) {
      PacketEvents.getAPI().getEventManager().unregisterListener(listener);
      listener = null;
    }
  }

  public void clearPlayer(UUID playerId) {
    rm.clearEntityVisibility(playerId);
    pendingSpawns.remove(playerId);
  }

  public void reload() {
    ClientVersion v = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    registry.rebuild(v);
    LogData.get().info("[hider] reloaded block registry - "
        + registry.amethystCount() + " amethyst, " + registry.oreCount() + " ore states (" + v + ")");
  }

  public void setGhostBlockManager(com.notlucy.donutrecreation.spawn.manager.GhostBlockManager gbm) {
    this.multiBlockChange.setGhostBlockManager(gbm);
  }

  private static final class PendingSpawn {
    final int entityId;
    final Optional<UUID> uuid;
    final EntityType type;
    final Vector3d pos;
    final float pitch;
    final float yaw;
    final float headYaw;
    final int data;
    final Optional<Vector3d> velocity;

    PendingSpawn(int entityId, Optional<UUID> uuid, EntityType type, Vector3d pos,
        float pitch, float yaw, float headYaw, int data, Optional<Vector3d> velocity) {
      this.entityId = entityId;
      this.uuid = uuid;
      this.type = type;
      this.pos = pos;
      this.pitch = pitch;
      this.yaw = yaw;
      this.headYaw = headYaw;
      this.data = data;
      this.velocity = velocity;
    }
  }

  private final class Listener extends PacketListenerAbstract {
    Listener() {
      super(PacketListenerPriority.MONITOR);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
      if (!(event.getPlayer() instanceof Player player)) {
        return;
      }
      try {
        var handler = handlers.get(event.getPacketType());
        if (handler != null) {
          handler.accept(event, player);
        }
      } catch (Throwable error) {
        if (failureCount.incrementAndGet() <= 8) {
          LogData.get().warning("[hider] packet handler error (" + event.getPacketType() + "): " + error);
        }
      }
    }
  }

  private void handleUnloadChunk(PacketSendEvent event, Player player) {
    var w = new WrapperPlayServerUnloadChunk(event);
    int cx = w.getChunkX(0);
    int cz = w.getChunkZ(0);
    rm.markChunkUnloaded(player.getUniqueId(), cx, cz);
  }

  private void handleSpawnEntity(PacketSendEvent event, Player player) {
    var w = new WrapperPlayServerSpawnEntity(event);
    var type = w.getEntityType();
    var pos = w.getPosition();
    if (type == null || pos == null) return;
    if (pos.getY() < rm.upperBarrierY()) {
      int cx = (int) Math.floor(pos.getX()) >> 4;
      int cz = (int) Math.floor(pos.getZ()) >> 4;
      if (!rm.isRevealed(player, cx, cz)) {
        event.setCancelled(true);
        pendingSpawns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
            .put(w.getEntityId(), new PendingSpawn(
                w.getEntityId(), w.getUUID(), type, pos,
                w.getPitch(), w.getYaw(), w.getHeadYaw(), w.getData(), w.getVelocity()));
      }
    }
  }

  private void resendSpawnsForChunk(Player viewer, int chunkX, int chunkZ) {
    Map<Integer, PendingSpawn> map = pendingSpawns.get(viewer.getUniqueId());
    if (map == null || map.isEmpty()) return;
    map.entrySet().removeIf(entry -> {
      PendingSpawn s = entry.getValue();
      if (((int) Math.floor(s.pos.getX()) >> 4) != chunkX
          || ((int) Math.floor(s.pos.getZ()) >> 4) != chunkZ) {
        return false;
      }
      try {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
            new WrapperPlayServerSpawnEntity(
                s.entityId, s.uuid, s.type, s.pos, s.pitch, s.yaw, s.headYaw, s.data, s.velocity));
      } catch (Throwable ignored) { }
      return true;
    });
  }

  private void handleEntityMove(PacketSendEvent event, Player player) {
    if (event.getPacketType() != PacketType.Play.Server.ENTITY_TELEPORT) return;
    try {
      var w = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport(event);
      var pos = w.getPosition();
      if (pos.getY() < rm.upperBarrierY()
          && !rm.isRevealed(player, (int) Math.floor(pos.getX()) >> 4, (int) Math.floor(pos.getZ()) >> 4)) {
        event.setCancelled(true);
      }
    } catch (Throwable ignored) {
    }
  }

  private void handleBlockAction(PacketSendEvent event, Player player) {
    try {
      var w = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction(event);
      var pos = w.getBlockPosition();
      if (pos == null) return;
      int y = pos.getY(), cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
      if (y < rm.hideBelowY()) {
        if (!rm.isRevealed(player, cx, cz)) event.setCancelled(true);
      } else if (y < rm.upperBarrierY()) {
        if (!rm.isUpperRevealed(player, cx, cz)) event.setCancelled(true);
      }
    } catch (Throwable ignored) {
    }
  }
}
