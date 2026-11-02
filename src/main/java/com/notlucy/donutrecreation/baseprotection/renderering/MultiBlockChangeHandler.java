package com.notlucy.donutrecreation.baseprotection.renderering;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import org.bukkit.entity.Player;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;

public final class MultiBlockChangeHandler {
  private final RevealManager revealManager;
  private final DeepslateProtection deepslate;
  private final AmethystProtection amethyst;
  private GhostBlockManager ghostBlockManager;

  public MultiBlockChangeHandler(RevealManager revealManager, DeepslateProtection deepslate, AmethystProtection amethyst) {
    this.revealManager = revealManager;
    this.deepslate = deepslate;
    this.amethyst = amethyst;
  }

  public void setGhostBlockManager(GhostBlockManager gbm) {
    this.ghostBlockManager = gbm;
  }

  public boolean handlePacket(PacketSendEvent event, Player player) {
    WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
    if (!deepslate.isWrapperRelevant(wrapper)) {
      return false;
    }
    var section = wrapper.getChunkPosition();
    if (section == null) {
      return false;
    }
    int chunkX = section.getX(), chunkZ = section.getZ();
    boolean upperRevealed = revealManager.isBarrierBandRevealed(player, chunkX, chunkZ);
    int salt = revealManager.getPlayerSalt(player.getUniqueId());
    int floorY = revealManager.hideBelowY();
    int upperY = revealManager.upperBarrierY();
    int spawnerMaskAboveY = revealManager.spawnerMaskAboveY();
    boolean checkGhosts = revealManager.hasAnyGhostBlocks(player.getUniqueId());
    int floorFixes = 0, spawnerFixes = 0;
    int spawnerId = deepslate.spawnerId();

    for (var encodedBlock : wrapper.getBlocks()) {
      int blockY = encodedBlock.getY();
      int blockX = encodedBlock.getX();
      int blockZ = encodedBlock.getZ();
      if (checkGhosts && ghostBlockManager != null
          && ghostBlockManager.hasGhostBlockAt(player.getUniqueId(), blockX, blockY, blockZ)) {
        continue;
      }
      boolean belowFloor = blockY < floorY;
      boolean barrierBand = !belowFloor && blockY < upperY;
      boolean visible = belowFloor
          ? revealManager.isCaveBlockVisible(player, blockX, blockY, blockZ)
          : (!barrierBand || upperRevealed);
      if (!visible && (belowFloor || barrierBand)) {
        boolean bandSpawner = barrierBand && encodedBlock.getBlockId() == spawnerId;
        if (!bandSpawner) {
          int want = deepslate.floorIdAt(salt, blockX, blockY, blockZ);
          if (encodedBlock.getBlockId() != want) {
            encodedBlock.setBlockId(want);
            floorFixes++;
          }
        }
      } else if (blockY > spawnerMaskAboveY
          && encodedBlock.getBlockId() == spawnerId
          && !deepslate.isNearSpawner(player, blockX, blockY, blockZ)) {
        encodedBlock.setBlockId(deepslate.stoneId());
        spawnerFixes++;
      }
    }
    int amFixes = amethyst.rewriteMultiBlock(wrapper, player, false, floorFixes);

    boolean touched = floorFixes > 0 || spawnerFixes > 0 || amFixes > 0;
    if (touched) {
      event.markForReEncode(true);
    }
    return touched;
  }
}
