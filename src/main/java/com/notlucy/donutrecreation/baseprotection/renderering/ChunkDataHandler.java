package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.util.LogData;

public final class ChunkDataHandler {
  private final RevealManager revealManager;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private final BlockEntityDebugProtection blockEntities;

  public ChunkDataHandler(RevealManager revealManager, DeepslateProtection deepslate,
      AmethystProtection amethyst, BlockEntityDebugProtection blockEntities) {
    this.revealManager = revealManager;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
    this.blockEntities = blockEntities;
  }

  public boolean handlePacket(PacketSendEvent event, Player player) {
    WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
    int chunkX = wrapper.getColumn().getX();
    int chunkZ = wrapper.getColumn().getZ();
    boolean touched = false;
    try {
      if (revealManager.geodeHideEnabled()) {
        touched = amethyst.rewriteChunk(wrapper, player);
      }
    } catch (Throwable e) {
      LogData.get().warning("[hider] amethyst rewrite crashed at " + chunkX + "," + chunkZ + ": " + e);
    }
    try {
      touched |= deepslate.rewriteChunk(event, wrapper, player);
    } catch (Throwable e) {
      LogData.get().warning("[hider] deepslate rewrite crashed at " + chunkX + "," + chunkZ + ": " + e);
    }
    try {
      /* Scrub blockEntities even when other rewrites are skipped: they expose containers. */
      touched |= blockEntities.scrubChunk(wrapper.getColumn(), player, chunkX, chunkZ);
    } catch (Throwable e) {
      LogData.get().warning("[hider] tile scrub crashed at " + chunkX + "," + chunkZ + ": " + e);
    }
    if (touched) {
      event.markForReEncode(true);
    }
    revealManager.recordChunkDelivered(player.getUniqueId(), chunkX, chunkZ);
    return touched;
  }
}
