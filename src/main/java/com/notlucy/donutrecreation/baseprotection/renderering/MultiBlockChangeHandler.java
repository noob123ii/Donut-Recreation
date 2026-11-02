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
    boolean chunkRevealed = revealManager.isChunkRevealed(player, chunkX, chunkZ);
    boolean upperRevealed = revealManager.isBarrierBandRevealed(player, chunkX, chunkZ);
    int salt = revealManager.getPlayerSalt(player.getUniqueId());
    int floorFixes = 0, spawnerFixes = 0;
    int spawnerId = deepslate.spawnerId();

    for (var encodedBlock : wrapper.getBlocks()) {
      if (ghostBlockManager != null
          && ghostBlockManager.hasGhostBlockAt(player.getUniqueId(), encodedBlock.getX(), encodedBlock.getY(), encodedBlock.getZ())) {
        continue;
      }
      if (deepslate.shouldMaskMultiBlock(encodedBlock.getY(), chunkRevealed, upperRevealed)) {
        boolean bandSpawner = encodedBlock.getY() >= revealManager.hideBelowY() && encodedBlock.getBlockId() == spawnerId;
        if (!bandSpawner) {
          int want = deepslate.floorIdAt(salt, encodedBlock.getX(), encodedBlock.getY(), encodedBlock.getZ());
          if (encodedBlock.getBlockId() != want) {
            encodedBlock.setBlockId(want);
            floorFixes++;
          }
        }
      } else if (encodedBlock.getY() > revealManager.spawnerMaskAboveY() && encodedBlock.getBlockId() == spawnerId
          && !deepslate.isNearSpawner(player, encodedBlock.getX(), encodedBlock.getY(), encodedBlock.getZ())) {
        encodedBlock.setBlockId(deepslate.stoneId());
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
