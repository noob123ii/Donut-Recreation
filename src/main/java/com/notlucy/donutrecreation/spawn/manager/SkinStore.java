package com.notlucy.donutrecreation.spawn.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.notlucy.donutrecreation.util.LogData;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class SkinStore {

  public record SkinRecord(UUID uuid, String name, String texture, String signature) {}

  private final File file;
  private final ConcurrentMap<String, SkinRecord> skins = new ConcurrentHashMap<>();

  public SkinStore(File dataFolder) {
    this.file = new File(dataFolder, "skins.yml");
    load();
  }

  public boolean capture(Player player) {
    if (player == null) {
      return false;
    }
    try {
      PlayerProfile profile = player.getPlayerProfile();
      ProfileProperty texture = null;
      for (ProfileProperty property : profile.getProperties()) {
        if ("textures".equals(property.getName())) {
          texture = property;
          break;
        }
      }
      if (texture == null || texture.getValue() == null || texture.getValue().isEmpty()) {
        return false;
      }
      SkinRecord record = new SkinRecord(
          player.getUniqueId(),
          player.getName(),
          texture.getValue(),
          texture.getSignature());
      skins.put(player.getName().toLowerCase(Locale.ROOT), record);
      save();
      return true;
    } catch (Throwable e) {
      LogData.get().warning("[skins] capture failed for " + player.getName() + ": " + e);
      return false;
    }
  }

  public SkinRecord byName(String name) {
    if (name == null) {
      return null;
    }
    return skins.get(name.toLowerCase(Locale.ROOT));
  }

  public SkinRecord random() {
    if (skins.isEmpty()) {
      return null;
    }
    List<SkinRecord> values = new ArrayList<>(skins.values());
    return values.get(ThreadLocalRandom.current().nextInt(values.size()));
  }

  public int count() {
    return skins.size();
  }

  private void load() {
    if (!file.exists()) {
      return;
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection skinsSection = yaml.getConfigurationSection("skins");
    Set<String> keys = skinsSection != null
        ? skinsSection.getKeys(false) : yaml.getKeys(false);
    for (String key : keys) {
      try {
        UUID uuid = UUID.fromString(yaml.getString(key + ".uuid"));
        String name = yaml.getString(key + ".name");
        String texture = yaml.getString(key + ".texture");
        String signature = yaml.getString(key + ".signature");
        if (name == null || texture == null) {
          continue;
        }
        skins.put(key.toLowerCase(Locale.ROOT),
            new SkinRecord(uuid, name, texture, signature));
      } catch (Throwable ignored) {
      }
    }
  }

  public static SkinRecord liveOf(Player player) {
    if (player == null) {
      return null;
    }
    try {
      PlayerProfile profile = player.getPlayerProfile();
      for (ProfileProperty property : profile.getProperties()) {
        if ("textures".equals(property.getName()) && property.getValue() != null
            && !property.getValue().isEmpty()) {
          return new SkinRecord(player.getUniqueId(), player.getName(),
              property.getValue(), property.getSignature());
        }
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  private void save() {
    try {
      YamlConfiguration yaml = new YamlConfiguration();
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<String, SkinRecord> entry : skins.entrySet()) {
        SkinRecord record = entry.getValue();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uuid", record.uuid().toString());
        data.put("name", record.name());
        data.put("texture", record.texture());
        data.put("signature", record.signature());
        out.put(entry.getKey(), data);
      }
      yaml.createSection("skins", out);
      File parent = file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
        return;
      }
      yaml.save(file);
    } catch (IOException e) {
      LogData.get().warning("[skins] save failed: " + e);
    }
  }
}
