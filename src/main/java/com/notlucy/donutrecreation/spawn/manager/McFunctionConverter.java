package com.notlucy.donutrecreation.spawn.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class McFunctionConverter {

  private McFunctionConverter() {
  }

  public static void convertIfNeeded(File mcfunctionFile, File outputYml) {
    if (outputYml.exists()) {
      return;
    }
    if (!mcfunctionFile.exists()) {
      LogData.get().warning("[stash] mcfunction not found: " + mcfunctionFile.getAbsolutePath());
      return;
    }
    try {
      Result result = readMcFunction(mcfunctionFile, new HashSet<>());
      if (result.blocks().isEmpty() && result.entities().isEmpty()) {
        LogData.get().warning("[stash] mcfunction empty (no blocks or entities): "
            + mcfunctionFile.getName());
        return;
      }
      fillAirBlocks(result.blocks());
      YamlConfiguration yaml = new YamlConfiguration();
      String baseName = mcfunctionFile.getName()
          .replaceAll("(?i)\\.mcfunction$", "");
      yaml.set("name", baseName);
      org.bukkit.configuration.ConfigurationSection sec = yaml.createSection("blocks");
      int i = 0;
      for (StashManager.StashBlock b : result.blocks()) {
        org.bukkit.configuration.ConfigurationSection entry = sec.createSection("b" + i);
        entry.set("x", b.x);
        entry.set("y", b.y);
        entry.set("z", b.z);
        entry.set("material", b.data.getMaterial().name());
        entry.set("data", b.data.getAsString());
        i++;
      }
      org.bukkit.configuration.ConfigurationSection entSec = yaml.createSection("entities");
      int j = 0;
      for (StashManager.StashEntity e : result.entities()) {
        org.bukkit.configuration.ConfigurationSection entry = entSec.createSection("e" + j);
        entry.set("type", e.type);
        entry.set("x", e.x);
        entry.set("y", e.y);
        entry.set("z", e.z);
        entry.set("facing", e.facing);
        entry.set("item", e.item);
        entry.set("rotationYaw", e.rotationYaw);
        j++;
      }
      yaml.save(outputYml);
      LogData.get().info("[stash] converted " + mcfunctionFile.getName()
          + " -> " + outputYml.getName() + " (" + result.blocks().size()
          + " blocks, " + result.entities().size() + " entities)");
    } catch (Throwable e) {
      LogData.get().warning("[stash] failed to convert "
          + mcfunctionFile.getName() + ": " + e);
      e.printStackTrace();
    }
  }

  private static Result readMcFunction(File file, Set<String> visited)
      throws IOException {
    String canonical = file.getCanonicalPath();
    if (!visited.add(canonical)) {
      return new Result(new ArrayList<>(), new ArrayList<>());
    }
    List<StashManager.StashBlock> blocks = new ArrayList<>();
    List<StashManager.StashEntity> entities = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        if (line.startsWith("function ")) {
          String funcRef = line.substring(9).trim();
          File sub = resolveFunction(file, funcRef);
          if (sub != null && sub.exists()) {
            Result subResult = readMcFunction(sub, visited);
            blocks.addAll(subResult.blocks());
            entities.addAll(subResult.entities());
          }
          continue;
        }
        if (line.startsWith("setblock ")) {
          ParsedEntry entry = parseSetblock(line);
          if (entry != null) {
            if (entry.block() != null) {
              blocks.add(entry.block());
            }
            if (entry.entity() != null) {
              entities.add(entry.entity());
            }
          }
        }
      }
    }
    return new Result(blocks, entities);
  }

  private static File resolveFunction(File parent, String funcRef) {
    String name = funcRef;
    int colon = name.indexOf(':');
    if (colon >= 0) {
      name = name.substring(colon + 1);
    }
    File dir = parent.getParentFile();
    if (dir == null) {
      dir = new File(".");
    }
    return new File(dir, name + ".mcfunction");
  }

  private static ParsedEntry parseSetblock(String line) {
    String[] parts = line.split("\\s+");
    if (parts.length < 5) {
      return null;
    }
    final int x = parseRelCoord(parts[1]);
    final int y = parseRelCoord(parts[2]);
    final int z = parseRelCoord(parts[3]);

    String rest = line.substring(line.indexOf(parts[4]));
    int bracket = rest.indexOf('[');
    String matName;
    String states = null;
    if (bracket >= 0) {
      matName = rest.substring(0, bracket).trim();
      states = rest.substring(bracket);
    } else {
      matName = rest.trim();
    }

    if (matName.isEmpty()) {
      return null;
    }
    String lower = matName.toLowerCase(Locale.ROOT);
    boolean isEntity = lower.equals("armor_stand") || lower.equals("item_frame")
        || lower.equals("painting") || lower.equals("glow_item_frame");
    if (!isEntity && states != null && states.contains("__entity=1")) {
      return null;
    }

    if (isEntity) {
      StashManager.StashEntity entity = parseEntity(lower, x, y, z, states);
      return new ParsedEntry(null, entity);
    }

    BlockData data = createBlockData(matName, states);
    if (data == null) {
      return null;
    }
    return new ParsedEntry(new StashManager.StashBlock(x, y, z, data), null);
  }

  private static StashManager.StashEntity parseEntity(String type,
                                                         int x, int y, int z,
                                                         String states) {
    int facing = -1;
    String item = null;
    float rotationYaw = 0f;
    if (states != null) {
      facing = extractIntState(states, "facing");
      item = extractStringState(states, "item");
      rotationYaw = extractFloatState(states, "yaw");
    }
    return new StashManager.StashEntity(type, x, y, z, facing, item, rotationYaw);
  }

  private static int extractIntState(String states, String key) {
    String pattern = key + "=";
    int idx = states.indexOf(pattern);
    if (idx < 0) {
      return -1;
    }
    int start = idx + pattern.length();
    int end = start;
    while (end < states.length() && (states.charAt(end) == '-'
        || Character.isDigit(states.charAt(end)))) {
      end++;
    }
    try {
      return Integer.parseInt(states.substring(start, end));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String extractStringState(String states, String key) {
    String pattern = key + "=";
    int idx = states.indexOf(pattern);
    if (idx < 0) {
      return null;
    }
    int start = idx + pattern.length();
    int end = start;
    while (end < states.length() && states.charAt(end) != ','
        && states.charAt(end) != ']') {
      end++;
    }
    String value = states.substring(start, end).trim();
    return value.isEmpty() ? null : value;
  }

  private static float extractFloatState(String states, String key) {
    String pattern = key + "=";
    int idx = states.indexOf(pattern);
    if (idx < 0) {
      return 0f;
    }
    int start = idx + pattern.length();
    int end = start;
    while (end < states.length() && (states.charAt(end) == '-'
        || states.charAt(end) == '.'
        || Character.isDigit(states.charAt(end)))) {
      end++;
    }
    try {
      return Float.parseFloat(states.substring(start, end));
    } catch (NumberFormatException e) {
      return 0f;
    }
  }

  private static void fillAirBlocks(List<StashManager.StashBlock> blocks) {
    if (blocks.isEmpty()) {
      return;
    }
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (StashManager.StashBlock b : blocks) {
      minX = Math.min(minX, b.x);
      maxX = Math.max(maxX, b.x);
      minY = Math.min(minY, b.y);
      maxY = Math.max(maxY, b.y);
      minZ = Math.min(minZ, b.z);
      maxZ = Math.max(maxZ, b.z);
    }
    Set<String> occupied = new HashSet<>();
    for (StashManager.StashBlock b : blocks) {
      occupied.add(b.x + "," + b.y + "," + b.z);
    }
    BlockData air = Material.AIR.createBlockData();
    int added = 0;
    for (int bx = minX; bx <= maxX; bx++) {
      for (int by = minY; by <= maxY; by++) {
        for (int bz = minZ; bz <= maxZ; bz++) {
          if (!occupied.contains(bx + "," + by + "," + bz)) {
            blocks.add(new StashManager.StashBlock(bx, by, bz, air));
            added++;
          }
        }
      }
    }
    LogData.get().fine("[stash] filled " + added + " air blocks");
  }

  private record Result(List<StashManager.StashBlock> blocks,
                          List<StashManager.StashEntity> entities) {
  }

  private record ParsedEntry(StashManager.StashBlock block,
                               StashManager.StashEntity entity) {
  }

  private static int parseRelCoord(String s) {
    if (!s.startsWith("~")) {
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    String rest = s.substring(1);
    if (rest.isEmpty()) {
      return 0;
    }
    try {
      return (int) Math.floor(Double.parseDouble(rest));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static BlockData createBlockData(String matName, String states) {
    String full = states == null ? "minecraft:" + matName
        : "minecraft:" + matName + states;
    try {
      return Bukkit.createBlockData(full);
    } catch (IllegalArgumentException e) {
      try {
        return Bukkit.createBlockData(matName
            + (states != null ? states : ""));
      } catch (IllegalArgumentException e2) {
        Material mat = Material.matchMaterial(matName);
        if (mat != null) {
          return mat.createBlockData();
        }
        LogData.get().fine("[stash] unknown material '" + matName + "'");
        return null;
      }
    }
  }
}
