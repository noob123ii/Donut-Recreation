package com.notlucy.donutrecreation.translation;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class MinecraftLanguageLoader {
  private static final Gson gson = new Gson();
  private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

  private final Map<String, Map<String, String>> langs = new HashMap<>();
  private boolean loaded;

  public void load(File langDir, Logger logger) {
    if (!langDir.isDirectory()) {
      logger.warning("[translation] lang directory not found: " + langDir);
      return;
    }
    File[] files = langDir.listFiles((dir, name) -> name.endsWith(".json"));
    if (files == null || files.length == 0) {
      logger.warning("[translation] No .json language files found in " + langDir);
      return;
    }

    Map<String, Map<String, String>> raw = new HashMap<>();
    for (File f : files) {
      String name = f.getName().replace(".json", "").toLowerCase(Locale.ROOT);
      try {
        String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        Map<String, String> map = gson.fromJson(content, MAP_TYPE);
        if (map != null && !map.isEmpty()) {
          raw.put(name, map);
        }
      } catch (IOException e) {
        logger.warning("[translation] Failed to read " + f.getName() + ": " + e.getMessage());
      }
    }

    String base = null;
    for (String key : raw.keySet()) {
      if (key.startsWith("en_")) {
        base = key;
        break;
      }
    }
    if (base == null) {
      logger.warning("[translation] No English variant found \u2014 cannot build reverse index.");
      return;
    }
    if (!base.equals("en_us")) {
      logger.info("[translation] Using '" + base + "' as English base.");
    }

    Map<String, String> english = raw.get(base);
    Map<String, Set<String>> reverse = new HashMap<>();
    for (Map.Entry<String, String> e : english.entrySet()) {
      reverse.computeIfAbsent(e.getValue().toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(e.getKey());
    }

    for (Map.Entry<String, Map<String, String>> entry : raw.entrySet()) {
      String langCode = entry.getKey();
      if (langCode.equals(base)) continue;
      Map<String, String> langMap = entry.getValue();
      Map<String, String> direct = new HashMap<>();
      for (Map.Entry<String, Set<String>> rev : reverse.entrySet()) {
        String engText = rev.getKey();
        for (String key : rev.getValue()) {
          String trans = langMap.get(key);
          if (trans != null) {
            direct.put(engText, trans);
            break;
          }
        }
      }
      langs.put(langCode, direct);
    }

    Map<String, String> selfMap = new HashMap<>();
    for (String v : english.values()) {
      selfMap.put(v.toLowerCase(Locale.ROOT), v);
    }
    langs.put(base, selfMap);

    int pairs = langs.values().stream().mapToInt(Map::size).sum();
    logger.info("[translation] Loaded " + raw.size() + " languages ("
        + pairs + " translation pairs) from " + langDir);
    loaded = true;
  }

  public boolean isLoaded() {
    return loaded;
  }

  public String translate(String text, String targetLang) {
    if (text == null || text.isEmpty()) return text;
    Map<String, String> dict = langs.get(targetLang);
    if (dict == null) return text;
    String result = dict.get(text.toLowerCase(Locale.ROOT));
    return result != null ? result : text;
  }

  public String translate(String text, String fromLang, String toLang) {
    if (fromLang.equals(toLang)) return text;
    return translate(text, toLang);
  }

  public Set<String> availableLanguages() {
    return langs.keySet();
  }

  public static void copyFromSource(File sourceDir, File destDir, Logger logger) {
    if (!sourceDir.isDirectory()) {
      logger.warning("[translation] Source lang directory not found: " + sourceDir);
      return;
    }
    if (!destDir.exists()) destDir.mkdirs();
    File[] files = sourceDir.listFiles((dir, name) -> name.endsWith(".json"));
    if (files == null) return;
    int copied = 0;
    for (File f : files) {
      File out = new File(destDir, f.getName());
      if (!out.exists() || out.lastModified() < f.lastModified()) {
        try {
          Files.copy(f.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
          copied++;
        } catch (IOException e) {
          logger.warning("[translation] Failed to copy " + f.getName() + ": " + e.getMessage());
        }
      }
    }
    if (copied > 0) {
      logger.info("[translation] Copied " + copied + " language file(s) to " + destDir);
    }
  }
}