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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateLight;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.ExplosionDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.LightDebugProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.ParticleDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.SoundDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.WorldEffectDamper;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class PacketHider {

  private final RevealManager rm;
  private final BlockIdRegistry registry;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private final BlockEntityDebugProtection tiles;
  private final SoundDamper sounds;
  private final ParticleDamper particles;
  private final ExplosionDamper explosions;
  private final WorldEffectDamper worldEffects;
  private final AtomicInteger failureCount = new AtomicInteger();
  private PacketListenerAbstract listener;

  public PacketHider(RevealManager revealManager) {
    this.rm = revealManager;
    this.registry = new BlockIdRegistry();
    this.deepslate = new DeepslateProtection(rm, registry);
    this.amethyst = new AmethystProtection(rm, registry);
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
          case PacketType.Play.Server.CHUNK_DATA -> dispatchChunkData(event, player);
          case PacketType.Play.Server.UNLOAD_CHUNK -> dispatchUnloadChunk(event, player);
          case PacketType.Play.Server.UPDATE_LIGHT -> dispatchUpdateLight(event, player);
          case PacketType.Play.Server.BLOCK_CHANGE -> dispatchBlockChange(event, player);
          case PacketType.Play.Server.MULTI_BLOCK_CHANGE -> dispatchMultiBlockChange(event, player);
          case PacketType.Play.Server.BLOCK_ENTITY_DATA -> tiles.handleBlockEntityData(event, player);
          case PacketType.Play.Server.SPAWN_ENTITY -> dispatchSpawnEntity(event, player);
          case PacketType.Play.Server.SOUND_EFFECT,
               PacketType.Play.Server.ENTITY_SOUND_EFFECT -> sounds.handle(event, player);
          case PacketType.Play.Server.PARTICLE -> particles.handle(event, player);
          case PacketType.Play.Server.EXPLOSION -> explosions.handle(event, player);
          case PacketType.Play.Server.EFFECT,
               PacketType.Play.Server.BLOCK_BREAK_ANIMATION -> worldEffects.handle(event, player);
          default -> {}
        }
      } catch (Throwable error) {
        if (failureCount.incrementAndGet() <= 8) {
          LogData.get().warning("[hider] packet handler error ("
              + event.getPacketType() + "): " + error);
        }
      }
    }
  }

  private void dispatchChunkData(PacketSendEvent event, Player player) {
    WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
    int cx = wrapper.getColumn().getX();
    int cz = wrapper.getColumn().getZ();
    boolean[] rewrote = {false};
    try {
      if (rm.geodeHideEnabled() && amethyst.rewriteChunk(wrapper, player)) {
        rewrote[0] = true;
      }
    } catch (Throwable e) {
      LogData.get().warning("[hider] amethyst rewrite crashed at " + cx + "," + cz
          + " for " + player.getName() + ": " + e);
      e.printStackTrace();
    }
    try {
      rewrote[0] |= deepslate.rewriteChunk(event, wrapper, player);
    } catch (Throwable e) {
      LogData.get().warning("[hider] deepslate rewrite crashed at " + cx + "," + cz
          + " for " + player.getName() + ": " + e);
      e.printStackTrace();
    }

    try {
      if (!rm.isRevealed(player, cx, cz)) {
        BlockEntityDebugProtection.scrubTilesBelow(wrapper.getColumn(), rm.hideBelowY());
        rewrote[0] = true;
      }
    } catch (Throwable e) {
      LogData.get().warning("[hider] tile entity scrub crashed at " + cx + "," + cz
          + " for " + player.getName() + ": " + e);
      e.printStackTrace();
    }
    if (rewrote[0]) {
      event.markForReEncode(true);
    }

    rm.markChunkDelivered(player.getUniqueId(), cx, cz);
  }

  private void dispatchUpdateLight(PacketSendEvent event, Player player) {
    try {
      WrapperPlayServerUpdateLight wrapper = new WrapperPlayServerUpdateLight(event);
      int cx = wrapper.getChunkX(0);
      int cz = wrapper.getChunkZ(0);

      if (rm.isRevealed(player, cx, cz)) {
        return;
      }
      int floorSection = rm.hideBelowY() >> 4;
      int minSection = rm.worldMinY() >> 4;
      var light = wrapper.getLightData();
      if (light == null) {
        return;
      }
      LightDebugProtection.stripFloorLight(light, minSection, floorSection);
      event.markForReEncode(true);
    } catch (Throwable e) {
      LogData.get().warning("[hider] update-light scrub crashed for "
          + player.getName() + ": " + e);
    }
  }

  private void dispatchUnloadChunk(PacketSendEvent event, Player player) {
    try {
      WrapperPlayServerUnloadChunk wrapper = new WrapperPlayServerUnloadChunk(event);
      rm.markChunkUnloaded(player.getUniqueId(), wrapper.getChunkX(0), wrapper.getChunkZ(0));
    } catch (Throwable ignored) {
    }
  }

  private void dispatchBlockChange(PacketSendEvent event, Player player) {
    WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
    var pos = wrapper.getBlockPosition();
    if (pos == null) {
      return;
    }
    int x = pos.getX();
    int y = pos.getY();
    int z = pos.getZ();

    if (y < rm.upperBarrierY()) {
      int cx = x >> 4, cz = z >> 4;
      if (!rm.isRevealed(player, cx, cz)) {
        event.setCancelled(true);
        return;
      }
    }

    if (deepslate.rewriteBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return;
    }
    if (deepslate.maskTileBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return;
    }
    if (amethyst.rewriteBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
      return;
    }
  }

  private void dispatchMultiBlockChange(PacketSendEvent event, Player player) {
    WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
    if (!deepslate.isWrapperRelevant(wrapper)) {
      return;
    }
    WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = wrapper.getBlocks();
    if (blocks == null || blocks.length == 0) {
      return;
    }

    var section = wrapper.getChunkPosition();
    if (section == null) {
      return;
    }
    int cx = section.getX();
    int cz = section.getZ();
    boolean chunkRevealed = rm.isRevealed(player, cx, cz);
    boolean upperRevealed = rm.isUpperRevealed(player, cx, cz);
    int salt = rm.saltFor(player.getUniqueId());

    int floorFixes = 0;
    for (WrapperPlayServerMultiBlockChange.EncodedBlock enc : blocks) {
      if (deepslate.shouldMaskMultiBlock(enc.getY(), chunkRevealed, upperRevealed)) {
        int wantId = deepslate.floorIdAt(salt, enc.getX(), enc.getY(), enc.getZ());
        if (enc.getBlockId() != wantId) {
          enc.setBlockId(wantId);
          floorFixes++;
        }
      }
    }

    int tileFixes = deepslate.maskTilesMultiBlock(wrapper, player);
    int amFixes = amethyst.rewriteMultiBlock(wrapper, player, chunkRevealed, floorFixes);

    if (floorFixes > 0 || amFixes > 0 || tileFixes > 0) {
      event.markForReEncode(true);
    }
  }

  private void dispatchSpawnEntity(PacketSendEvent event, Player player) {
    WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
    var entityType = wrapper.getEntityType();
    if (entityType == null) {
      return;
    }

    if (entityType == EntityTypes.PLAYER) {
      return;
    }

    var position = wrapper.getPosition();
    if (position == null) {
      return;
    }
    double y = position.getY();

    if (y < rm.upperBarrierY()) {
      int cx = (int) Math.floor(position.getX()) >> 4;
      int cz = (int) Math.floor(position.getZ()) >> 4;
      if (!rm.isRevealed(player, cx, cz)) {
        event.setCancelled(true);
      }
    }
  }
}
