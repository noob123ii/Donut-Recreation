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
package com.notlucy.donutrecreation.baseprotection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.baseprotection.packet.BlockIdRegistry;
import com.notlucy.donutrecreation.util.LogData;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Tracks player-placed blocks and hides base-like placements from other viewers.
 * Placed blocks at or above {@code placed-hide-above-y} are hidden, and placed
 * containers below {@code chest-hide-below-y} are also hidden.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class PlacedBlockHider {

  private static final Set<String> CONTAINER_SUFFIXES = Set.of(
      "_chest", "_barrel", "_shulker_box");

  private static final Set<String> CONTAINER_NAMES = Set.of(
      "chest", "trapped_chest", "ender_chest", "barrel",
      "hopper", "dropper", "dispenser", "furnace", "blast_furnace",
      "smoker", "brewing_stand");

  private static final class WorldData {
    final ConcurrentHashMap<Long, UUID> byPos = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Long, LongOpenHashSet> byChunk = new ConcurrentHashMap<>();
  }

  private static final UUID SYSTEM_OWNER = new UUID(0L, 0L);

  private final DonutRecreation plugin;
  private final Map<UUID, WorldData> worlds = new ConcurrentHashMap<>();
  private final boolean enabled;
  private final int placedAboveY;
  private final int chestBelowY;
  private final boolean hideFromOwner;
  private final boolean scanContainers;

  public PlacedBlockHider(DonutRecreation plugin) {
    this.plugin = plugin;
    var cfg = plugin.getConfig();
    this.enabled = cfg.getBoolean("hider.placed-hide-enabled", true);
    this.placedAboveY = cfg.getInt("hider.placed-hide-above-y", 60);
    this.chestBelowY = cfg.getInt("hider.chest-hide-below-y", 0);
    this.hideFromOwner = cfg.getBoolean("hider.placed-hide-owner", false);
    this.scanContainers = cfg.getBoolean("hider.hide-detected-containers", true);
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean shouldHideMaterialAt(boolean placed, Material material, int y) {
    if (!enabled || !placed) {
      return false;
    }
    if (y >= placedAboveY) {
      return true;
    }
    return y < chestBelowY && isContainerMaterial(material);
  }

  private boolean shouldHideForOwner(UUID owner, UUID viewerId) {
    if (owner == null) {
      return false;
    }
    if (hideFromOwner) {
      return true;
    }
    if (SYSTEM_OWNER.equals(owner)) {
      return true;
    }
    return !owner.equals(viewerId);
  }

  private boolean shouldHideStateAt(boolean placed, String stateName, int y) {
    if (!enabled || !placed) {
      return false;
    }
    if (y >= placedAboveY) {
      return true;
    }
    return y < chestBelowY && isContainerName(stateName);
  }

  public boolean isTrackedPlaced(UUID worldId, int x, int y, int z) {
    WorldData data = worlds.get(worldId);
    return data != null && data.byPos.containsKey(RevealManager.packPos(x, y, z));
  }

  public UUID ownerAt(UUID worldId, int x, int y, int z) {
    WorldData data = worlds.get(worldId);
    return data == null ? null : data.byPos.get(RevealManager.packPos(x, y, z));
  }

  public boolean shouldHideBlockChange(Player viewer, UUID worldId, int x, int y, int z,
      int globalId, ClientVersion version) {
    if (!enabled || viewer == null || worldId == null) {
      return false;
    }
    WorldData data = worlds.get(worldId);
    if (data == null) {
      return false;
    }
    UUID owner = data.byPos.get(RevealManager.packPos(x, y, z));
    if (!shouldHideForOwner(owner, viewer.getUniqueId())) {
      return false;
    }
    return shouldHideStateAt(true, stateName(globalId, version), y);
  }

  public boolean rewriteBlockChange(WrapperPlayServerBlockChange wrapper, Player viewer,
      BlockIdRegistry blockIds) {
    if (!enabled || viewer == null) {
      return false;
    }
    var position = wrapper.getBlockPosition();
    if (position == null) {
      return false;
    }
    int x = position.getX();
    int y = position.getY();
    int z = position.getZ();
    WorldData data = worlds.get(viewer.getWorld().getUID());
    if (data == null) {
      return false;
    }
    UUID owner = data.byPos.get(RevealManager.packPos(x, y, z));
    if (!shouldHideForOwner(owner, viewer.getUniqueId())) {
      return false;
    }
    var state = wrapper.getBlockState();
    String name = state == null || state.getType() == null ? ""
        : safeName(state.getType().getName());
    if (!shouldHideStateAt(true, name, y)) {
      return false;
    }
    wrapper.setBlockState(WrappedBlockState.getByGlobalId(
        coverGlobalId(viewer.getWorld(), y, blockIds)));
    return true;
  }

  public int rewriteMultiBlock(WrapperPlayServerMultiBlockChange wrapper, Player viewer,
      BlockIdRegistry blockIds) {
    if (!enabled || viewer == null) {
      return 0;
    }
    WorldData data = worlds.get(viewer.getWorld().getUID());
    if (data == null || data.byPos.isEmpty()) {
      return 0;
    }
    ClientVersion version = blockIds.version();
    int changed = 0;
    for (var encoded : wrapper.getBlocks()) {
      int x = encoded.getX();
      int y = encoded.getY();
      int z = encoded.getZ();
      UUID owner = data.byPos.get(RevealManager.packPos(x, y, z));
      if (!shouldHideForOwner(owner, viewer.getUniqueId())) {
        continue;
      }
      if (!shouldHideStateAt(true, stateName(encoded.getBlockId(), version), y)) {
        continue;
      }
      encoded.setBlockId(coverGlobalId(viewer.getWorld(), y, blockIds));
      changed++;
    }
    return changed;
  }

  public boolean rewriteChunk(WrapperPlayServerChunkData wrapper, Player viewer,
      BlockIdRegistry blockIds) {
    if (!enabled || viewer == null) {
      return false;
    }
    World world = viewer.getWorld();
    WorldData data = worlds.get(world.getUID());
    if (data == null || data.byPos.isEmpty()) {
      return false;
    }
    var column = wrapper.getColumn();
    int chunkX = column.getX();
    int chunkZ = column.getZ();
    LongOpenHashSet positions = data.byChunk.get(RevealManager.chunkKey(chunkX, chunkZ));
    if (positions == null || positions.isEmpty()) {
      return false;
    }
    BaseChunk[] sections = column.getChunks();
    if (sections == null) {
      return false;
    }
    int minSection = world.getMinHeight() >> 4;
    ClientVersion version = blockIds.version();
    UUID viewerId = viewer.getUniqueId();
    boolean touched = false;
    long[] snapshot;
    synchronized (positions) {
      snapshot = positions.toLongArray();
    }
    for (long packed : snapshot) {
      UUID owner = data.byPos.get(packed);
      if (!shouldHideForOwner(owner, viewerId)) {
        continue;
      }
      int x = RevealManager.unpackX(packed);
      int y = RevealManager.unpackY(packed);
      int z = RevealManager.unpackZ(packed);
      int sectionIndex = (y >> 4) - minSection;
      if (sectionIndex < 0 || sectionIndex >= sections.length) {
        continue;
      }
      BaseChunk section = sections[sectionIndex];
      if (section == null) {
        continue;
      }
      int lx = x & 15;
      int ly = y & 15;
      int lz = z & 15;
      int currentId = section.getBlockId(lx, ly, lz);
      if (!shouldHideStateAt(true, stateName(currentId, version), y)) {
        continue;
      }
      section.set(lx, ly, lz, coverGlobalId(world, y, blockIds));
      touched = true;
    }
    return touched;
  }

  public int coverGlobalId(World world, int y, BlockIdRegistry blockIds) {
    if (world != null && world.getEnvironment() == World.Environment.NETHER) {
      return blockIds.netherrackId();
    }
    if (y < 0) {
      return blockIds.floorId();
    }
    return blockIds.stoneId();
  }

  public Material coverMaterial(World world, int y) {
    if (world != null && world.getEnvironment() == World.Environment.NETHER) {
      return Material.NETHERRACK;
    }
    if (y < 0) {
      return Material.DEEPSLATE;
    }
    return Material.STONE;
  }

  public void trackPlaced(Player placer, Block block) {
    if (!enabled || placer == null || block == null) {
      return;
    }
    World world = block.getWorld();
    int y = block.getY();
    if (!shouldHideMaterialAt(true, block.getType(), y)) {
      return;
    }
    trackInternal(world, block.getX(), y, block.getZ(), placer.getUniqueId());
    sendHideToOthers(placer, world, block.getX(), y, block.getZ());
  }

  private void trackInternal(World world, int x, int y, int z, UUID owner) {
    UUID worldId = world.getUID();
    WorldData data = worlds.computeIfAbsent(worldId, ignored -> new WorldData());
    long packed = RevealManager.packPos(x, y, z);
    data.byPos.put(packed, owner == null ? SYSTEM_OWNER : owner);
    long ck = RevealManager.chunkKey(x >> 4, z >> 4);
    LongOpenHashSet set = data.byChunk.computeIfAbsent(ck, ignored -> new LongOpenHashSet());
    synchronized (set) {
      set.add(packed);
    }
  }

  /**
   * Scans tile entities in a chunk and tracks containers that match hide rules.
   * This catches command-placed/existing containers (e.g. chests) without requiring BlockPlaceEvent.
   */
  public void scanChunk(org.bukkit.Chunk chunk) {
    if (!enabled || !scanContainers || chunk == null) {
      return;
    }
    World world = chunk.getWorld();
    try {
      Material coverCache = null;
      for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
        if (state == null) {
          continue;
        }
        Material type = state.getType();
        int y = state.getY();
        boolean hide = false;
        if (y < chestBelowY && isContainerMaterial(type)) {
          hide = true;
        } else if (y >= placedAboveY && isContainerMaterial(type)) {
          hide = true;
        }
        if (!hide) {
          continue;
        }
        int x = state.getX();
        int z = state.getZ();
        long packed = RevealManager.packPos(x, y, z);
        WorldData data = worlds.computeIfAbsent(world.getUID(), ignored -> new WorldData());
        UUID prev = data.byPos.putIfAbsent(packed, SYSTEM_OWNER);
        long ck = RevealManager.chunkKey(chunk.getX(), chunk.getZ());
        LongOpenHashSet set = data.byChunk.computeIfAbsent(ck, ignored -> new LongOpenHashSet());
        synchronized (set) {
          set.add(packed);
        }
        if (prev == null) {
          // Newly detected: push a hide update to current viewers.
          Material cover = coverMaterial(world, y);
          org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z);
          for (Player viewer : world.getPlayers()) {
            if (!viewer.isOnline()) {
              continue;
            }
            try {
              viewer.sendBlockChange(loc, cover.createBlockData());
            } catch (Throwable ignored2) {
            }
          }
        }
      }
    } catch (Throwable ignored) {
      // Chunk/tile access can throw during load/unload races.
    }
  }

  public void untrack(World world, int x, int y, int z) {
    if (!enabled || world == null) {
      return;
    }
    WorldData data = worlds.get(world.getUID());
    if (data == null) {
      return;
    }
    long packed = RevealManager.packPos(x, y, z);
    if (data.byPos.remove(packed) == null) {
      return;
    }
    long ck = RevealManager.chunkKey(x >> 4, z >> 4);
    LongOpenHashSet set = data.byChunk.get(ck);
    if (set != null) {
      synchronized (set) {
        set.remove(packed);
        if (set.isEmpty()) {
          data.byChunk.remove(ck, set);
        }
      }
    }
  }

  public void untrackIfLoaded(World world, int x, int y, int z) {
    if (world != null && world.isChunkLoaded(x >> 4, z >> 4)) {
      untrack(world, x, y, z);
    }
  }

  private void sendHideToOthers(Player placer, World world, int x, int y, int z) {
    Material cover = coverMaterial(world, y);
    org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z);
    for (Player viewer : world.getPlayers()) {
      if (viewer.getUniqueId().equals(placer.getUniqueId()) && !hideFromOwner) {
        continue;
      }
      if (!viewer.isOnline()) {
        continue;
      }
      try {
        viewer.sendBlockChange(loc, cover.createBlockData());
      } catch (Throwable ignored) {
      }
    }
  }

  public boolean isContainerMaterial(Material material) {
    if (material == null) {
      return false;
    }
    return isContainerName(material.name());
  }

  public boolean isContainerName(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    if (CONTAINER_NAMES.contains(lower)) {
      return true;
    }
    if (lower.contains("shulker_box")) {
      return true;
    }
    for (String suffix : CONTAINER_SUFFIXES) {
      if (lower.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static String safeName(String name) {
    return name == null ? "" : name.toLowerCase(Locale.ROOT);
  }

  private static String stateName(int globalId, ClientVersion version) {
    try {
      WrappedBlockState state = WrappedBlockState.getByGlobalId(version, globalId, false);
      if (state == null || state.getType() == null) {
        return "";
      }
      return safeName(state.getType().getName());
    } catch (Throwable ignored) {
      return "";
    }
  }

  public void save() {
    if (!enabled) {
      return;
    }
    Path file = storageFile();
    try {
      Files.createDirectories(file.getParent());
      try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
          Files.newOutputStream(file, StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)))) {
        out.writeInt(1);
        int worldsCount = 0;
        for (var entry : worlds.entrySet()) {
          if (entry.getValue() != null && !entry.getValue().byPos.isEmpty()) {
            worldsCount++;
          }
        }
        out.writeInt(worldsCount);
        int total = 0;
        for (var entry : worlds.entrySet()) {
          WorldData data = entry.getValue();
          if (data == null || data.byPos.isEmpty()) {
            continue;
          }
          out.writeLong(entry.getKey().getMostSignificantBits());
          out.writeLong(entry.getKey().getLeastSignificantBits());
          out.writeInt(data.byPos.size());
          for (var blockEntry : data.byPos.entrySet()) {
            out.writeLong(blockEntry.getKey());
            UUID owner = blockEntry.getValue();
            out.writeLong(owner == null ? 0L : owner.getMostSignificantBits());
            out.writeLong(owner == null ? 0L : owner.getLeastSignificantBits());
            total++;
          }
        }
        LogData.get().info("[hider] saved placed-block hide cache (" + total + " blocks)");
      }
    } catch (IOException e) {
      LogData.get().warning("[hider] failed to save placed-block cache: " + e);
    }
  }

  public void load() {
    if (!enabled) {
      return;
    }
    Path file = storageFile();
    if (!Files.exists(file)) {
      return;
    }
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(
        Files.newInputStream(file)))) {
      int ver = in.readInt();
      if (ver != 1) {
        return;
      }
      int worldCount = in.readInt();
      int total = 0;
      for (int w = 0; w < worldCount; w++) {
        UUID worldId = new UUID(in.readLong(), in.readLong());
        int count = in.readInt();
        WorldData data = new WorldData();
        for (int i = 0; i < count; i++) {
          long pos = in.readLong();
          UUID owner = new UUID(in.readLong(), in.readLong());
          data.byPos.put(pos, owner);
          long ck = RevealManager.chunkKey(
              RevealManager.unpackX(pos) >> 4, RevealManager.unpackZ(pos) >> 4);
          data.byChunk.computeIfAbsent(ck, ignored -> new LongOpenHashSet());
          LongOpenHashSet set = data.byChunk.get(ck);
          synchronized (set) {
            set.add(pos);
          }
          total++;
        }
        worlds.put(worldId, data);
      }
      LogData.get().info("[hider] loaded placed-block hide cache (" + total + " blocks)");
    } catch (Exception e) {
      LogData.get().warning("[hider] failed to load placed-block cache: " + e);
    }
  }

  public void clear() {
    worlds.clear();
  }

  private Path storageFile() {
    return plugin.getDataFolder().toPath().resolve("baseprotection").resolve("placed-blocks.dat");
  }
}
