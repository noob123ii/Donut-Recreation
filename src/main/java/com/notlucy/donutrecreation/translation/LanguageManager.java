package com.notlucy.donutrecreation.translation;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LanguageManager {
  private static final String DEFAULT = "en";
  private final Map<UUID, String> langs = new ConcurrentHashMap<>();
  private final File file;
  private BiConsumer<UUID, String> onChange;

  public LanguageManager(File dataFolder) {
    this.file = new File(dataFolder, "player-languages.yml");
    load();
  }

  public void setOnChange(BiConsumer<UUID, String> onChange) {
    this.onChange = onChange;
  }

  public String getLang(UUID id) {
    return langs.getOrDefault(id, DEFAULT);
  }

  public void setLang(UUID id, String lang) {
    String norm = lang.toLowerCase(Locale.ROOT);
    String prev = langs.put(id, norm);
    if (prev == null || !prev.equals(norm)) {
      save();
      if (onChange != null) onChange.accept(id, norm);
    }
  }

  private void load() {
    if (!file.exists()) return;
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    for (String key : cfg.getKeys(false)) {
      try {
        langs.put(UUID.fromString(key), cfg.getString(key, DEFAULT));
      } catch (IllegalArgumentException ignored) { }
    }
  }

  private void save() {
    YamlConfiguration cfg = new YamlConfiguration();
    for (Map.Entry<UUID, String> e : langs.entrySet()) {
      cfg.set(e.getKey().toString(), e.getValue());
    }
    try {
      cfg.save(file);
    } catch (IOException ignored) { }
  }
}