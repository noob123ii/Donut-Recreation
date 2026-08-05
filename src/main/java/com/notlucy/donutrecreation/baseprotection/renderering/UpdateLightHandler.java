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

  public void handle(PacketSendEvent event, Player player) {
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
}
