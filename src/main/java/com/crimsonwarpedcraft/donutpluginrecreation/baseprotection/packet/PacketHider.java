package com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.packet;

import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.RevealManager;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.protection.AmethystProtection;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.protection.DeepslateProtection;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.crimsonwarpedcraft.donutpluginrecreation.baseprotection.renderering.SoundDamper;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class PacketHider {

  private final RevealManager rm;
  private final Logger log;
  private final BlockIdRegistry registry;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private final BlockEntityDebugProtection tiles;
  private final SoundDamper sounds;
  private final AtomicInteger failureCount = new AtomicInteger();
  private PacketListenerAbstract listener;

  public PacketHider(RevealManager revealManager) {
    this.rm = revealManager;
    this.log = revealManager.plugin().getLogger();
    this.registry = new BlockIdRegistry();
    this.deepslate = new DeepslateProtection(rm, registry, log);
    this.amethyst = new AmethystProtection(rm, registry, log);
    this.tiles = new BlockEntityDebugProtection(rm);
    this.sounds = new SoundDamper(rm);
  }

  public void register() {
    ClientVersion v = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    registry.rebuild(v);
    log.info("[hider] cached " + registry.amethystCount()
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
          case PacketType.Play.Server.BLOCK_CHANGE -> dispatchBlockChange(event, player);
          case PacketType.Play.Server.MULTI_BLOCK_CHANGE -> dispatchMultiBlockChange(event, player);
          case PacketType.Play.Server.BLOCK_ENTITY_DATA ->
              tiles.handleBlockEntityData(event, player);
          case PacketType.Play.Server.SOUND_EFFECT,
               PacketType.Play.Server.ENTITY_SOUND_EFFECT -> sounds.handle(event, player);
          default -> {}
        }
      } catch (Throwable error) {
        if (failureCount.incrementAndGet() <= 8) {
          log.warning("[hider] packet handler error (" + event.getPacketType() + "): " + error);
        }
      }
    }
  }

  private void dispatchChunkData(PacketSendEvent event, Player player) {
    WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
    boolean rewrote = deepslate.rewriteChunk(event, wrapper, player);
    if (rm.geodeHideEnabled() && amethyst.rewriteChunk(wrapper, player)) {
      rewrote = true;
    }
    if (rewrote) {
      event.markForReEncode(true);
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
    if (amethyst.rewriteBlockChange(wrapper, player, x, y, z)) {
      event.markForReEncode(true);
    }
  }

  private void dispatchMultiBlockChange(PacketSendEvent event, Player player) {
    WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
    if (!deepslate.isWrapperRelevant(wrapper)) {
      return;
    }
    var blocks = wrapper.getBlocks();
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
    int salt = rm.saltFor(player.getUniqueId());

    int floorFixes = 0;
    for (var enc : blocks) {
      if (deepslate.shouldMaskMultiBlock(enc.getY(), chunkRevealed)) {
        int wantId = deepslate.floorIdAt(salt, enc.getX(), enc.getY(), enc.getZ());
        if (enc.getBlockId() != wantId) {
          enc.setBlockId(wantId);
          floorFixes++;
        }
      }
    }

    int amFixes = amethyst.rewriteMultiBlock(wrapper, player, chunkRevealed, floorFixes);

    if (floorFixes > 0 || amFixes > 0) {
      event.markForReEncode(true);
    }
  }
}
