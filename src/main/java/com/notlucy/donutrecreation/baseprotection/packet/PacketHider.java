package com.notlucy.donutrecreation.baseprotection.packet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockActionHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockChangeHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.ChunkDataHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.EntityEquipmentHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.EntityMetadataHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.EntityMoveHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.ExplosionDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.MultiBlockChangeHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.ParticleDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.SoundDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.SpawnEntityHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.UnloadChunkHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.UpdateLightHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.WorldEffectDamper;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class PacketHider {

  private final RevealManager rm;
  private final BlockIdRegistry registry;
  private final AtomicInteger failureCount = new AtomicInteger();
  private PacketListenerAbstract listener;

  private final ChunkDataHandler chunkDataHandler;
  private final UpdateLightHandler updateLightHandler;
  private final UnloadChunkHandler unloadChunkHandler;
  private final BlockChangeHandler blockChangeHandler;
  private final MultiBlockChangeHandler multiBlockChangeHandler;
  private final SpawnEntityHandler spawnEntityHandler;
  private final EntityMoveHandler entityMoveHandler;
  private final EntityEquipmentHandler entityEquipmentHandler;
  private final EntityMetadataHandler entityMetadataHandler;
  private final BlockActionHandler blockActionHandler;
  private final BlockEntityDebugProtection tiles;
  private final SoundDamper sounds;
  private final ParticleDamper particles;
  private final ExplosionDamper explosions;
  private final WorldEffectDamper worldEffects;

  private Map<String, PacketHandler> handlers;

  @FunctionalInterface
  interface PacketHandler {
    void handle(PacketSendEvent event, Player player);
  }

  public PacketHider(RevealManager revealManager) {
    this.rm = revealManager;
    this.registry = new BlockIdRegistry();
    DeepslateProtection deepslate = new DeepslateProtection(rm, registry);
    AmethystProtection amethyst = new AmethystProtection(rm, registry);

    this.chunkDataHandler = new ChunkDataHandler(rm, deepslate, amethyst);
    this.updateLightHandler = new UpdateLightHandler(rm);
    this.unloadChunkHandler = new UnloadChunkHandler(rm);
    this.blockChangeHandler = new BlockChangeHandler(rm, deepslate, amethyst);
    this.multiBlockChangeHandler = new MultiBlockChangeHandler(rm, deepslate, amethyst);
    this.spawnEntityHandler = new SpawnEntityHandler(rm);
    this.entityMoveHandler = new EntityMoveHandler(rm);
    this.entityEquipmentHandler = new EntityEquipmentHandler(rm);
    this.entityMetadataHandler = new EntityMetadataHandler(rm);
    this.blockActionHandler = new BlockActionHandler(rm);
    this.tiles = new BlockEntityDebugProtection(rm);
    this.sounds = new SoundDamper(rm);
    this.particles = new ParticleDamper(rm);
    this.explosions = new ExplosionDamper(rm);
    this.worldEffects = new WorldEffectDamper(rm);
  }

  public void register() {
    ClientVersion v = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    registry.rebuild(v);
    LogData.get().info("[hider] cached " + registry.amethystCount()
        + " amethyst states (" + v + ")");

    Map<String, PacketHandler> h = new java.util.HashMap<>();
    h.put("CHUNK_DATA", chunkDataHandler::handle);
    h.put("UPDATE_LIGHT", updateLightHandler::handle);
    h.put("UNLOAD_CHUNK", unloadChunkHandler::handle);
    h.put("BLOCK_CHANGE", blockChangeHandler::handle);
    h.put("MULTI_BLOCK_CHANGE", multiBlockChangeHandler::handle);
    h.put("BLOCK_ENTITY_DATA", tiles::handleBlockEntityData);
    h.put("SPAWN_ENTITY", spawnEntityHandler::handle);
    h.put("SOUND_EFFECT", sounds::handle);
    h.put("ENTITY_SOUND_EFFECT", sounds::handle);
    h.put("PARTICLE", particles::handle);
    h.put("EXPLOSION", explosions::handle);
    h.put("EFFECT", worldEffects::handle);
    h.put("BLOCK_BREAK_ANIMATION", worldEffects::handle);
    h.put("ENTITY_RELATIVE_MOVE", entityMoveHandler::handle);
    h.put("ENTITY_TELEPORT", entityMoveHandler::handle);
    h.put("ENTITY_EQUIPMENT", entityEquipmentHandler::handle);
    h.put("ENTITY_METADATA", entityMetadataHandler::handle);
    h.put("BLOCK_ACTION", blockActionHandler::handle);
    handlers = Map.copyOf(h);

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
        + registry.amethystCount() + " amethyst states, "
        + registry.oreCount() + " ore states (" + v + ")");
  }

  public void setGhostBlockManager(com.notlucy.donutrecreation.spawn.manager.GhostBlockManager gbm) {
    this.multiBlockChangeHandler.setGhostBlockManager(gbm);
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
      PacketHandler handler = handlers.get(event.getPacketType().getName());
      if (handler != null) {
        try {
          handler.handle(event, player);
        } catch (Throwable error) {
          if (failureCount.incrementAndGet() <= 8) {
            LogData.get().warning("[hider] packet handler error ("
                + event.getPacketType() + "): " + error);
          }
        }
      }
    }
  }
}
