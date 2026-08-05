package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.util.LogData;

public final class ChunkDataHandler {
  private final RevealManager rm;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;

  public ChunkDataHandler(RevealManager rm, DeepslateProtection deepslate, AmethystProtection amethyst) {
    this.rm = rm;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
  }

  public void handle(PacketSendEvent event, Player player) {
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
}
