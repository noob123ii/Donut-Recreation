package com.notlucy.donutrecreation.baseprotection.protection;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class SpawnListener implements Listener {

  private static final Set<SpawnReason> NATURAL_SPAWNS = EnumSet.of(
      SpawnReason.NATURAL,
      SpawnReason.JOCKEY,
      SpawnReason.MOUNT,
      SpawnReason.PATROL,
      SpawnReason.RAID,
      SpawnReason.REINFORCEMENTS,
      SpawnReason.TRAP,
      SpawnReason.VILLAGE_DEFENSE,
      SpawnReason.VILLAGE_INVASION,
      SpawnReason.SLIME_SPLIT,
      SpawnReason.SILVERFISH_BLOCK);

  private final RevealManager revealManager;
  private final boolean killNaturalSpawns;
  private final boolean killAllUnderFloorSpawns;

  public SpawnListener(RevealManager revealManager) {
    this.revealManager = revealManager;
    this.killNaturalSpawns = revealManager.plugin().getConfig()
        .getBoolean("hider.block-natural-spawns-below-hide", true);
    this.killAllUnderFloorSpawns = revealManager.plugin().getConfig()
        .getBoolean("hider.block-all-spawns-below-hide", true);
    LogData.get().info("[hider] block natural spawns under floor: " + killNaturalSpawns
        + " (all reasons: " + killAllUnderFloorSpawns + ")");
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    SpawnReason reason = event.getSpawnReason();
    LivingEntity entity = event.getEntity();
    if (entity.getLocation().getY() >= revealManager.hideBelowY()) {
      return;
    }
    boolean isNatural = reason != null && NATURAL_SPAWNS.contains(reason);
    boolean shouldCancel = killAllUnderFloorSpawns
        || (killNaturalSpawns && isNatural);
    if (!shouldCancel) {
      return;
    }
    event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {

  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void handleChunkUnload(ChunkUnloadEvent event) {
    revealManager.handleChunkUnload(event.getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (isAmethystBlock(block.getType())) {
      revealManager.forgetAmethystBlock(block.getX(), block.getY(), block.getZ());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    Block block = event.getBlockPlaced();
    if (isAmethystBlock(block.getType())) {
      revealManager.rememberAmethystBlock(block.getX(), block.getY(), block.getZ());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockGrow(BlockGrowEvent event) {
    syncOne(event.getBlock(), event.getNewState());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockSpread(BlockSpreadEvent event) {
    syncOne(event.getBlock(), event.getNewState());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockFade(BlockFadeEvent event) {
    syncOne(event.getBlock(), event.getNewState());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockExplode(BlockExplodeEvent event) {
    for (Block block : event.blockList()) {
      if (isAmethystBlock(block.getType())) {
        revealManager.forgetAmethystBlock(block.getX(), block.getY(), block.getZ());
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPhysics(BlockPhysicsEvent event) {
    Block block = event.getBlock();
    if (isAmethystBlock(block.getType())) {
      revealManager.forgetAmethystBlock(block.getX(), block.getY(), block.getZ());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityExplode(EntityExplodeEvent event) {
    for (Block block : event.blockList()) {
      if (isAmethystBlock(block.getType())) {
        revealManager.forgetAmethystBlock(block.getX(), block.getY(), block.getZ());
      }
    }

  }

  private void syncOne(Block before, BlockState after) {
    boolean wasAm = isAmethystBlock(before.getType());
    boolean nowAm = isAmethystBlock(after.getType());
    if (wasAm && !nowAm) {
      revealManager.forgetAmethystBlock(before.getX(), before.getY(), before.getZ());
    } else if (!wasAm && nowAm) {
      revealManager.rememberAmethystBlock(before.getX(), before.getY(), before.getZ());
    }
  }

  private static boolean isAmethystBlock(Material m) {
    return m == Material.AMETHYST_BLOCK
        || m == Material.BUDDING_AMETHYST
        || m == Material.AMETHYST_CLUSTER
        || m == Material.SMALL_AMETHYST_BUD
        || m == Material.MEDIUM_AMETHYST_BUD
        || m == Material.LARGE_AMETHYST_BUD;
  }
}
