package com.notlucy.donutrecreation.spawn.manager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class LitematicConverter {

  private LitematicConverter() {
  }

  public static void convertIfNeeded(File litematicFile, File outputYml) {
    if (outputYml.exists()) {
      LogData.get().info("[stash] skipping conversion (yml exists): " + litematicFile.getName());
      return;
    }
    try {
      LogData.get().info("[stash] converting litematic: " + litematicFile.getName());
      List<StashManager.StashBlock> blocks = readLitematic(litematicFile);
      if (blocks.isEmpty()) {
        LogData.get().warning("[stash] litematic empty: " + litematicFile.getName());
        return;
      }
      YamlConfiguration yaml = new YamlConfiguration();
      String baseName = litematicFile.getName()
          .replaceAll("(?i)\\.litematic$", "");
      yaml.set("name", baseName);
      org.bukkit.configuration.ConfigurationSection sec = yaml.createSection("blocks");
      int i = 0;
      for (StashManager.StashBlock b : blocks) {
        org.bukkit.configuration.ConfigurationSection entry = sec.createSection("b" + i);
        entry.set("x", b.x);
        entry.set("y", b.y);
        entry.set("z", b.z);
        entry.set("data", b.data.getAsString());
        i++;
      }
      yaml.save(outputYml);
      LogData.get().info("[stash] converted " + litematicFile.getName()
          + " -> " + outputYml.getName() + " (" + blocks.size() + " blocks)");
    } catch (Throwable e) {
      LogData.get().warning("[stash] failed to convert "
          + litematicFile.getName() + ": " + e);
      e.printStackTrace();
    }
  }

  private static List<StashManager.StashBlock> readLitematic(File file) throws IOException {
    try (DataInputStream in = new DataInputStream(
        new GZIPInputStream(new FileInputStream(file)))) {
      NbtReader r = new NbtReader(in);
      r.readByte();
      r.readString();
      Map<String, Object> root = r.readCompound();
      @SuppressWarnings("unchecked")
      Map<String, Object> regions = (Map<String, Object>) root.get("Regions");
      if (regions == null) {
        return List.of();
      }
      List<StashManager.StashBlock> all = new ArrayList<>();
      for (Object regObj : regions.values()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> region = (Map<String, Object>) regObj;
        all.addAll(readRegion(region));
      }
      return all;
    }
  }

  @SuppressWarnings("unchecked")
  private static List<StashManager.StashBlock> readRegion(Map<String, Object> region) {
    int sxRaw;
    int syRaw;
    int szRaw;
    Object sizeObj = region.get("Size");
    if (sizeObj instanceof int[] arr && arr.length >= 3) {
      sxRaw = arr[0];
      syRaw = arr[1];
      szRaw = arr[2];
    } else if (sizeObj instanceof Map<?, ?> m) {
      sxRaw = ((Number) m.get("x")).intValue();
      syRaw = ((Number) m.get("y")).intValue();
      szRaw = ((Number) m.get("z")).intValue();
    } else {
      return List.of();
    }
    int sx = Math.abs(sxRaw);
    int sy = Math.abs(syRaw);
    int sz = Math.abs(szRaw);
    int offX = sxRaw < 0 ? sxRaw + 1 : 0;
    int offY = syRaw < 0 ? syRaw + 1 : 0;
    int offZ = szRaw < 0 ? szRaw + 1 : 0;

    int px = 0;
    int py = 0;
    int pz = 0;
    Object posObj = region.get("Position");
    if (posObj instanceof int[] arr && arr.length >= 3) {
      px = arr[0];
      py = arr[1];
      pz = arr[2];
    } else if (posObj instanceof Map<?, ?> m) {
      px = ((Number) m.get("x")).intValue();
      py = ((Number) m.get("y")).intValue();
      pz = ((Number) m.get("z")).intValue();
    }

    List<Map<String, Object>> palette =
        (List<Map<String, Object>>) region.get("BlockStatePalette");
    if (palette == null || palette.isEmpty()) {
      return List.of();
    }
    String[] names = new String[palette.size()];
    for (int i = 0; i < palette.size(); i++) {
      Object name = palette.get(i).get("Name");
      String baseName = name instanceof String ? (String) name : "minecraft:air";
      Map<String, Object> props = (Map<String, Object>) palette.get(i).get("Properties");
      if (props != null && !props.isEmpty()) {
        StringBuilder sb = new StringBuilder(baseName);
        sb.append("[");
        boolean first = true;
        for (Map.Entry<String, Object> entry : props.entrySet()) {
          if (!first) {
            sb.append(",");
          }
          sb.append(entry.getKey()).append("=").append(entry.getValue());
          first = false;
        }
        sb.append("]");
        names[i] = sb.toString();
      } else {
        names[i] = baseName;
      }
    }

    long[] states;
    Object statesObj = region.get("BlockStates");
    if (statesObj instanceof long[] arr) {
      states = arr;
    } else if (statesObj instanceof List<?> list) {
      states = new long[list.size()];
      for (int i = 0; i < list.size(); i++) {
        states[i] = ((Number) list.get(i)).longValue();
      }
    } else {
      return List.of();
    }

    int bits;
    if (names.length == 1) {
      bits = 0;
    } else {
      bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(names.length - 1));
    }
    long mask = (1L << bits) - 1;
    int total = sx * sy * sz;
    List<StashManager.StashBlock> blocks = new ArrayList<>();
    if (bits == 0) {
      BlockData all = parseBlockData(names[0]);
      if (all != null) {
        for (int i = 0; i < total; i++) {
          int bx = i % sx;
          int bz = (i / sx) % sz;
          int by = i / (sx * sz);
          blocks.add(new StashManager.StashBlock(
              bx + offX + px, by + offY + py, bz + offZ + pz, all));
        }
      }
      return blocks;
    }
    int idx = 0;
    for (int i = 0; i < total && idx < states.length; i++) {
      long state = (states[idx] >>> ((long) (i * bits) % 64)) & mask;
      if ((i + 1) * bits > (idx + 1) * 64L) {
        state |= (states[idx + 1] << (64 - ((long) (i * bits) % 64))) & mask;
      }
      if ((long) (i + 1) * bits >= (idx + 1) * 64L) {
        idx++;
      }
      if (state >= names.length) {
        continue;
      }
      String mcName = names[(int) state];
      BlockData data = parseBlockData(mcName);
      if (data == null) {
        continue;
      }
      int bx = i % sx;
      int bz = (i / sx) % sz;
      int by = i / (sx * sz);
      blocks.add(new StashManager.StashBlock(
          bx + offX + px, by + offY + py, bz + offZ + pz, data));
    }
    return blocks;
  }

  private static BlockData parseBlockData(String minecraftName) {
    try {
      return Bukkit.createBlockData(minecraftName);
    } catch (IllegalArgumentException e) {
      LogData.get().fine("[stash] no Bukkit block data for " + minecraftName);
      return null;
    }
  }

  private static final class NbtReader {
    private final DataInputStream in;

    NbtReader(DataInputStream in) {
      this.in = in;
    }

    byte readByte() throws IOException {
      return in.readByte();
    }

    short readShort() throws IOException {
      return in.readShort();
    }

    int readInt() throws IOException {
      return in.readInt();
    }

    long readLong() throws IOException {
      return in.readLong();
    }

    float readFloat() throws IOException {
      return in.readFloat();
    }

    double readDouble() throws IOException {
      return in.readDouble();
    }

    String readString() throws IOException {
      short len = readShort();
      byte[] b = new byte[len];
      in.readFully(b);
      return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    byte[] readByteArray() throws IOException {
      int len = readInt();
      byte[] arr = new byte[len];
      in.readFully(arr);
      return arr;
    }

    int[] readIntArray() throws IOException {
      int len = readInt();
      int[] arr = new int[len];
      for (int i = 0; i < len; i++) {
        arr[i] = readInt();
      }
      return arr;
    }

    long[] readLongArray() throws IOException {
      int len = readInt();
      long[] arr = new long[len];
      for (int i = 0; i < len; i++) {
        arr[i] = readLong();
      }
      return arr;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    Object readPayload(int type) throws IOException {
      return switch (type) {
        case 1 -> readByte();
        case 2 -> readShort();
        case 3 -> readInt();
        case 4 -> readLong();
        case 5 -> readFloat();
        case 6 -> readDouble();
        case 7 -> readByteArray();
        case 8 -> readString();
        case 9 -> readList();
        case 10 -> readCompound();
        case 11 -> readIntArray();
        case 12 -> readLongArray();
        default -> throw new IOException("Unknown NBT type: " + type);
      };
    }

    List<Object> readList() throws IOException {
      int elemType = readByte();
      int len = readInt();
      List<Object> list = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        list.add(readPayload(elemType));
      }
      return list;
    }

    Map<String, Object> readCompound() throws IOException {
      Map<String, Object> map = new LinkedHashMap<>();
      while (true) {
        int type = readByte() & 0xFF;
        if (type == 0) {
          break;
        }
        String name = readString();
        map.put(name, readPayload(type));
      }
      return map;
    }
  }
}
