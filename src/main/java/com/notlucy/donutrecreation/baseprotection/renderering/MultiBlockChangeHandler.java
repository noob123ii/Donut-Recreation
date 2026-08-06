package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;

public final class MultiBlockChangeHandler {
  private final RevealManager rm;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private GhostBlockManager ghostBlockManager;

  public MultiBlockChangeHandler(RevealManager rm, DeepslateProtection deepslate, AmethystProtection amethyst) {
    this.rm = rm;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
  }

  public void setGhostBlockManager(GhostBlockManager gbm) {
    this.ghostBlockManager = gbm;
  }

  public boolean handle(PacketSendEvent event, Player player) {
    WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
    if (!deepslate.isWrapperRelevant(wrapper)) {
      return false;
    }
    var section = wrapper.getChunkPosition();
    if (section == null) {
      return false;
    }
    int cx = section.getX(), cz = section.getZ();
    boolean chunkRevealed = rm.isRevealed(player, cx, cz);
    boolean upperRevealed = rm.isUpperRevealed(player, cx, cz);
    int salt = rm.saltFor(player.getUniqueId());
    int floorFixes = 0, spawnerFixes = 0;
    int floorId = deepslate.floorId(), spawnerId = deepslate.spawnerId();

    for (var enc : wrapper.getBlocks()) {
      if (ghostBlockManager != null
          && ghostBlockManager.hasGhostBlockAt(player.getUniqueId(), enc.getX(), enc.getY(), enc.getZ())) {
        continue;
      }
      if (deepslate.shouldMaskMultiBlock(enc.getY(), chunkRevealed, upperRevealed)) {
        int want = deepslate.floorIdAt(salt, enc.getX(), enc.getY(), enc.getZ());
        if (enc.getBlockId() != want) {
          enc.setBlockId(want);
          floorFixes++;
        }
      } else if (enc.getY() >= rm.hideBelowY() && enc.getBlockId() == spawnerId) {
        enc.setBlockId(floorId);
        spawnerFixes++;
      }
    }
    int amFixes = amethyst.rewriteMultiBlock(wrapper, player, chunkRevealed, floorFixes);

    boolean touched = floorFixes > 0 || spawnerFixes > 0 || amFixes > 0;
    if (touched) {
      event.markForReEncode(true);
    }
    return touched;
  }
}
