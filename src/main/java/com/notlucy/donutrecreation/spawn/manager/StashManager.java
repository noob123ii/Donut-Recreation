package com.notlucy.donutrecreation.spawn.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class StashManager {

  private final List<StashTemplate> templates = new ArrayList<>();
  private final File stashDir;

  public StashManager(File dataFolder) {
    this.stashDir = new File(dataFolder, "Stashs");
    loadAll();
  }

  public void reload() {
    templates.clear();
    loadAll();
  }

  public boolean isEmpty() {
    return templates.isEmpty();
  }

  public int count() {
    return templates.size();
  }

  public StashTemplate pickRandom() {
    if (templates.isEmpty()) {
      return null;
    }
    return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
  }

  public StashTemplate getByName(String name) {
    if (name == null || templates.isEmpty()) {
      return null;
    }
    String lowerName = name.toLowerCase(Locale.ROOT);
    for (StashTemplate t : templates) {
      if (t.name.toLowerCase(Locale.ROOT).equals(lowerName)) {
        return t;
      }
    }
    return null;
  }

  public List<String> getTemplateNames() {
    List<String> names = new ArrayList<>(templates.size());
    for (StashTemplate t : templates) {
      names.add(t.name);
    }
    return names;
  }

  private void loadAll() {
    if (!stashDir.exists()) {
      if (!stashDir.mkdirs() && !stashDir.exists()) {
        LogData.get().warning("[stash] could not create dir " + stashDir);
        return;
      }
      writeDefaultTemplates();
    }
    convertLitematics();
    Set<String> referenced = collectReferencedMcFunctions(stashDir);
    List<File> ymlFiles = new ArrayList<>();
    collectYml(stashDir, ymlFiles, referenced);
    for (File file : ymlFiles) {
      try {
        StashTemplate t = loadYaml(file);
        if (t != null && !t.blocks.isEmpty()) {
          templates.add(t);
          LogData.get().info("[stash] loaded '" + t.name
              + "' (" + t.blocks.size() + " blocks)");
        }
      } catch (Throwable e) {
        LogData.get().warning("[stash] failed to load " + file.getName() + ": " + e);
      }
    }
    LogData.get().info("[stash] " + templates.size()
        + " template(s) loaded.");
  }

  private void convertLitematics() {
    scanAndConvert(stashDir, ".litematic",
        (file, yml) -> LitematicConverter.convertIfNeeded(file, yml));
    scanAndConvert(stashDir, ".mcfunction",
        (file, yml) -> McFunctionConverter.convertIfNeeded(file, yml));
  }

  private void scanAndConvert(File dir, String ext, BiConsumer<File, File> converter) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File f : files) {
      if (f.isDirectory()) {
        scanAndConvert(f, ext, converter);
      } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(ext)) {
        String base = f.getName().replaceAll("(?i)" + ext.replace(".", "\\.") + "$", "");
        File convertedDir = new File(f.getParentFile(), "converted");
        if (!convertedDir.exists() && !convertedDir.mkdirs() && !convertedDir.exists()) {
          LogData.get().warning("[stash] could not create dir " + convertedDir);
          continue;
        }
        File yml = new File(convertedDir, base + ".yml");
        converter.accept(f, yml);
      }
    }
  }

  private Set<String> collectReferencedMcFunctions(File dir) {
    Set<String> refs = new HashSet<>();
    File[] files = dir.listFiles();
    if (files == null) {
      return refs;
    }
    for (File f : files) {
      if (f.isDirectory()) {
        refs.addAll(collectReferencedMcFunctions(f));
      } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".mcfunction")) {
        readFunctionRefs(f, refs);
      }
    }
    return refs;
  }

  private void readFunctionRefs(File file, Set<String> refs) {
    try (BufferedReader br = new BufferedReader(
        new FileReader(file, StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.startsWith("function ")) {
          String ref = line.substring(9).trim();
          int colon = ref.indexOf(':');
          if (colon >= 0) {
            ref = ref.substring(colon + 1);
          }
          int slash = ref.lastIndexOf('/');
          if (slash >= 0) {
            ref = ref.substring(slash + 1);
          }
          refs.add(ref.toLowerCase(Locale.ROOT));
        }
      }
    } catch (IOException e) {
      LogData.get().warning("[stash] failed to read " + file.getName() + ": " + e);
    }
  }

  private void collectYml(File dir, List<File> out, Set<String> skip) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File f : files) {
      if (f.isDirectory()) {
        collectYml(f, out, skip);
      } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
        String base = f.getName().replaceAll("(?i)\\.yml$", "")
            .toLowerCase(Locale.ROOT);
        if (!skip.contains(base)) {
          out.add(f);
        }
      }
    }
  }

  private StashTemplate loadYaml(File file) {
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    String name = extractTemplateName(file);
    List<StashBlock> blocks = new ArrayList<>();
    ConfigurationSection blocksSection = yaml.getConfigurationSection("blocks");
    if (blocksSection != null) {
      for (String key : blocksSection.getKeys(false)) {
        ConfigurationSection entry = blocksSection.getConfigurationSection(key);
        if (entry == null) {
          continue;
        }
        int x = entry.getInt("x");
        int y = entry.getInt("y");
        int z = entry.getInt("z");
        String dataStr = entry.getString("data");
        BlockData data = null;
        if (dataStr != null) {
          try {
            data = Bukkit.createBlockData(dataStr);
          } catch (IllegalArgumentException e) {
            LogData.get().fine("[stash] bad block data '" + dataStr
                + "' in " + file.getName());
          }
        }
        if (data == null) {
          String matStr = entry.getString("material", "STONE");
          Material mat;
          try {
            mat = Material.valueOf(matStr.toUpperCase(Locale.ROOT));
          } catch (IllegalArgumentException e) {
            LogData.get().warning("[stash] unknown material '" + matStr
                + "' in " + file.getName());
            continue;
          }
          data = mat.createBlockData();
        }
        if (data.getMaterial() == Material.AIR || data.getMaterial() == Material.CAVE_AIR) {
          blocks.add(new StashBlock(x, y, z, data));
          continue;
        }
        blocks.add(new StashBlock(x, y, z, data));
      }
    }
    List<StashEntity> entities = new ArrayList<>();
    ConfigurationSection entitiesSection = yaml.getConfigurationSection("entities");
    if (entitiesSection != null) {
      for (String key : entitiesSection.getKeys(false)) {
        ConfigurationSection entry = entitiesSection.getConfigurationSection(key);
        if (entry == null) {
          continue;
        }
        String type = entry.getString("type", "armor_stand");
        int ex = entry.getInt("x");
        int ey = entry.getInt("y");
        int ez = entry.getInt("z");
        int facing = entry.getInt("facing", -1);
        String item = entry.getString("item");
        float yaw = (float) entry.getDouble("rotationYaw", 0.0);
        entities.add(new StashEntity(type, ex, ey, ez, facing, item, yaw));
      }
    }
    return new StashTemplate(name, blocks, entities);
  }

  private String extractTemplateName(File file) {
    File parentDir = file.getParentFile();
    if (parentDir == null) {
      return file.getName().replace(".yml", "");
    }
    String parentName = parentDir.getName();
    if ("converted".equalsIgnoreCase(parentName)) {
      File grandParent = parentDir.getParentFile();
      if (grandParent != null) {
        String grandParentName = grandParent.getName();
        if ("Stashs".equalsIgnoreCase(grandParentName)) {
          return file.getName().replace(".yml", "");
        }
        return grandParentName;
      }
    }
    if ("Stashs".equalsIgnoreCase(parentName)) {
      return file.getName().replace(".yml", "");
    }
    return parentName;
  }

  private void writeDefaultTemplates() {
    try {
      YamlConfiguration basic = new YamlConfiguration();
      basic.set("name", "BasicRoom");
      ConfigurationSection blocks = basic.createSection("blocks");
      writeBlock(blocks, "b0", 0, 0, 0, "OBSIDIAN");
      writeBlock(blocks, "b1", 1, 0, 0, "OBSIDIAN");
      writeBlock(blocks, "b2", 0, 1, 0, "CHEST");
      basic.save(new File(stashDir, "basic.yml"));
    } catch (Throwable e) {
      LogData.get().warning("[stash] failed to write default template: " + e);
    }
  }

  private static void writeBlock(ConfigurationSection parent, String key,
                                 int x, int y, int z, String material) {
    ConfigurationSection b = parent.createSection(key);
    b.set("x", x);
    b.set("y", y);
    b.set("z", z);
    b.set("material", material);
  }

  public static final class StashTemplate {
    public final String name;
    public final List<StashBlock> blocks;
    public final List<StashEntity> entities;

    StashTemplate(String name, List<StashBlock> blocks, List<StashEntity> entities) {
      this.name = name;
      this.blocks = Collections.unmodifiableList(blocks);
      this.entities = Collections.unmodifiableList(entities);
    }

    public List<GhostBlockManager.GhostBlock> toGhostBlocks(Location origin) {
      return toGhostBlocks(origin, 0f);
    }

    public List<GhostBlockManager.GhostBlock> toGhostBlocks(Location origin, float yaw) {
      World world = origin.getWorld();
      int ox = origin.getBlockX();
      int oy = origin.getBlockY();
      int oz = origin.getBlockZ();
      int steps = yawToSteps(yaw);
      List<GhostBlockManager.GhostBlock> list = new ArrayList<>(blocks.size());
      for (StashBlock b : blocks) {
        int[] rot = rotateOffset(b.x, b.z, steps);
        BlockData rotated = rotateBlockData(b.data, steps);
        list.add(new GhostBlockManager.GhostBlock(
            new Location(world, ox + rot[0], oy + b.y, oz + rot[1]), rotated));
      }
      return list;
    }

    private static int[] rotateOffset(int x, int z, int steps) {
      return switch (steps & 3) {
        case 1 -> new int[]{-z, x};
        case 2 -> new int[]{-x, -z};
        case 3 -> new int[]{z, -x};
        default -> new int[]{x, z};
      };
    }

    private static int yawToSteps(float yaw) {
      float norm = yaw % 360f;
      if (norm < 0f) {
        norm += 360f;
      }
      return Math.round(norm / 90f) % 4;
    }

    private static BlockData rotateBlockData(BlockData data, int steps) {
      if (steps == 0) {
        return data;
      }
      if (data instanceof org.bukkit.block.data.Directional dir) {
        org.bukkit.block.BlockFace facing = dir.getFacing();
        org.bukkit.block.BlockFace[] horiz = {
            org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.WEST,
            org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.EAST
        };
        int idx = -1;
        for (int i = 0; i < horiz.length; i++) {
          if (horiz[i] == facing) {
            idx = i;
            break;
          }
        }
        if (idx >= 0) {
          boolean allHoriz = true;
          for (org.bukkit.block.BlockFace f : horiz) {
            if (!dir.getFaces().contains(f)) {
              allHoriz = false;
              break;
            }
          }
          if (allHoriz) {
            BlockData copy = data.clone();
            ((org.bukkit.block.data.Directional) copy).setFacing(
                horiz[(idx + steps) % 4]);
            return copy;
          }
        }
      }
      if (data instanceof org.bukkit.block.data.Rotatable rot) {
        org.bukkit.block.BlockFace cur = rot.getRotation();
        org.bukkit.block.BlockFace[] all16 = {
            org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.SOUTH_SOUTH_WEST,
            org.bukkit.block.BlockFace.SOUTH_WEST,
            org.bukkit.block.BlockFace.WEST_SOUTH_WEST,
            org.bukkit.block.BlockFace.WEST,
            org.bukkit.block.BlockFace.WEST_NORTH_WEST,
            org.bukkit.block.BlockFace.NORTH_WEST,
            org.bukkit.block.BlockFace.NORTH_NORTH_WEST,
            org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.NORTH_NORTH_EAST,
            org.bukkit.block.BlockFace.NORTH_EAST,
            org.bukkit.block.BlockFace.EAST_NORTH_EAST,
            org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.EAST_SOUTH_EAST,
            org.bukkit.block.BlockFace.SOUTH_EAST,
            org.bukkit.block.BlockFace.SOUTH_SOUTH_EAST
        };
        int rIdx = -1;
        for (int i = 0; i < all16.length; i++) {
          if (all16[i] == cur) {
            rIdx = i;
            break;
          }
        }
        if (rIdx >= 0) {
          BlockData copy = data.clone();
          ((org.bukkit.block.data.Rotatable) copy).setRotation(
              all16[(rIdx + steps * 4) % 16]);
          return copy;
        }
      }
      return data;
    }
  }

  @SuppressWarnings("checkstyle:MemberName")
  public static final class StashBlock {
    public final int x;
    public final int y;
    public final int z;
    public final BlockData data;

    StashBlock(int x, int y, int z, BlockData data) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.data = data;
    }
  }

  @SuppressWarnings("checkstyle:MemberName")
  public static final class StashEntity {
    public final String type;
    public final int x;
    public final int y;
    public final int z;
    public final int facing;
    public final String item;
    public final float rotationYaw;

    StashEntity(String type, int x, int y, int z, int facing, String item,
                  float rotationYaw) {
      this.type = type;
      this.x = x;
      this.y = y;
      this.z = z;
      this.facing = facing;
      this.item = item;
      this.rotationYaw = rotationYaw;
    }
  }
}
