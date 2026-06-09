package com.notlucy.donutrecreation.spawn.manager;

import com.notlucy.donutrecreation.util.LogData;
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
    String name = yaml.getString("name", file.getName().replace(".yml", ""));
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
      World world = origin.getWorld();
      int ox = origin.getBlockX();
      int oy = origin.getBlockY();
      int oz = origin.getBlockZ();
      List<GhostBlockManager.GhostBlock> list = new ArrayList<>(blocks.size());
      for (StashBlock b : blocks) {
        list.add(new GhostBlockManager.GhostBlock(
            new Location(world, ox + b.x, oy + b.y, oz + b.z), b.data));
      }
      return list;
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
