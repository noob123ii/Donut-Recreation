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

  public String getLang(UUID playerId) {
    return langs.getOrDefault(playerId, DEFAULT);
  }

  public void setLang(UUID playerId, String lang) {
    String norm = lang.toLowerCase(Locale.ROOT);
    String prev = langs.put(playerId, norm);
    if (prev == null || !prev.equals(norm)) {
      save();
      if (onChange != null) onChange.accept(playerId, norm);
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
