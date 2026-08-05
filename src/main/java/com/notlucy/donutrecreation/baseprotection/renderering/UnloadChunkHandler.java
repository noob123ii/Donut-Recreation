package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;

public final class UnloadChunkHandler {
  private final RevealManager rm;

  public UnloadChunkHandler(RevealManager rm) {
    this.rm = rm;
  }

  public void handle(PacketSendEvent event, Player player) {
    try {
      WrapperPlayServerUnloadChunk wrapper = new WrapperPlayServerUnloadChunk(event);
      rm.markChunkUnloaded(player.getUniqueId(), wrapper.getChunkX(0), wrapper.getChunkZ(0));
    } catch (Throwable ignored) {
    }
  }
}
