package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateLight;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.util.LogData;

public final class UpdateLightHandler {
  private final RevealManager rm;

  public UpdateLightHandler(RevealManager rm) {
    this.rm = rm;
  }

  public boolean handle(PacketSendEvent event, Player player) {
    try {
      WrapperPlayServerUpdateLight wrapper = new WrapperPlayServerUpdateLight(event);
      int cx = wrapper.getChunkX(0), cz = wrapper.getChunkZ(0);
      if (rm.isRevealed(player, cx, cz)) {
        return false;
      }
      var light = wrapper.getLightData();
      if (light == null) {
        return false;
      }
      LightDebugProtection.stripFloorLight(light, rm.worldMinY() >> 4, rm.hideBelowY() >> 4);
      event.markForReEncode(true);
      return true;
    } catch (Throwable e) {
      LogData.get().warning("[hider] light scrub crashed for " + player.getName() + ": " + e);
      return false;
    }
  }
}
