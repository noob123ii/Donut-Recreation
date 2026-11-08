/*
 * Copyright (C) 2026 Notlucy and Donut Recreation contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.notlucy.donutrecreation.baseprotection.protection;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.packet.BlockIdRegistry;

/**
 * Hides buried ores and reveals ores that touch air. Works on packet data so the real world
 * stays unchanged, and pushes updates when blocks are mined.
 */
public final class OreVisibility {

  private static final Set<Material> NATURAL_ORES = EnumSet.of(
      Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
      Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
      Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
      Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
      Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
      Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
      Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
      Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
      Material.ANCIENT_DEBRIS, Material.NETHER_GOLD_ORE,
      Material.NETHER_QUARTZ_ORE, Material.GILDED_BLACKSTONE);

  private static final Set<String> NON_SOLID_NAMES = Set.of(
      "air", "cave_air", "void_air", "water", "lava",
      "glass", "glass_pane", "tinted_glass",
      "white_stained_glass", "orange_stained_glass", "magenta_stained_glass",
      "light_blue_stained_glass", "yellow_stained_glass", "lime_stained_glass",
      "pink_stained_glass", "gray_stained_glass", "light_gray_stained_glass",
      "cyan_stained_glass", "purple_stained_glass", "blue_stained_glass",
      "brown_stained_glass", "green_stained_glass", "red_stained_glass",
      "black_stained_glass",
      "white_stained_glass_pane", "orange_stained_glass_pane", "magenta_stained_glass_pane",
      "light_blue_stained_glass_pane", "yellow_stained_glass_pane", "lime_stained_glass_pane",
      "pink_stained_glass_pane", "gray_stained_glass_pane", "light_gray_stained_glass_pane",
      "cyan_stained_glass_pane", "purple_stained_glass_pane", "blue_stained_glass_pane",
      "brown_stained_glass_pane", "green_stained_glass_pane", "red_stained_glass_pane",
      "black_stained_glass_pane",
      "torch", "wall_torch", "soul_torch", "soul_wall_torch",
      "redstone_torch", "redstone_wall_torch",
      "ladder", "rail", "powered_rail", "detector_rail", "activator_rail",
      "lever", "stone_button", "oak_button", "spruce_button", "birch_button",
      "jungle_button", "acacia_button", "dark_oak_button", "mangrove_button",
      "cherry_button", "bamboo_button", "crimson_button", "warped_button",
      "polished_blackstone_button",
      "redstone_wire", "tripwire", "tripwire_hook",
      "oak_sign", "spruce_sign", "birch_sign", "jungle_sign", "acacia_sign",
      "dark_oak_sign", "mangrove_sign", "cherry_sign", "bamboo_sign",
      "crimson_sign", "warped_sign",
      "oak_wall_sign", "spruce_wall_sign", "birch_wall_sign", "jungle_wall_sign",
      "acacia_wall_sign", "dark_oak_wall_sign", "mangrove_wall_sign", "cherry_wall_sign",
      "bamboo_wall_sign", "crimson_wall_sign", "warped_wall_sign",
      "oak_hanging_sign", "spruce_hanging_sign", "birch_hanging_sign", "jungle_hanging_sign",
      "acacia_hanging_sign", "dark_oak_hanging_sign", "mangrove_hanging_sign",
      "cherry_hanging_sign", "bamboo_hanging_sign", "crimson_hanging_sign",
      "warped_hanging_sign",
      "iron_bars", "chain", "iron_chain", "copper_chain",
      "scaffolding", "cobweb", "snow", "short_grass", "tall_grass", "fern",
      "large_fern", "dead_bush", "vine", "glow_lichen", "seagrass",
      "tall_seagrass", "kelp", "kelp_plant",
      "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
      "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
      "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose",
      "sunflower", "lilac", "rose_bush", "peony",
      "sugar_cane", "bamboo", "bamboo_sapling",
      "nether_portal", "end_portal", "end_gateway",
      "fire", "soul_fire", "spawner", "chest", "trapped_chest", "ender_chest",
      "barrier", "light", "structure_void", "pointed_dripstone",
      "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
      "amethyst_cluster", "lightning_rod",
      "oak_fence", "spruce_fence", "birch_fence", "jungle_fence", "acacia_fence",
      "dark_oak_fence", "mangrove_fence", "cherry_fence", "bamboo_fence",
      "crimson_fence", "warped_fence", "nether_brick_fence",
      "oak_fence_gate", "spruce_fence_gate", "birch_fence_gate", "jungle_fence_gate",
      "acacia_fence_gate", "dark_oak_fence_gate", "mangrove_fence_gate",
      "cherry_fence_gate", "bamboo_fence_gate", "crimson_fence_gate", "warped_fence_gate",
      "brewing_stand", "enchanting_table", "anvil", "chipped_anvil", "damaged_anvil",
      "bell", "lantern", "soul_lantern", "campfire", "soul_campfire",
      "cake", "candle", "white_candle", "orange_candle", "magenta_candle",
      "light_blue_candle", "yellow_candle", "lime_candle", "pink_candle",
      "gray_candle", "light_gray_candle", "cyan_candle", "purple_candle",
      "blue_candle", "brown_candle", "green_candle", "red_candle", "black_candle",
      "flower_pot", "decorated_pot", "lectern", "stonecutter", "grindstone",
      "composter", "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron",
      "hopper", "dragon_egg", "conduit", "end_rod",
      "oak_door", "spruce_door", "birch_door", "jungle_door", "acacia_door",
      "dark_oak_door", "mangrove_door", "cherry_door", "bamboo_door",
      "crimson_door", "warped_door", "iron_door",
      "oak_trapdoor", "spruce_trapdoor", "birch_trapdoor", "jungle_trapdoor",
      "acacia_trapdoor", "dark_oak_trapdoor", "mangrove_trapdoor",
      "cherry_trapdoor", "bamboo_trapdoor", "crimson_trapdoor",
      "warped_trapdoor", "iron_trapdoor",
      "white_bed", "orange_bed", "magenta_bed", "light_blue_bed", "yellow_bed",
      "lime_bed", "pink_bed", "gray_bed", "light_gray_bed", "cyan_bed",
      "purple_bed", "blue_bed", "brown_bed", "green_bed", "red_bed", "black_bed",
      "player_head", "player_wall_head", "zombie_head", "zombie_wall_head",
      "skeleton_skull", "skeleton_wall_skull", "wither_skeleton_skull",
      "wither_skeleton_wall_skull", "creeper_head", "creeper_wall_head",
      "dragon_head", "dragon_wall_head", "piglin_head", "piglin_wall_head");

  private static final int[][] NEIGHBORS = {
      {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
  };

  private final RevealManager revealManager;
  private final BlockIdRegistry blockIds;
  private final Listener listener = new OreListener();
  private volatile boolean running;
  private final Queue<BlockKey> pending = new ArrayDeque<>();
  private int updateTaskId = -1;

  private record BlockKey(World world, int x, int y, int z) {
  }

  public OreVisibility(RevealManager revealManager, BlockIdRegistry blockIds) {
    this.revealManager = revealManager;
    this.blockIds = blockIds;
  }

  public void start() {
    if (running) {
      return;
    }
    running = true;
    Bukkit.getPluginManager().registerEvents(listener, revealManager.plugin());
    updateTaskId = Bukkit.getScheduler()
        .runTaskTimer(revealManager.plugin(), this::flushUpdates, 1L, 1L)
        .getTaskId();
  }

  public void stop() {
    running = false;
    HandlerList.unregisterAll(listener);
    if (updateTaskId != -1) {
      Bukkit.getScheduler().cancelTask(updateTaskId);
      updateTaskId = -1;
    }
    synchronized (pending) {
      pending.clear();
    }
  }

  /** Rewrites ores in chunk data: buried ores become stone, exposed ores stay real. */
  public boolean rewriteChunk(WrapperPlayServerChunkData wrapper, Player player) {
    if (!running) {
      return false;
    }
    World world = player.getWorld();
    var column = wrapper.getColumn();
    BaseChunk[] sections = column.getChunks();
    if (sections == null) {
      return false;
    }
    int chunkX = column.getX();
    int chunkZ = column.getZ();
    int minSection = world.getMinHeight() >> 4;
    boolean touched = false;

    for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
      BaseChunk section = sections[sectionIndex];
      if (section == null) {
        continue;
      }
      int worldOriginY = (minSection + sectionIndex) << 4;
      for (int x = 0; x < 16; x++) {
        for (int z = 0; z < 16; z++) {
          for (int y = 0; y < 16; y++) {
            int id = section.getBlockId(x, y, z);
            if (!blockIds.isNaturalOre(id)) {
              continue;
            }
            int worldY = worldOriginY + y;
            if (isExposedInPacket(sections, sectionIndex, minSection, world, x, y, z,
                chunkX, chunkZ)) {
              continue;
            }
            section.set(x, y, z, coverId(id, worldY, world));
            touched = true;
          }
        }
      }
    }
    return touched;
  }

  private boolean isExposedInPacket(
      BaseChunk[] sections, int sectionIndex, int minSection, World world,
      int localX, int localY, int localZ, int chunkX, int chunkZ) {
    int worldY = ((minSection + sectionIndex) << 4) + localY;
    for (int[] offset : NEIGHBORS) {
      int nx = localX + offset[0];
      int ny = localY + offset[1];
      int nz = localZ + offset[2];
      int neighborWorldY = worldY + offset[1];
      if (neighborWorldY < world.getMinHeight() || neighborWorldY >= world.getMaxHeight()) {
        return true;
      }
      if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
        int neighborSection = sectionIndex;
        int neighborLocalY = ny;
        if (ny < 0) {
          neighborSection--;
          neighborLocalY = 15;
        } else if (ny > 15) {
          neighborSection++;
          neighborLocalY = 0;
        }
        if (neighborSection < 0 || neighborSection >= sections.length) {
          return true;
        }
        BaseChunk neighbor = sections[neighborSection];
        if (neighbor == null) {
          /* Null section below the floor is treated as solid by the hider; above, unknown. */
          return neighborWorldY >= revealManager.hideBelowY();
        }
        int neighborId = neighbor.getBlockId(nx, neighborLocalY, nz);
        if (isTransparentPacketId(neighborId)) {
          return true;
        }
      } else {
        /* Cross-chunk neighbor: check loaded world state conservatively. */
        int wx = (chunkX << 4) + nx;
        int wz = (chunkZ << 4) + nz;
        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) {
          /* Unknown neighbor - treat as solid to avoid leaking buried ores. */
          continue;
        }
        Material type = world.getBlockAt(wx, neighborWorldY, wz).getType();
        if (isTransparentMaterial(type)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isTransparentPacketId(int globalId) {
    try {
      WrappedBlockState state = WrappedBlockState.getByGlobalId(
          com.github.retrooper.packetevents.PacketEvents.getAPI()
              .getServerManager().getVersion().toClientVersion(),
          globalId, false);
      if (state == null || state.getType() == null) {
        return false;
      }
      return NON_SOLID_NAMES.contains(state.getType().getName());
    } catch (Throwable ignored) {
      return false;
    }
  }

  private boolean isTransparentMaterial(Material type) {
    if (type == null) {
      return false;
    }
    if (type.isAir()) {
      return true;
    }
    return NON_SOLID_NAMES.contains(type.getKey().getKey());
  }

  public boolean rewriteBlockChange(
      WrapperPlayServerBlockChange wrapper, Player player, int x, int y, int z) {
    if (!running) {
      return false;
    }
    WrappedBlockState state = wrapper.getBlockState();
    if (state == null || !blockIds.isNaturalOre(state.getGlobalId())) {
      return false;
    }
    if (isExposedInWorld(player.getWorld(), x, y, z)) {
      return false;
    }
    wrapper.setBlockState(WrappedBlockState.getByGlobalId(
        coverId(state.getGlobalId(), y, player.getWorld())));
    return true;
  }

  public int rewriteMultiBlock(WrapperPlayServerMultiBlockChange wrapper, Player player) {
    if (!running) {
      return 0;
    }
    World world = player.getWorld();
    int changed = 0;
    for (var encoded : wrapper.getBlocks()) {
      if (!blockIds.isNaturalOre(encoded.getBlockId())) {
        continue;
      }
      if (isExposedInWorld(world, encoded.getX(), encoded.getY(), encoded.getZ())) {
        continue;
      }
      encoded.setBlockId(coverId(encoded.getBlockId(), encoded.getY(), world));
      changed++;
    }
    return changed;
  }

  private boolean isExposedInWorld(World world, int x, int y, int z) {
    if (world == null) {
      return true;
    }
    for (int[] offset : NEIGHBORS) {
      int nx = x + offset[0];
      int ny = y + offset[1];
      int nz = z + offset[2];
      if (ny < world.getMinHeight() || ny >= world.getMaxHeight()) {
        return true;
      }
      if (!world.isChunkLoaded(nx >> 4, nz >> 4)) {
        continue;
      }
      Material type = world.getBlockAt(nx, ny, nz).getType();
      if (type.isAir() || isTransparentMaterial(type)) {
        return true;
      }
    }
    return false;
  }

  private boolean isAirLike(World world, int x, int y, int z) {
    if (world == null) {
      return true;
    }
    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
      return true;
    }
    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
      return false;
    }
    Material type = world.getBlockAt(x, y, z).getType();
    return type.isAir() || isTransparentMaterial(type);
  }

  /**
   * Dynamic reveal check: exposed if air/transparent is adjacent, or within 2 blocks with one
   * solid block between (more reliable when mining close by).
   */
  private boolean isExposedForUpdate(World world, int x, int y, int z) {
    if (world == null) {
      return true;
    }
    for (int[] offset : NEIGHBORS) {
      int ax = x + offset[0];
      int ay = y + offset[1];
      int az = z + offset[2];
      if (isAirLike(world, ax, ay, az)) {
        return true;
      }
      int bx = x + offset[0] * 2;
      int by = y + offset[1] * 2;
      int bz = z + offset[2] * 2;
      if (by < world.getMinHeight() || by >= world.getMaxHeight()) {
        continue;
      }
      if (!world.isChunkLoaded(bx >> 4, bz >> 4) || !world.isChunkLoaded(ax >> 4, az >> 4)) {
        continue;
      }
      Material between = world.getBlockAt(ax, ay, az).getType();
      if (between.isAir() || isTransparentMaterial(between)) {
        return true;
      }
      Material far = world.getBlockAt(bx, by, bz).getType();
      if ((far.isAir() || isTransparentMaterial(far))
          && !between.isAir() && !isTransparentMaterial(between)) {
        return true;
      }
    }
    return false;
  }

  private int coverId(int oreId, int y, World world) {
    if (world != null && world.getEnvironment() == World.Environment.NETHER) {
      return blockIds.netherrackId();
    }
    if (y < 0 || blockIds.isDeepslateOre(oreId)) {
      return blockIds.floorId();
    }
    return blockIds.stoneId();
  }

  private void queueUpdate(Block block) {
    if (block == null || !running) {
      return;
    }
    synchronized (pending) {
      pending.add(new BlockKey(block.getWorld(), block.getX(), block.getY(), block.getZ()));
      if (pending.size() > 4096) {
        pending.poll();
      }
    }
  }

  private void flushUpdates() {
    BlockKey key;
    int processed = 0;
    while (processed++ < 256) {
      synchronized (pending) {
        key = pending.poll();
      }
      if (key == null) {
        return;
      }
      updateAround(key.world(), key.x(), key.y(), key.z());
    }
  }

  private void updateAround(World world, int bx, int by, int bz) {
    if (world == null) {
      return;
    }
    int radius = 2;
    double closeSq = 5.0 * 5.0;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dz = -radius; dz <= radius; dz++) {
          int x = bx + dx;
          int y = by + dy;
          int z = bz + dz;
          if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            continue;
          }
          if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            continue;
          }
          Block block = world.getBlockAt(x, y, z);
          if (!NATURAL_ORES.contains(block.getType())) {
            continue;
          }
          Location location = block.getLocation();
          boolean exposed = isExposedForUpdate(world, x, y, z);
          BlockData data = exposed ? block.getBlockData() : coverData(block.getType(), y, world);
          for (Player viewer : world.getPlayers()) {
            if (!viewer.isOnline() || !viewer.getWorld().equals(world)) {
              continue;
            }
            double distSq = viewer.getLocation().distanceSquared(location);
            if (exposed && distSq > closeSq) {
              continue;
            }
            if (!exposed && distSq > 96 * 96) {
              continue;
            }
            if (!revealManager.wasChunkDeliveredTo(viewer.getUniqueId(), x >> 4, z >> 4)) {
              continue;
            }
            try {
              viewer.sendBlockChange(location, data);
            } catch (Throwable ignored) {
            }
          }
        }
      }
    }
  }

  private BlockData coverData(Material ore, int y, World world) {
    if (world != null && world.getEnvironment() == World.Environment.NETHER) {
      return Material.NETHERRACK.createBlockData();
    }
    if (y < 0 || ore.name().startsWith("DEEPSLATE_")) {
      return Material.DEEPSLATE.createBlockData();
    }
    return Material.STONE.createBlockData();
  }

  private final class OreListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
      queueUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
      queueUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
      for (Block block : event.blockList()) {
        queueUpdate(block);
      }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
      for (Block block : event.blockList()) {
        queueUpdate(block);
      }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
      for (Block block : event.getBlocks()) {
        queueUpdate(block);
      }
      queueUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
      for (Block block : event.getBlocks()) {
        queueUpdate(block);
      }
      queueUpdate(event.getBlock());
    }
  }
}
