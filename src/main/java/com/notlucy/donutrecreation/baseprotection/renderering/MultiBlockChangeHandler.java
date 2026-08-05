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

  public void handle(PacketSendEvent event, Player player) {
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
    int spawnerFixes = 0;
    int floorId = deepslate.floorId();
    int spawnerId = deepslate.spawnerId();
    for (WrapperPlayServerMultiBlockChange.EncodedBlock enc : blocks) {
      if (ghostBlockManager != null
          && ghostBlockManager.hasGhostBlockAt(player.getUniqueId(), enc.getX(), enc.getY(), enc.getZ())) {
        continue;
      }
      if (deepslate.shouldMaskMultiBlock(enc.getY(), chunkRevealed, upperRevealed)) {
        int wantId = deepslate.floorIdAt(salt, enc.getX(), enc.getY(), enc.getZ());
        if (enc.getBlockId() != wantId) {
          enc.setBlockId(wantId);
          floorFixes++;
        }
      } else if (enc.getY() >= rm.hideBelowY() && enc.getBlockId() == spawnerId) {
        enc.setBlockId(floorId);
        spawnerFixes++;
      }
    }

    int tileFixes = deepslate.maskTilesMultiBlock(wrapper, player);
    int amFixes = amethyst.rewriteMultiBlock(wrapper, player, chunkRevealed, floorFixes);

    if (floorFixes > 0 || spawnerFixes > 0 || amFixes > 0 || tileFixes > 0) {
      event.markForReEncode(true);
    }
  }
}
