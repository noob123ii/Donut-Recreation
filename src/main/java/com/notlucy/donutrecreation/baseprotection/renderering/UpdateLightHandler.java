package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateLight;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.util.LogData;

public final class UpdateLightHandler {
  private final RevealManager revealManager;

  public UpdateLightHandler(RevealManager revealManager) {
    this.revealManager = revealManager;
  }

  public boolean handlePacket(PacketSendEvent event, Player player) {
    try {
      WrapperPlayServerUpdateLight wrapper = new WrapperPlayServerUpdateLight(event);
      int chunkX = wrapper.getChunkX(0), chunkZ = wrapper.getChunkZ(0);
      var light = wrapper.getLightData();
      if (light == null) {
        return false;
      }
      int minSection = revealManager.worldMinY() >> 4;
      int floorSection = (revealManager.hideBelowY() - 1) >> 4;
      int upperY = revealManager.upperBarrierY();
      int upperSection = (upperY - 1) >> 4;
      boolean touched = false;

      if (!revealManager.isChunkRevealed(player, chunkX, chunkZ)) {
        LightDebugProtection.stripSectionRange(light, minSection, floorSection, minSection);
        touched = true;
      }
      if (upperY > revealManager.hideBelowY() && !revealManager.isBarrierBandRevealed(player, chunkX, chunkZ)) {
        LightDebugProtection.stripSectionRange(
            light, Math.max(minSection, floorSection), upperSection, minSection);
        touched = true;
      }
      if (touched) {
        event.markForReEncode(true);
      }
      return touched;
    } catch (Throwable e) {
      LogData.get().warning("[hider] light scrub crashed for " + player.getName() + ": " + e);
      return false;
    }
  }
}
