package com.notlucy.donutrecreation.baseprotection.packet;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateLight;
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
  private PacketListenerAbstract listener;

  private final ChunkDataHandler chunkData;
  private final UpdateLightHandler updateLight;
  private final BlockChangeHandler blockChange;
  private final MultiBlockChangeHandler multiBlockChange;
  private final BlockEntityDebugProtection tiles;
  private final SoundDamper sounds;
  private final ParticleDamper particles;
  private final ExplosionDamper explosions;
  private final WorldEffectDamper worldEffects;

  public PacketHider(RevealManager revealManager) {
    this.rm = revealManager;
    this.registry = new BlockIdRegistry();
    DeepslateProtection deepslate = new DeepslateProtection(rm, registry);
    AmethystProtection amethyst = new AmethystProtection(rm, registry);
    this.chunkData = new ChunkDataHandler(rm, deepslate, amethyst);
    this.updateLight = new UpdateLightHandler(rm);
    this.blockChange = new BlockChangeHandler(rm, deepslate, amethyst);
    this.multiBlockChange = new MultiBlockChangeHandler(rm, deepslate, amethyst);
    this.tiles = new BlockEntityDebugProtection(rm);
    this.sounds = new SoundDamper(rm);
    this.particles = new ParticleDamper(rm);
    this.explosions = new ExplosionDamper(rm);
    this.worldEffects = new WorldEffectDamper(rm);
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
        switch (event.getPacketType()) {
          case PacketType.Play.Server.CHUNK_DATA -> chunkData.handle(event, player);
          case PacketType.Play.Server.UPDATE_LIGHT -> updateLight.handle(event, player);
          case PacketType.Play.Server.UNLOAD_CHUNK -> handleUnloadChunk(event, player);
          case PacketType.Play.Server.BLOCK_CHANGE -> blockChange.handle(event, player);
          case PacketType.Play.Server.MULTI_BLOCK_CHANGE -> multiBlockChange.handle(event, player);
          case PacketType.Play.Server.BLOCK_ENTITY_DATA -> tiles.handleBlockEntityData(event, player);
          case PacketType.Play.Server.SPAWN_ENTITY -> handleSpawnEntity(event, player);
          case PacketType.Play.Server.SOUND_EFFECT,
               PacketType.Play.Server.ENTITY_SOUND_EFFECT -> sounds.handle(event, player);
          case PacketType.Play.Server.PARTICLE -> particles.handle(event, player);
          case PacketType.Play.Server.EXPLOSION -> explosions.handle(event, player);
          case PacketType.Play.Server.EFFECT,
               PacketType.Play.Server.BLOCK_BREAK_ANIMATION -> worldEffects.handle(event, player);
          case PacketType.Play.Server.ENTITY_RELATIVE_MOVE,
               PacketType.Play.Server.ENTITY_TELEPORT -> handleEntityMove(event, player);
          case PacketType.Play.Server.ENTITY_EQUIPMENT,
               PacketType.Play.Server.ENTITY_METADATA -> handlePositionalCancel(event, player);
          case PacketType.Play.Server.BLOCK_ACTION -> handleBlockAction(event, player);
          default -> {}
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
    rm.markChunkUnloaded(player.getUniqueId(), w.getChunkX(0), w.getChunkZ(0));
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
      }
    }
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

  private void handlePositionalCancel(PacketSendEvent event, Player player) {
    var loc = player.getLocation();
    if (!rm.isRevealed(player, loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
      event.setCancelled(true);
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
