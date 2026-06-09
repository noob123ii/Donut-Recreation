package com.notlucy.donutrecreation.baseprotection.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.ParticleDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.SoundDamper;
import com.notlucy.donutrecreation.util.LogData;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class PacketHider {

  private final RevealManager rm;
  private final BlockIdRegistry registry;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private final BlockEntityDebugProtection tiles;
  private final SoundDamper sounds;
  private final ParticleDamper particles;
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
          case PacketType.Play.Server.BLOCK_CHANGE -> dispatchBlockChange(event, player);
          case PacketType.Play.Server.MULTI_BLOCK_CHANGE -> dispatchMultiBlockChange(event, player);
          case PacketType.Play.Server.BLOCK_ENTITY_DATA ->
              tiles.handleBlockEntityData(event, player);
          case PacketType.Play.Server.SOUND_EFFECT,
               PacketType.Play.Server.ENTITY_SOUND_EFFECT -> sounds.handle(event, player);
          case PacketType.Play.Server.PARTICLE -> particles.handle(event, player);
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
    if (rewrote[0]) {
      event.markForReEncode(true);
    }
    // Mark this chunk as delivered to the player AFTER processing so subsequent
    // multi-block-change packets aren't dropped on a chunk the client doesn't
    // yet have. This is the key fix for "caves don't load until I relog".
    rm.markChunkDelivered(player.getUniqueId(), cx, cz);
    if (rm.verboseLogging()) {
      boolean didRewrite = rewrote[0];
      int sections = wrapper.getColumn().getChunks().length;
      boolean hasLight = wrapper.getLightData() != null;
      LogData.get().info("[hider] chunk " + cx + "," + cz + " sections=" + sections
          + " rewrote=" + didRewrite + " hasLight=" + hasLight
          + " player=" + player.getName());
    }
  }

  private void dispatchUnloadChunk(PacketSendEvent event, Player player) {
    try {
      WrapperPlayServerUnloadChunk wrapper = new WrapperPlayServerUnloadChunk(event);
      rm.markChunkUnloaded(player.getUniqueId(), wrapper.getChunkX(), wrapper.getChunkZ());
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
}
