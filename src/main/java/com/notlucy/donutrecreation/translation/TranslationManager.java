package com.notlucy.donutrecreation.translation;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.notlucy.donutrecreation.translation.model.SignedText;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class TranslationManager {
  private static final int CACHE_MAX = 50000;
  private static final long API_DELAY_MS = 500;
  private static final int BURST_LIMIT = 20;

  private final Map<Location, SignedText> signs = new ConcurrentHashMap<>();
  private final Map<Location, NBTCompound> nbtCache = new ConcurrentHashMap<>();
  private final Map<String, String> cache = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> phrases = new ConcurrentHashMap<>();
  private final HttpClient http;
  private final Plugin plugin;
  private final MinecraftLanguageLoader mc;

  private String libreUrl;
  private String googleKey;
  private String googleProjectId;
  private Instant lastCallAt = Instant.EPOCH;
  private int charsUsed;

  public TranslationManager(Plugin plugin, File translationDir, MinecraftLanguageLoader mc) {
    this.plugin = plugin;
    this.mc = mc;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();
    loadPhrases(translationDir);
    loadConfig(translationDir);
  }

  private static String normalize(String lang) {
    if (lang == null) return "en";
    String lower = lang.toLowerCase(Locale.ROOT).replace('-', '_');
    if (lower.length() > 2 && lower.charAt(2) == '_') {
      return lower;
    }
    return lower;
  }

  private String mcLookup(String text, String lang) {
    String norm = normalize(lang);
    String result = mc.translate(text, norm);
    if (!result.equals(text)) return result;
    if (norm.length() > 2 && norm.charAt(2) == '_') {
      result = mc.translate(text, norm.substring(0, 2));
      if (!result.equals(text)) return result;
    } else {
      for (String full : mc.availableLanguages()) {
        if (full.startsWith(norm + "_")) {
          result = mc.translate(text, full);
          if (!result.equals(text)) return result;
          break;
        }
      }
    }
    return text;
  }

  public String[] translateLines(String[] lines, String targetLang) {
    if ("en".equals(targetLang)) return lines.clone();
    String cacheKey = "\0" + targetLang + "\0" + String.join("\0", lines);
    String cached = cache.get(cacheKey);
    if (cached != null) return cached.split("\0", -1);
    String[] result = new String[lines.length];
    for (int i = 0; i < lines.length; i++) {
      result[i] = translate(lines[i], "en", targetLang);
    }
    if (cache.size() > CACHE_MAX) cache.clear();
    cache.put(cacheKey, String.join("\0", result));
    return result;
  }

  public String translate(String text, String from, String to) {
    if (text == null || text.isEmpty()) return text;
    if (from.equals(to)) return text;
    String lower = text.toLowerCase(Locale.ROOT).trim();

    String mcResult = mcLookup(lower, to);
    if (!mcResult.equals(lower)) return mcResult;

    Map<String, String> dict = phrases.get(normalize(to));
    if (dict != null) {
      String d = dict.get(lower);
      if (d != null) return d;
    }

    String cacheKey = from + ":" + to + ":" + text;
    String cached = cache.get(cacheKey);
    if (cached != null) return cached;

    String api = apiTranslate(text, from, to);
    if (!api.equals(text)) {
      if (cache.size() > CACHE_MAX) cache.clear();
      cache.put(cacheKey, api);
    }
    return api;
  }

  private String apiTranslate(String text, String from, String to) {
    if (googleKey != null && !googleKey.isEmpty()) {
      return googleTranslate(text, from, to);
    }
    if (libreUrl != null && !libreUrl.isEmpty()) {
      return libreTranslate(text, from, to);
    }
    return text;
  }

  private String googleTranslate(String text, String from, String to) {
    if (!rateLimit()) return text;
    if (googleProjectId == null || googleProjectId.isEmpty()) return text;
    try {
      String src = googleLang(from);
      String dst = googleLang(to);
      String json = "{\"contents\":[\"" + escapeJson(text)
          + "\"],\"sourceLanguageCode\":\"" + src
          + "\",\"targetLanguageCode\":\"" + dst
          + "\",\"mimeType\":\"text/plain\"}";
      String url = "https://translate.googleapis.com/v3/projects/"
          + URLEncoder.encode(googleProjectId, StandardCharsets.UTF_8) + ":translateText"
          + "?key=" + URLEncoder.encode(googleKey, StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url)).header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(10))
          .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        String t = extract(response.body());
        if (t != null && !t.isEmpty()) return t;
      }
    } catch (Exception ignored) { }
    return text;
  }

  private static String googleLang(String lang) {
    if (lang == null || lang.isEmpty()) return "";
    String normalized = lang.toLowerCase(Locale.ROOT).replace('_', '-');
    int idx = normalized.indexOf('-');
    if (idx > 0 && idx + 2 <= normalized.length()) {
      normalized = normalized.substring(0, idx + 1)
          + Character.toUpperCase(normalized.charAt(idx + 1))
          + normalized.substring(idx + 2);
    }
    return normalized;
  }

  private String libreTranslate(String text, String from, String to) {
    if (!rateLimit()) return text;
    try {
      String body = "q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
          + "&source=" + URLEncoder.encode(from, StandardCharsets.UTF_8)
          + "&target=" + URLEncoder.encode(to, StandardCharsets.UTF_8);
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(libreUrl))
          .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
          .timeout(Duration.ofSeconds(10))
          .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        String t = extract(response.body());
        if (t != null && !t.isEmpty()) return t;
      }
    } catch (Exception ignored) { }
    return text;
  }

  private boolean rateLimit() {
    Instant now = Instant.now();
    long elapsed = Duration.between(lastCallAt, now).toMillis();
    if (elapsed < API_DELAY_MS) return false;
    if (elapsed > 60_000) charsUsed = 0;
    if (charsUsed >= BURST_LIMIT) return false;
    lastCallAt = now;
    charsUsed++;
    return true;
  }

  public Set<String> supportedLanguages() {
    return mc.availableLanguages();
  }

  public SignedText getSignText(Location loc) { return signs.get(loc); }

  public void putSignText(Location loc, SignedText text) {
    if (signs.size() > CACHE_MAX) signs.clear();
    signs.put(loc, text);
  }

  public Map<Location, SignedText> allSignTexts() { return signs; }

  public NBTCompound getSignNbt(Location loc) { return nbtCache.get(loc); }

  public void putSignNbt(Location loc, NBTCompound nbt) {
    if (nbtCache.size() > CACHE_MAX) nbtCache.clear();
    nbtCache.put(loc, nbt.copy());
  }

  private static String extract(String jsonBody) {
    try {
      int idx = jsonBody.indexOf("\"translatedText\":\"");
      if (idx < 0) return null;
      idx += "\"translatedText\":\"".length();
      StringBuilder sb = new StringBuilder();
      for (; idx < jsonBody.length(); idx++) {
        char c = jsonBody.charAt(idx);
        if (c == '"') break;
        if (c == '\\' && idx + 1 < jsonBody.length()) sb.append(jsonBody.charAt(++idx));
        else sb.append(c);
      }
      return sb.toString();
    } catch (Exception e) { return null; }
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  private void loadPhrases(File dir) {
    File file = new File(dir, "phrases.yml");
    if (!file.exists()) writeDefaultPhrases(file);
    try {
      var config = YamlConfiguration.loadConfiguration(file);
      for (String lang : config.getKeys(false)) {
        Map<String, String> dict = new HashMap<>();
        var section = config.getConfigurationSection(lang);
        if (section != null) {
          for (String phrase : section.getKeys(false)) {
            dict.put(phrase.toLowerCase(Locale.ROOT), section.getString(phrase, phrase));
          }
        }
        phrases.put(lang, dict);
      }
    } catch (Exception e) {
      plugin.getLogger().warning("[translation] Failed to load phrase dictionary: " + e.getMessage());
    }
  }

  private void loadConfig(File dir) {
    File file = new File(dir, "config.yml");
    if (!file.exists()) writeDefaultConfig(file);
    try {
      var config = YamlConfiguration.loadConfiguration(file);
      this.libreUrl = config.getString("libre-url", "");
      this.googleKey = config.getString("google-api-key", "");
      this.googleProjectId = config.getString("google-project-id", "");
    } catch (Exception e) {
      plugin.getLogger().warning("[translation] Failed to load config: " + e.getMessage());
    }
  }

  private void writeDefaultPhrases(File file) {
    try {
      var config = new YamlConfiguration();
      config.set("es.hello", "hola");
      config.set("es.how are you", "c\u00f3mo est\u00e1s");
      config.set("es.good morning", "buenos d\u00edas");
      config.set("es.thank you", "gracias");
      config.set("fr.hello", "bonjour");
      config.set("fr.how are you", "comment allez-vous");
      config.set("fr.thank you", "merci");
      config.set("de.hello", "hallo");
      config.set("de.how are you", "wie geht es dir");
      config.set("de.thank you", "danke");
      config.save(file);
    } catch (IOException e) {
      plugin.getLogger().warning("[translation] Failed to create default phrases: " + e.getMessage());
    }
  }

  private void writeDefaultConfig(File file) {
    try {
      var config = new YamlConfiguration();
      config.set("google-project-id", "");
      config.set("google-project-id-comment", "Your Google Cloud project ID (e.g. 'my-project-123')");
      config.set("google-api-key", "");
      config.set("google-api-key-comment", "Get a key at https://console.cloud.google.com/apis/credentials (free tier: 500k chars/month)");
      config.set("libre-url", "https://donut-debug-vercel-web-brct.vercel.app/api/translate");
      config.set("libre-url-comment", "Free self-hosted: deploy api/ to Vercel, or docker run -d -p 5000:5000 libretranslate/libretranslate");
      config.save(file);
    } catch (IOException e) {
      plugin.getLogger().warning("[translation] Failed to create default config: " + e.getMessage());
    }
  }
}